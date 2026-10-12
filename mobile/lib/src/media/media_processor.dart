import 'dart:math';

import 'package:drift/drift.dart';

import '../persistence/app_database.dart';
import '../session/api_client.dart';
import '../session/contracts.dart';
import '../session/session_controller.dart';
import 'documents_client.dart';
import 'media_file_store.dart';
import 'media_store.dart';

/// A phase of ReplayWorker, not a second lifecycle/foreground worker. Its caller
/// holds the shared database lane across the entire PUT, finalize and DB result.
class MediaProcessor {
  MediaProcessor(
    this.store,
    this.documents,
    this.puts, {
    double Function()? jitter,
  }) : jitter = jitter ?? Random.secure().nextDouble;
  final MediaStore store;
  final DocumentTransport documents;
  final SignedPutTransport puts;
  final double Function() jitter;
  final _grants =
      <
        String,
        PendingDocumentUpload
      >{}; // ephemeral bearer grants, never SQLite/logs
  void forgetGrants() => _grants.clear();

  Future<void> process(
    SessionController session,
    SessionSnapshot owner,
    bool Function() current,
  ) async {
    await store.recover(owner.userId, owner.membership.tenantId);
    var count = 0;
    for (final candidate in await store.list(
      owner.userId,
      owner.membership.tenantId,
    )) {
      if (!current() || count >= 10) break;
      if (candidate.state == 'FINALIZED' ||
          candidate.reviewRequired ||
          candidate.fileState != 'PRESENT' ||
          candidate.nextRetryAt != null &&
              store.now().isBefore(candidate.nextRetryAt!)) {
        continue;
      }
      count++;
      if (!await store.claim(candidate)) continue;
      if (!current()) return;
      var row = (await store.find(
        candidate.userId,
        candidate.tenantId,
        candidate.id,
      ))!;
      try {
        if (row.serverDocumentId != null) {
          // First reconcile any possible successful PUT/finalize. No new PUT or
          // Document identity on an uncertain finalization or expired grant.
          try {
            final at = await documents.finalize(row);
            if (!current()) return;
            await _confirmed(row, at);
            continue;
          } on ApiProblem catch (e) {
            if (e.status != 409 || e.code != 'UPLOAD_NOT_COMPLETE') rethrow;
            if (!current()) return;
            final grant = _grants[row.id];
            if (grant == null ||
                grant.documentId != row.serverDocumentId ||
                !grant.expiresAt.isAfter(store.now())) {
              await store.verify(row);
              await store.change(
                row,
                const LocalMediaCompanion(
                  lastErrorCode: Value('UPLOAD_NOT_COMPLETE'),
                ),
              );
              row = (await store.find(row.userId, row.tenantId, row.id))!;
              await store.newAttempt(row);
              row = (await store.find(row.userId, row.tenantId, row.id))!;
              if (!current()) return;
            }
          }
        }
        await store.verify(row);
        if (!current()) return;
        if (row.serverDocumentId == null) {
          final grant = await documents.initialize(row);
          if (!current()) return;
          // Persist identity before the next network operation. Init has NO
          // replay guarantee; an ACK loss may leave an unlinked PENDING orphan.
          if (!await store.change(
            row,
            LocalMediaCompanion(
              state: const Value('UPLOAD_PENDING'),
              serverDocumentId: Value(grant.documentId),
              updatedAt: Value(store.now()),
            ),
          )) {
            continue;
          }
          _grants[row.id] = grant;
          row = (await store.find(row.userId, row.tenantId, row.id))!;
        }
        final grant = _grants[row.id];
        if (grant == null || grant.documentId != row.serverDocumentId) {
          throw const MediaFileProblem('UPLOAD_URL_EXPIRED');
        }
        if (!await store.change(
          row,
          LocalMediaCompanion(
            state: const Value('UPLOADING'),
            updatedAt: Value(store.now()),
          ),
        )) {
          continue;
        }
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        if (!current()) return;
        await puts.put(grant, row, store.files);
        if (!current()) return;
        if (!await store.change(
          row,
          LocalMediaCompanion(
            state: const Value('UPLOADED'),
            updatedAt: Value(store.now()),
          ),
        )) {
          continue;
        }
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        await store.change(
          row,
          LocalMediaCompanion(
            state: const Value('FINALIZE_PENDING'),
            updatedAt: Value(store.now()),
          ),
        );
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        if (!current()) return;
        final at = await documents.finalize(row);
        if (!current()) return;
        await _confirmed(row, at);
      } on ApiProblem catch (e) {
        if (!current()) return;
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        final retry =
            e.status == 408 ||
            e.status == 429 ||
            e.status >= 500 ||
            (e.status >= 200 &&
                e.status < 300 &&
                e.code == 'UNEXPECTED_RESPONSE');
        await store.failed(
          row,
          e.code,
          status: e.status,
          retryAt: retry ? _backoff(row, e.retryAfter) : null,
          review: !retry,
        );
        if ({401, 403}.contains(e.status)) {
          await session.rejectOperationalContext(e);
          return;
        }
        if (e.status == 404) {
          await session.refresh();
          return;
        }
      } on SignedPutProblem catch (e) {
        if (!current()) return;
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        final retry = e.status == 408 || e.status == 429 || e.status >= 500;
        if (!retry) _grants.remove(row.id);
        await store.failed(
          row,
          'UPLOAD_TRANSPORT_FAILED',
          status: e.status,
          retryAt: retry ? _backoff(row, e.retryAfter) : null,
          review: !retry,
        );
      } on MediaFileProblem catch (e) {
        if (!current()) return;
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        final expired = e.code == 'UPLOAD_URL_EXPIRED';
        await store.failed(
          row,
          e.code,
          review: !expired,
          retryAt: expired ? _backoff(row, null) : null,
        );
      } catch (_) {
        if (!current()) return;
        row = (await store.find(row.userId, row.tenantId, row.id))!;
        if (row.state == 'FINALIZED') continue; // ACK never becomes failure after refresh/storage uncertainty.
        await store.failed(
          row,
          'RESULT_UNCERTAIN',
          retryAt: _backoff(row, null),
        );
      }
    }
    if (current()) await store.cleanup(owner.userId, owner.membership.tenantId);
  }

  Future<void> _confirmed(LocalMediaData row, DateTime at) async {
    await store.change(
      row,
      LocalMediaCompanion(
        state: const Value('FINALIZED'),
        finalizedAt: Value(at),
        updatedAt: Value(store.now()),
        nextRetryAt: const Value(null),
        lastErrorCode: const Value(null),
        lastHttpStatus: const Value(200),
        reviewRequired: const Value(false),
      ),
    );
    _grants.remove(row.id);
  }

  DateTime _backoff(LocalMediaData row, DateTime? retryAfter) {
    final seconds = min(
      300,
      2 * pow(2, min(max(row.attemptCount - 1, 0), 8)),
    ).toDouble();
    final proposed = store.now().add(
      Duration(milliseconds: (seconds * (0.5 + jitter()) * 1000).round()),
    );
    return retryAfter != null && retryAfter.isAfter(proposed)
        ? retryAfter
        : proposed;
  }

  Future<DateTime?> nextRetry(String user, String tenant) async {
    final times =
        (await store.list(user, tenant))
            .where(
              (r) =>
                  r.state != 'FINALIZED' &&
                  !r.reviewRequired &&
                  r.fileState == 'PRESENT',
            )
            .map(
              (r) =>
                  r.nextRetryAt ?? store.now().add(const Duration(seconds: 1)),
            )
            .toList()
          ..sort();
    return times.firstOrNull;
  }
}
