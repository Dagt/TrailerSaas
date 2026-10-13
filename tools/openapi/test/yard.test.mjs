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
const directory = mkdtempSync(join(tmpdir(), 'trailersaas-yard-contract-'));
const output = join(directory, 'contract.json');
let api;
try {
  const result = spawnSync(process.execPath, [cli, 'bundle', fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url)),
    '--config', fileURLToPath(new URL('../redocly.yaml', import.meta.url)), '--output', output], { encoding: 'utf8', timeout: 30000, env: { ...process.env, REDOCLY_TELEMETRY: 'off' } });
  assert.ifError(result.error);assert.equal(result.status, 0, result.stdout + result.stderr);api = JSON.parse(readFileSync(output, 'utf8'));
} finally { rmSync(directory, { recursive: true, force: true }); }

const root = '/api/v1/tenants/{tenantId}';
const move = api.paths[`${root}/yard-moves`];

test('#48 exposes only the four implemented scoped Yard operations', () => {
  const implemented = Object.entries(api.paths).filter(([, path]) => Object.values(path).some(op => op?.['x-implementation-status'] === 'implemented-issue-48')).map(([path]) => path);
  assert.deepEqual(implemented.sort(), [`${root}/yard-moves`, `${root}/yard/sites`, `${root}/yard/sites/{siteId}/locations`, `${root}/yard/trailers/{trailerId}`].sort());
  for (const path of implemented) {
    const operation = api.paths[path].get ?? api.paths[path].post;
    for (const rule of ['YARD_MOVE_EXECUTE', 'ACTIVE membership', 'ACTIVE Tenant']) assert.ok(operation.description.includes(rule), `${path}: ${rule}`);
  }
});
test('#48 HTTP mutation has the original strict five-field request and idempotency header', () => {
  const schema = api.components.schemas.YardMoveRequest;
  const fields = ['trailerId', 'fromLocationId', 'toLocationId', 'observedAt', 'expectedTrailerVersion'];
  assert.deepEqual(schema.required, fields);assert.deepEqual(Object.keys(schema.properties), fields);assert.equal(schema.additionalProperties, false);
  assert.equal(schema.properties.expectedTrailerVersion.type, 'integer');assert.equal(schema.properties.expectedTrailerVersion.minimum, 0);
  assert.equal(schema.properties.observedAt.format, 'date-time');assert.ok(schema.properties.observedAt.pattern.includes('{4}'));
  for (const field of fields.slice(0, 3)) assert.equal(schema.properties[field].format, 'uuid');
  assert.ok(move.parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'));
  assert.equal(api.components.parameters.IdempotencyKey.required, true);
  assert.deepEqual(move.post.requestBody.content['application/json'].schema, { $ref: '#/components/schemas/YardMoveRequest' });
});
test('#48 preserves physical authority, specific conflicts, chronology and human provenance', () => {
  for (const rule of ['MobileOperationLedger', 'TrailerMovementProjection.yardMove', 'MovementRules.yard', 'IN_SERVICE', 'AT_LOCATION', 'UNCOUPLED',
    'same Site', 'LOCATION_MISMATCH', 'STALE_VERSION', 'HUMAN_USER', 'USER_ACTION', 'YARD_MOVED', 'recordedAt', 'Load State, Orders', 'IDEMPOTENCY_KEY_REUSED']) assert.ok(move.post.description.includes(rule), rule);
  assert.ok(move.post.responses['409'].description.includes('LOCATION_MISMATCH'));
  assert.ok(move.post.responses['409'].description.includes('STALE_VERSION'));
  assert.ok(move.post.description.includes('even if version also changed'));
});
test('#48 Yard context is bounded and has no coordinates or fake unknown location', () => {
  const schema = api.components.schemas;
  for (const name of ['YardCustomer', 'YardLocation', 'YardSite', 'YardSitesPage', 'YardLocationsPage', 'YardRecordedLocation', 'YardTrailer']) assert.equal(schema[name].additionalProperties, false);
  assert.equal(schema.YardSitesPage.properties.items.maxItems, 50);
  assert.equal(schema.YardSite.properties.locations.maxItems, 200);
  assert.equal(schema.YardLocationsPage.properties.items.maxItems, 200);
  assert.deepEqual(schema.YardLocation.properties.type.enum, ['BASE', 'YARD', 'DOCK', 'CUSTOMER_AREA', 'SHOP', 'OTHER']);
  assert.ok(!schema.YardLocation.properties.type.enum.includes('LOCATION_UNKNOWN'));
  assert.ok(!/latitude|longitude|actor_user_id|driverId|tractorId|orders/.test(JSON.stringify(schema.YardTrailer)));
  assert.ok(!/latitude|longitude/.test(JSON.stringify(schema.YardSite)));
});
test('#48 result and safe conflict describe the actual Yard projection/event DTOs', () => {
  const result = api.components.schemas.YardMoveResult;
  assert.equal(result.additionalProperties, false);assert.deepEqual(result.required, ['trailer', 'movementEvent']);
  assert.deepEqual(result.properties.movementEvent.required, ['id', 'eventType', 'occurredAt', 'recordedAt']);
  assert.equal(result.properties.movementEvent.properties.eventType.const, 'YARD_MOVED');
  assert.equal(result.properties.movementEvent.additionalProperties, false);
  const trailer = api.components.schemas.YardTrailer;
  assert.ok(trailer.properties.currentLocation.oneOf.some(s => s.type === 'null'));
  assert.ok(trailer.required.includes('version') && trailer.required.includes('couplingState'));
  assert.deepEqual(api.components.schemas.YardConflictProblem.allOf[1].properties.currentTrailer.$ref, '#/components/schemas/YardTrailer');
});
