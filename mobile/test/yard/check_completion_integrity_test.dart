import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:trailersaas_mobile/src/persistence/app_database.dart';
import 'package:trailersaas_mobile/src/sync/command_store.dart';
import 'package:trailersaas_mobile/src/yard/check_controller.dart';
import 'package:trailersaas_mobile/src/yard/check_contracts.dart';

import 'check_fixtures.dart';
import 'fixtures.dart';

void main() {
  late Directory dir;
  late CheckFixture f;
  late YardCheckController c;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('check-completion-integrity-');
    f = CheckFixture(dir, 1);
    await f.open();
    await f.snapshot();
    c = YardCheckController(f.mobile.session, f.worker, FakeYardScanner());
    c.selected = await f.cache.find(f.mobile.session.snapshot!, checkId);
    await c.loadLocal();
  });
  tearDown(() async {
    c.dispose();
    await f.close();
    await dir.delete(recursive: true);
  });

  Future<String> observe() => f.replay.scan(
    f.mobile.session.snapshot!,
    checkId,
    qrToken,
    yardId,
    DateTime.now().toUtc(),
    () => true,
  );
  Future<void> review(String op, CommandState state) async {
    final command = (await f.worker.store.find(checkUser, 'A', op))!;
    await f.worker.store.result(command, state, code: 'RESOURCE_CONFLICT');
    await c.reviewFailure(
      (await f.worker.store.find(checkUser, 'A', op))!,
      'Checked the authorized inventory; retain the failed evidence.',
    );
    expect(
      await f.mobile.db.select(f.mobile.db.yardCheckReviews).get(),
      hasLength(1),
    );
  }

  Future<String> terminal(String kind, CheckSnapshot state) => f.replay.capture(
    f.mobile.session.snapshot!,
    kind,
    '${f.cache.root(f.mobile.session.snapshot!)}/$checkId/${kind == 'YARD_CHECK_COMPLETE' ? 'completion' : 'cancellation'}',
    {
      'review': state.review,
      if (kind == 'YARD_CHECK_CANCEL') 'reason': 'Stopped safely',
      '_capturedAt': DateTime.now().toUtc().toIso8601String(),
    },
  );

  for (final state in [
    CommandState.conflict,
    CommandState.blocked,
    CommandState.permanentFailure,
  ]) {
    for (final frozen in [false, true]) {
      test(
        '${state.value} scan reviewed without ACK blocks completion at review, capture and ${frozen ? 'frozen' : 'new'} replay',
        () async {
          final reviewedState = (await c.reviewState())!;
          final op = await observe();
          await review(op, state);
          expect(
            await f.replay.settled(f.mobile.session.snapshot!, checkId),
            false,
          );
          await expectLater(c.reviewState(), throwsStateError);
          await c.terminal(reviewedState);
          expect((await f.worker.store.list(checkUser, 'A')), hasLength(1));
          final completion = await terminal(
            'YARD_CHECK_COMPLETE',
            reviewedState,
          );
          if (frozen) {
            // Recover a request materialized by the previous vulnerable app version.
            await f.mobile.db
                .into(f.mobile.db.materializedRequests)
                .insert(
                  MaterializedRequestsCompanion.insert(
                    clientOperationId: completion,
                    userId: checkUser,
                    tenantId: 'A',
                    endpointPath:
                        '${f.cache.root(f.mobile.session.snapshot!)}/$checkId/completion',
                    httpMethod: 'POST',
                    payloadJson: '{"expectedVersion":0}',
                    createdAt: DateTime.now(),
                  ),
                );
          }
          await f.worker.sync();
          expect(
            (await f.worker.store.find(checkUser, 'A', completion))!.state,
            'CONFLICT',
          );
          expect(
            (await f.worker.store.find(checkUser, 'A', op))!.state,
            state.value,
          );
          expect(f.mobile.requests.where((r) => r.method == 'POST'), isEmpty);
          expect(f.summary['status'], 'IN_PROGRESS');
          expect(f.summary['missingCount'], 0);
          expect(f.items.single['result'], isNull);
          expect(
            await f.mobile.db.select(f.mobile.db.commandResults).get(),
            isEmpty,
          );
          expect(
            await f.cache.observations(f.mobile.session.snapshot!, checkId),
            hasLength(1),
          );
          if (frozen) {
            expect(
              (await f.mobile.db
                      .select(f.mobile.db.materializedRequests)
                      .getSingle())
                  .payloadJson,
              '{"expectedVersion":0}',
            );
          }
        },
      );
    }
    test(
      'explicit cancellation retains reviewed ${state.value} evidence without inventing ACK or MISSING',
      () async {
        final op = await observe();
        await expectLater(c.reviewState(cancelling: true), throwsStateError);
        await review(op, state);
        final cancellationReview = (await c.reviewState(cancelling: true))!;
        await c.terminal(cancellationReview, reason: 'Stopped safely');
        expect(f.summary['status'], 'CANCELLED');
        expect(f.summary['missingCount'], 0);
        expect(f.items.single['result'], isNull);
        expect(
          (await f.worker.store.find(checkUser, 'A', op))!.state,
          state.value,
        );
        expect(
          await f.cache.observations(f.mobile.session.snapshot!, checkId),
          hasLength(1),
        );
        final receipts = await f.mobile.db
            .select(f.mobile.db.commandResults)
            .get();
        expect(receipts, hasLength(1));
        expect(
          jsonDecode(receipts.single.projectionJson)['status'],
          'CANCELLED',
        );
        expect(
          f.mobile.requests.where((r) => r.method == 'POST').single.url.path,
          endsWith('/cancellation'),
        );
      },
    );
  }
  test(
    'ACKNOWLEDGED label without durable typed scan receipt cannot complete',
    () async {
      final op = await observe();
      await f.worker.store.result(
        (await f.worker.store.find(checkUser, 'A', op))!,
        CommandState.acknowledged,
      );
      expect(
        await f.replay.settled(f.mobile.session.snapshot!, checkId),
        false,
      );
      await expectLater(c.reviewState(), throwsStateError);
      expect(f.summary['status'], 'IN_PROGRESS');
    },
  );
  test(
    'reviewed failure that becomes uncertain still blocks cancellation',
    () async {
      final op = await observe();
      await review(op, CommandState.conflict);
      await f.worker.store.result(
        (await f.worker.store.find(checkUser, 'A', op))!,
        CommandState.retryableFailure,
      );
      expect(
        await f.replay.settled(
          f.mobile.session.snapshot!,
          checkId,
          allowReviewedFailures: true,
        ),
        false,
      );
      await expectLater(c.reviewState(cancelling: true), throwsStateError);
      expect(f.summary['status'], 'IN_PROGRESS');
      expect(
        await f.cache.observations(f.mobile.session.snapshot!, checkId),
        hasLength(1),
      );
    },
  );
  test(
    'ACK label with malformed durable response is not a valid scan receipt',
    () async {
      final op = await observe();
      final command = (await f.worker.store.find(checkUser, 'A', op))!;
      await f.replay.materialize(
        command,
        f.mobile.session.snapshot!,
        () => true,
      );
      await f.mobile.db
          .into(f.mobile.db.commandResults)
          .insert(
            CommandResultsCompanion.insert(
              clientOperationId: op,
              userId: checkUser,
              tenantId: 'A',
              resultKind: 'YARD_CHECK',
              serverId: checkId,
              projectionJson: '{}',
              confirmedAt: DateTime.now(),
            ),
          );
      await f.worker.store.result(command, CommandState.acknowledged);
      expect(
        await f.replay.settled(f.mobile.session.snapshot!, checkId),
        false,
      );
      await expectLater(c.reviewState(), throwsStateError);
      expect(f.summary['status'], 'IN_PROGRESS');
    },
  );
  test('pending terminal intent fences new scans and retains original frozen request for lost ACK retry', () async {
    final state = (await c.reviewState())!;
    final op = await terminal('YARD_CHECK_COMPLETE', state);
    final command = (await f.worker.store.find(checkUser, 'A', op))!;
    final body = await f.replay.materialize(
      command,
      f.mobile.session.snapshot!,
      () => true,
    );
    await expectLater(observe(), throwsStateError);
    expect(
      await f.cache.observations(f.mobile.session.snapshot!, checkId),
      isEmpty,
    );
    expect(
      await f.replay.materialize(
        command,
        f.mobile.session.snapshot!,
        () => true,
      ),
      body,
    );
    await f.worker.sync();
    expect(f.summary['status'], 'COMPLETED');
  });
}
