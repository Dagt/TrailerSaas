import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const require = createRequire(import.meta.url);
const cli = join(dirname(require.resolve('@redocly/cli/package.json')), 'bin/cli.js');
const directory = mkdtempSync(join(tmpdir(), 'trailersaas-yard-corrective-contract-'));
let api;
try {
  const output = join(directory, 'contract.json');
  const result = spawnSync(process.execPath, [cli, 'bundle', fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url)),
    '--config', fileURLToPath(new URL('../redocly.yaml', import.meta.url)), '--output', output], { encoding: 'utf8', timeout: 30000, env: { ...process.env, REDOCLY_TELEMETRY: 'off' } });
  assert.ifError(result.error); assert.equal(result.status, 0, result.stdout + result.stderr);
  api = JSON.parse(readFileSync(output, 'utf8'));
} finally { rmSync(directory, { recursive: true, force: true }); }
const root = '/api/v1/tenants/{tenantId}';
const types = [['location-corrections', 'LocationCorrection', 'TRAILER_LOCATION_CORRECT', 'LOCATION_CORRECTED'],
  ['load-state-changes', 'LoadStateChange', 'TRAILER_STATE_CORRECT', 'LOAD_STATE_CHANGED'],
  ['out-of-service', 'Oos', 'TRAILER_OOS_MARK', 'MARKED_OOS'],
  ['discrepancy-reports', 'DiscrepancyReport', 'YARD_MOVE_EXECUTE', null]];
const schema = api.components.schemas;

test('#49 exposes bounded typed orchestration and scoped damage upload without replacing admin or Yard Move', () => {
  const implemented = Object.entries(api.paths).filter(([, p]) => Object.values(p).some(op => op?.['x-implementation-status'] === 'implemented-issue-49')).map(([p]) => p).sort();
  assert.deepEqual(implemented, [...types.map(([p]) => `${root}/yard/${p}`), `${root}/trailers/{trailerId}/damage-uploads`].sort());
  assert.equal(api.paths[`${root}/yard-moves`].post['x-implementation-status'], 'implemented-issue-48');
  for (const path of ['location-corrections', 'load-state-changes', 'out-of-service', 'return-to-service']) {
    assert.ok(api.paths[`${root}/trailers/{trailerId}/${path}`].post);
    assert.notEqual(api.paths[`${root}/trailers/{trailerId}/${path}`].post['x-implementation-status'], 'implemented-issue-49');
  }
});
test('#49 mutations use exact authorization and the existing UUID ledger with server-derived provenance', () => {
  for (const [path, suffix, capability] of types) {
    const p = api.paths[`${root}/yard/${path}`];
    for (const text of ['YARD_MOVE_EXECUTE', capability, 'ACTIVE membership', 'ACTIVE Tenant', 'MobileOperationLedger', 'HUMAN_USER', 'USER_ACTION', 'IDEMPOTENCY_KEY_REUSED']) assert.ok(p.post.description.includes(text), `${path}: ${text}`);
    assert.ok(p.parameters.some(v => v.$ref === '#/components/parameters/IdempotencyKey') || p.post.parameters?.some(v => v.$ref === '#/components/parameters/IdempotencyKey'));
    assert.deepEqual(p.post.requestBody.content['application/json'].schema, { $ref: `#/components/schemas/Yard${suffix}Request` });
    const input = schema[`Yard${suffix}Request`];
    assert.equal(input.additionalProperties, false);
    assert.deepEqual(input.required, Object.keys(input.properties));
    assert.ok(!/actorUserId|source_type|actor_type|driverId|tripId|tractorId/.test(JSON.stringify(input)));
    assert.equal(input.properties.observedAt.format, 'date-time');
    assert.equal(input.properties.observedAt.pattern.includes('{4}'), true);
    assert.equal(input.properties.reason?.maxLength ?? input.properties.comment.maxLength, 4000);
  }
});
test('#49 physical versions and load dimensions match actual command contracts', () => {
  for (const [, suffix, , event] of types.filter(v => v[3])) {
    const input = schema[`Yard${suffix}Request`], result = schema[`Yard${suffix}Result`];
    assert.equal(input.properties.expectedTrailerVersion.type, 'integer');
    assert.equal(input.properties.expectedTrailerVersion.minimum, 0);
    assert.equal(result.additionalProperties, false);
    assert.equal(result.properties.movementEvent.additionalProperties, false);
    assert.equal(result.properties.movementEvent.properties.eventType.const, event);
    assert.deepEqual(result.properties.movementEvent.required, ['id', 'eventType', 'occurredAt', 'recordedAt']);
  }
  assert.deepEqual(schema.YardLoadStateChangeRequest.properties.loadState.enum, ['EMPTY', 'LOADED', 'UNKNOWN']);
  assert.ok(api.paths[`${root}/yard/load-state-changes`].post.description.includes('LOCATION_UNKNOWN or IN_TRANSIT'));
});
test('#49 report keeps historical observation while separate bounded review has no mutation grants', () => {
  const input = schema.YardDiscrepancyReportRequest;
  assert.equal(input.properties.expectedTrailerVersion, undefined);
  assert.deepEqual(input.properties.recordedLocationId.type, ['string', 'null']);
  assert.equal(input.properties.observedTrailerVersion.minimum, 0);
  assert.ok(input.properties.observedTrailerVersion.description.includes('cannot exceed'));
  assert.equal(schema.YardDiscrepancyReport.required.length, 11);
  assert.equal(schema.YardDiscrepancyReport.additionalProperties, false);
  assert.deepEqual(schema.YardDiscrepancyReportResult.required, ['trailer', 'report']);
  assert.equal(schema.YardDiscrepancyReportResult.properties.movementEvent, undefined);
  const read = api.paths[`${root}/yard/discrepancy-reports`].get;
  assert.ok(read.description.includes('TRAILER_VIEW_ALL'));
  assert.equal(read.parameters.find(v => v.name === 'limit').schema.maximum, 50);
  assert.equal(schema.YardDiscrepancyReportsPage.properties.items.maxItems, 50);
  assert.equal(api.paths[`${root}/yard/discrepancy-reports`].patch, undefined);
  assert.equal(api.paths[`${root}/yard/discrepancy-reports`].delete, undefined);
});
test('#49 OOS accepts only the distinct finalized contextual photo set with atomic event evidence ledger', () => {
  const photos = schema.YardOosRequest.properties.documentIds;
  assert.equal(photos.minItems, 1); assert.equal(photos.maxItems, 50);
  assert.equal(photos.uniqueItems, true); assert.equal(photos.items.format, 'uuid');
  const description = api.paths[`${root}/yard/out-of-service`].post.description;
  for (const text of ['own UPLOADED DAMAGE_PHOTO', 'exclusively linked', 'only one Yard OOS event', 'atomically in PostgreSQL', 'R2 upload precedes', 'No automatic Drop', 'No Return grant']) assert.ok(description.includes(text), text);
  assert.deepEqual(schema.YardOosResult.required, ['trailer', 'movementEvent', 'documentIds']);
});
test('#49 private damage transport reuses a closed Initialize DTO and restricts type and MIME', () => {
  const upload = api.paths[`${root}/trailers/{trailerId}/damage-uploads`].post;
  assert.ok(upload.description.includes('TRAILER_OOS_MARK') && upload.description.includes('No Driver/Trip'));
  assert.ok(upload.description.includes('not idempotent') && upload.description.includes('never creates MARKED_OOS'));
  assert.deepEqual(upload.responses['201'].content['application/json'].schema, { $ref: '#/components/schemas/InitializeUploadResponseImplemented' });
  const damage = schema.TrailerDamageUploadRequest.allOf;
  assert.deepEqual(damage[0], { $ref: '#/components/schemas/InitializeUploadRequestImplemented' });
  assert.equal(schema.InitializeUploadRequestImplemented.additionalProperties, false);
  assert.equal(damage[1].properties.documentType.const, 'DAMAGE_PHOTO');
  assert.deepEqual(damage[1].properties.contentType.enum, ['image/jpeg', 'image/png']);
});
test('#49 SQLite preserves a single operation graph and native camera reuses existing permission surface', () => {
  const read = p => readFileSync(fileURLToPath(new URL(`../../../${p}`, import.meta.url)), 'utf8');
  const sql = read('backend/src/main/resources/db/migration/V23__yard_reports_oos_evidence.sql');
  for (const table of ['yard_discrepancy_reports', 'trailer_oos_evidence']) {
    assert.ok(sql.includes(table)); assert.ok(sql.includes(`ALTER TABLE ${table} ENABLE ROW LEVEL SECURITY`));
    assert.ok(sql.includes(`CREATE POLICY ${table}_tenant ON ${table}`));
  }
  const native = read('mobile/lib/src/yard/yard_damage_store.dart');
  assert.ok(native.includes('InspectionCamera') && native.includes('media.files.capture'));
  assert.ok(!native.includes('ImagePicker()'));
  const replay = read('mobile/lib/src/yard/yard_commands.dart');
  assert.ok(replay.includes("source.state != 'ACKNOWLEDGED'"));
  assert.ok(replay.includes('Invalid physical predecessor'));
  const db = read('mobile/lib/src/persistence/app_database.dart');
  assert.match(db, /schemaVersion\s*=>\s*8/);
  assert.ok(!/gps|breadcrumb|coordinate_history/i.test(db));
});
test('#49 one shared iOS camera purpose explains QR inspection and damage without background permissions', () => {
  const ios = readFileSync(fileURLToPath(new URL('../../../mobile/ios/Runner/Info.plist', import.meta.url)), 'utf8');
  assert.equal(ios.match(/<key>NSCameraUsageDescription<\/key>/g)?.length, 1);
  assert.equal(ios.match(/<key>NSPhotoLibraryUsageDescription<\/key>/g)?.length, 1);
  assert.ok(ios.includes('Scan Trailer QR labels and capture inspection or damage photographs'));
  assert.ok(!ios.includes('NSLocationAlways') && !ios.includes('UIBackgroundModes'));
});
