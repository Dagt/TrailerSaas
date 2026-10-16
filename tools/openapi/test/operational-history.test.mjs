import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
const require = createRequire(import.meta.url), dir = mkdtempSync(join(tmpdir(), 'trailersaas-history-'));
let api;
try {
  const cli = join(dirname(require.resolve('@redocly/cli/package.json')), 'bin/cli.js'), output = join(dir, 'api.json');
  const result = spawnSync(process.execPath, [cli, 'bundle', fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url)), '--config', fileURLToPath(new URL('../redocly.yaml', import.meta.url)), '--output', output], { encoding: 'utf8', timeout: 30000, env: { ...process.env, REDOCLY_TELEMETRY: 'off' } });
  assert.ifError(result.error); assert.equal(result.status, 0, result.stdout + result.stderr); api = JSON.parse(readFileSync(output, 'utf8'));
} finally { rmSync(dir, { recursive: true, force: true }); }
const root = '/api/v1/tenants/{tenantId}/trips/{tripId}', schemas = api.components.schemas;
test('#62 preserves the legacy timeline and adds explicit bounded SQL cursor pages', () => {
  const get = api.paths[root + '/timeline'].get;
  assert.equal(get['x-implementation-status'], 'implemented-issue-62');
  assert.match(get.description, /TRIP_VIEW_ALL/); assert.match(get.description, /legacy complete/); assert.match(get.description, /SQL keyset/);
  assert.match(get.description, /authenticated User, Tenant, Trip/); assert.match(get.description, /every page reauthorizes/);
  assert.deepEqual(get.parameters.map(p => p.name), ['limit', 'cursor']);
  assert.equal(get.parameters[0].schema.maximum, 100); assert.equal(get.parameters[0].schema.minimum, 1);
  assert.equal(get.parameters[1].schema.maxLength, 512);
  const variants = get.responses['200'].content['application/json'].schema.oneOf.map(v => v.$ref.split('/').at(-1));
  assert.deepEqual(variants, ['TripOperationalTimelineImplemented', 'TripOperationalTimelinePageImplemented']);
  assert.deepEqual(schemas.TripOperationalTimelineImplemented.required, ['items']);
  assert.deepEqual(schemas.TripOperationalTimelinePageImplemented.required, ['items', 'nextCursor']);
  assert.equal(schemas.TripOperationalTimelinePageImplemented.additionalProperties, false);
  assert.equal(schemas.TripOperationalTimelinePageImplemented.properties.items.maxItems, 100);
});
test('#62 avoids loading all embedded history without changing default Trip detail', () => {
  const get = api.paths[root].get;
  const parameter = get.parameters.find(p => p.name === 'includeHistory');
  assert.deepEqual(parameter.schema, { type: 'boolean', default: true });
  assert.match(get.description, /empty arrays/); assert.match(get.description, /Stops\/current state are unchanged/);
  assert.ok(schemas.TripDetailImplemented.required.includes('assignmentHistory'));
  assert.ok(schemas.TripDetailImplemented.required.includes('operationalHistory'));
});
test('#62 Web Audit filter catalogs match the actual API and do not broaden grants', () => {
  const source = readFileSync(fileURLToPath(new URL('../../../web/src/areas/tenant-admin/audit/contracts.ts', import.meta.url)), 'utf8');
  for (const [name, variable] of [['AuditActionCode', 'auditActionCode'], ['AuditEntityType', 'auditEntityType']]) {
    const match = source.match(new RegExp(`export const ${variable} = z.enum\\((\\[[^\\n]+?\\])\\)`));
    assert.ok(match); assert.deepEqual(JSON.parse(match[1]), schemas[name].enum);
  }
  assert.equal(api.paths['/api/v1/tenants/{tenantId}/audit-events'].post, undefined);
  assert.match(api.paths['/api/v1/tenants/{tenantId}/audit-events'].get.description, /AUDIT_VIEW/);
});
