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
const directory = mkdtempSync(join(tmpdir(), 'trailersaas-yard-check-contract-'));
let api;
try {
  const output = join(directory, 'contract.json');
  const result = spawnSync(process.execPath, [cli, 'bundle', fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url)),
    '--config', fileURLToPath(new URL('../redocly.yaml', import.meta.url)), '--output', output], { encoding: 'utf8', timeout: 30000, env: { ...process.env, REDOCLY_TELEMETRY: 'off' } });
  assert.ifError(result.error); assert.equal(result.status, 0, result.stdout + result.stderr);
  api = JSON.parse(readFileSync(output, 'utf8'));
} finally { rmSync(directory, { recursive: true, force: true }); }
const root = '/api/v1/tenants/{tenantId}/yard-checks';
const s = api.components.schemas;
const commandTypes = [
  ['', 'StartYardCheckRequest', '201'],
  ['/{yardCheckId}/scans', 'YardCheckScanRequest', '200'],
  ['/{yardCheckId}/completion', 'CompleteYardCheckRequest', '200'],
  ['/{yardCheckId}/cancellation', 'CancelYardCheckRequest', '200'],
  ['/{yardCheckId}/items/{itemId}/mark-location-unknown', 'YardCheckMarkUnknownRequest', '200'],
  ['/{yardCheckId}/items/{itemId}/location-correction', 'YardCheckCorrectionRequest', '200'],
];
test('#50 implements exactly eight Yard Check method surfaces without changing existing Yard commands', () => {
  const implemented = Object.entries(api.paths).flatMap(([path, value]) => Object.entries(value)
    .filter(([, op]) => op?.['x-implementation-status'] === 'implemented-issue-50').map(([method]) => `${method} ${path}`));
  assert.deepEqual(implemented.sort(), [...commandTypes.map(([suffix]) => `post ${root}${suffix}`), `get ${root}`, `get ${root}/{yardCheckId}`].sort());
  assert.equal(api.paths['/api/v1/tenants/{tenantId}/yard-moves'].post['x-implementation-status'], 'implemented-issue-48');
  assert.equal(api.paths['/api/v1/tenants/{tenantId}/yard/location-corrections'].post['x-implementation-status'], 'implemented-issue-49');
});
test('#50 critical commands are closed and use the existing required UUID ledger header with safe failures', () => {
  for (const [suffix, name, code] of commandTypes) {
    const op = api.paths[root + suffix].post;
    assert.equal(s[name].additionalProperties, false);
    assert.ok(op.parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'));
    assert.equal(op.requestBody.content['application/json'].schema.$ref, `#/components/schemas/${name}`);
    assert.ok(op.responses[code].content['application/json']);
    for (const status of ['400', '401', '403', '404', '409']) assert.ok(op.responses[status]);
    for (const text of ['YARD_CHECK_EXECUTE', 'ACTIVE membership', 'ACTIVE Tenant', 'MobileOperationLedger', 'IDEMPOTENCY_KEY_REUSED']) assert.ok(op.description.includes(text));
    assert.ok(!/actorUserId|driverId|tripId|source_type|actor_type/.test(JSON.stringify(s[name].properties)));
  }
});
test('#50 start freezes a single view and represents pending expected inventory without inventing MISSING', () => {
  assert.deepEqual(s.StartYardCheckRequest.required, ['siteId']);
  assert.deepEqual(s.StartYardCheckRequest.properties.locationId.type, ['string', 'null']);
  const description = api.paths[root].post.description;
  for (const text of ['ONE INSERT SELECT', 'OOS/load/coupling', 'Concurrent independent Checks', 'never MISSING', 'snapshotComplete=false']) assert.ok(description.includes(text));
  assert.deepEqual(s.YardCheckItem.properties.result.enum, ['MATCHED', 'MISSING', 'UNEXPECTED', 'LOCATION_MISMATCH', null]);
  assert.equal(s.YardCheckRecordedSnapshot.additionalProperties, false);
  assert.ok(!/customer|driver|tractor|order|document/i.test(JSON.stringify(s.YardCheckRecordedSnapshot.properties)));
});
test('#50 detail and list are bounded with explicit page/version/completeness semantics', () => {
  const list = api.paths[root].get, detail = api.paths[root + '/{yardCheckId}'].get;
  assert.equal(list.parameters.find(p => p.name === 'limit').schema.maximum, 50);
  assert.equal(detail.parameters.find(p => p.name === 'limit').schema.maximum, 200);
  assert.equal(s.YardChecksPage.properties.items.maxItems, 50);
  assert.equal(s.YardCheckDetail.properties.items.maxItems, 200);
  assert.ok(detail.description.includes('expectedVersion is REQUIRED with cursor') && detail.description.includes('STALE_VERSION'));
  assert.ok(detail.description.includes('unique item counts'));
  assert.equal(s.YardCheckDetail.properties.snapshotComplete.type, 'boolean');
  assert.equal(detail.parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'), false);
});
test('#50 scans classify observations and duplicates never replace original facts or move a Trailer', () => {
  assert.deepEqual(s.YardCheckScanRequest.required, ['trailerId', 'observedLocationId', 'scannedAt']);
  assert.equal(s.YardCheckScanRequest.properties.observedLocationId.type, 'string');
  assert.equal(s.YardCheckScanRequest.properties.scannedAt.format, 'date-time');
  assert.deepEqual(s.YardCheckScanResult.properties.outcome.enum, ['MATCHED', 'UNEXPECTED', 'LOCATION_MISMATCH', 'ALREADY_SCANNED']);
  const description = api.paths[root + '/{yardCheckId}/scans'].post.description;
  for (const text of ['IN_PROGRESS', 'not before server startedAt', 'without replacing actor/time/location', 'Never mutates Trailer']) assert.ok(description.includes(text));
});
test('#50 terminal commands preserve human authority and server time with no automatic reconciliation', () => {
  assert.deepEqual(s.CompleteYardCheckRequest.required, ['expectedVersion']);
  assert.equal(s.CompleteYardCheckRequest.properties.completedAt, undefined);
  assert.deepEqual(s.CancelYardCheckRequest.required, ['expectedVersion', 'reason']);
  assert.equal(s.CancelYardCheckRequest.properties.reason.maxLength, 4000);
  assert.ok(api.paths[root + '/{yardCheckId}/completion'].post.description.includes('No Trailer mutation, LOCATION_UNKNOWN or alert'));
  assert.ok(api.paths[root + '/{yardCheckId}/cancellation'].post.description.includes('null pending results'));
});
test('#50 separate reconciliation requires correction authority and atomic provisional Administration alert', () => {
  const missing = api.paths[root + '/{yardCheckId}/items/{itemId}/mark-location-unknown'].post;
  const correction = api.paths[root + '/{yardCheckId}/items/{itemId}/location-correction'].post;
  for (const op of [missing, correction]) for (const text of ['TRAILER_LOCATION_CORRECT', 'expectedTrailerVersion', 'HUMAN_USER/USER_ACTION', 'One reconciliation per item']) assert.ok(op.description.includes(text));
  for (const text of ['TRAILER_MISSING/WARNING/OPEN', 'No recipients, push, outbox, worker', 'OQ-059', 'preserves last-known/seen/Driver']) assert.ok(missing.description.includes(text));
  assert.ok(correction.description.includes('original observed active same-Site Location'));
  assert.deepEqual(s.YardCheckMovementEvent.properties.eventType.enum, ['LOCATION_MARKED_UNKNOWN', 'LOCATION_CORRECTED']);
  assert.equal(s.YardCheckReconciliationResult.additionalProperties, false);
});
test('#50 migration preserves pending result and terminal history under runtime RLS with no second ledger', () => {
  const sql = readFileSync(fileURLToPath(new URL('../../../backend/src/main/resources/db/migration/V24__yard_check_snapshots.sql', import.meta.url)), 'utf8');
  for (const table of ['yard_checks', 'yard_check_items', 'yard_check_reconciliations', 'alerts']) {
    assert.ok(sql.includes(`ALTER TABLE ${table} ENABLE ROW LEVEL SECURITY`));
    assert.ok(sql.includes(`CREATE POLICY ${table}_tenant ON ${table}`));
  }
  assert.ok(sql.includes('UNIQUE(tenant_id,yard_check_id,trailer_id)'));
  assert.ok(sql.includes('result varchar NULL'));
  assert.ok(sql.includes('DEFERRABLE INITIALLY DEFERRED'));
  assert.ok(sql.includes("AND p.status='PROCESSING'"));
  assert.ok(!/CREATE TABLE (processed_client_operations|alert_recipients|outbox_jobs)/.test(sql));
});
