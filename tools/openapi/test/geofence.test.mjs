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
const contract = fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url));
const config = fileURLToPath(new URL('../redocly.yaml', import.meta.url));
const directory = mkdtempSync(join(tmpdir(), 'trailersaas-geofence-contract-'));
const output = join(directory, 'contract.json');
let api;
try {
  const result = spawnSync(process.execPath, [cli, 'bundle', contract, '--config', config, '--output', output], {
    encoding: 'utf8', timeout: 30000, env: { ...process.env, REDOCLY_TELEMETRY: 'off' },
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  api = JSON.parse(readFileSync(output, 'utf8'));
} finally {
  rmSync(directory, { recursive: true, force: true });
}

test('#47 keeps one authorized human Arrival endpoint and existing ledger contract', () => {
  const path = '/api/v1/tenants/{tenantId}/stops/{stopId}/arrivals';
  const operation = api.paths[path].post;
  assert.equal(operation['x-implementation-status'], 'implemented-issue-47');
  for (const rule of ['ARRIVAL_CONFIRM', 'TRIP_EXECUTE_ASSIGNED', 'ACTIVE membership', 'own active linked Driver',
    'current Trip assignment', 'IN_TRANSIT', 'EN_ROUTE', 'HUMAN_USER', 'MOBILE_AUTOMATION', 'USER_ACTION', 'IDEMPOTENCY_KEY_REUSED']) {
    assert.ok(operation.description.includes(rule), rule);
  }
  assert.ok(api.paths[path].parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'));
  assert.deepEqual(operation.requestBody.content['application/json'].schema, { $ref: '#/components/schemas/ConfirmedArrivalRequestImplemented' });
  assert.deepEqual(Object.keys(api.paths).filter(p => /geofence|tracking|locations-history/i.test(p)), []);
});

test('#47 sources and coordinates are closed and conditional evidence is explicit', () => {
  const schema = api.components.schemas.ConfirmedArrivalRequestImplemented;
  assert.equal(schema.additionalProperties, false);
  assert.deepEqual(Object.keys(schema.properties), ['occurredAt', 'source', 'coordinates']);
  assert.deepEqual(schema.properties.source.enum, ['MANUAL', 'GEOFENCE_CONFIRMED']);
  assert.equal(schema.properties.coordinates.additionalProperties, false);
  assert.deepEqual(schema.properties.coordinates.required, ['latitude', 'longitude', 'accuracyM']);
  assert.equal(schema.properties.coordinates.properties.latitude.minimum, -90);
  assert.equal(schema.properties.coordinates.properties.longitude.maximum, 180);
  assert.equal(schema.properties.coordinates.properties.accuracyM.minimum, 0);
  assert.equal(schema.allOf[0].if.properties.source.const, 'GEOFENCE_CONFIRMED');
  assert.deepEqual(schema.allOf[0].then.required, ['coordinates']);
  assert.equal(schema.allOf[0].then.properties.coordinates.type, 'object');
  assert.equal(schema.allOf[0].then.properties.coordinates.properties.accuracyM.exclusiveMinimum, 0);
});

test('#47 next operational Site extends only authenticated own detail', () => {
  const operation = api.paths['/api/v1/tenants/{tenantId}/driver/me/trips/{tripId}'].get;
  for (const rule of ['TRIP_VIEW_ASSIGNED', 'own active Driver/current assignment', 'next EN_ROUTE Stop', 'latitude, longitude and geofenceRadiusM']) {
    assert.ok(operation.description.includes(rule), rule);
  }
  const context = api.components.schemas.DriverArrivalAssistance;
  assert.equal(context.additionalProperties, false);
  for (const field of ['tripId', 'tripVersion', 'driverId', 'stopId', 'stopVersion', 'siteId', 'siteVersion', 'latitude', 'longitude', 'geofenceRadiusM']) {
    assert.ok(context.required.includes(field));
  }
  assert.equal(context.properties.stopStatus.const, 'EN_ROUTE');
  assert.equal(context.properties.geofenceRadiusM.minimum, 1);
  assert.equal(context.properties.geofenceRadiusM.default, undefined);
  assert.ok(api.components.schemas.DriverExecutionDetail.required.includes('arrivalAssistance'));
});

test('#47 native privacy and the same Arrival lane remain intact after the #49 schema upgrade', () => {
  const read = path => readFileSync(fileURLToPath(new URL(`../../../${path}`, import.meta.url)), 'utf8');
  const android = read('mobile/android/app/src/main/AndroidManifest.xml');
  assert.ok(android.includes('ACCESS_COARSE_LOCATION') && android.includes('ACCESS_FINE_LOCATION'));
  assert.ok(!android.includes('ACCESS_BACKGROUND_LOCATION') && !android.includes('FOREGROUND_SERVICE_LOCATION'));
  const ios = read('mobile/ios/Runner/Info.plist');
  assert.ok(ios.includes('NSLocationWhenInUseUsageDescription'));
  assert.ok(!ios.includes('NSLocationAlways') && !ios.includes('UIBackgroundModes'));
  const adapter = read('mobile/lib/src/platform/native_location_assistance.dart');
  assert.ok(adapter.includes('allowBackgroundLocationUpdates: false'));
  assert.ok(!adapter.includes('foregroundNotificationConfig:'));
  const schema = read('mobile/lib/src/persistence/app_database.dart');
  assert.match(schema, /schemaVersion\s*=>\s*8/);
  assert.ok(!/gps|breadcrumb|coordinate_history/i.test(schema));
});
