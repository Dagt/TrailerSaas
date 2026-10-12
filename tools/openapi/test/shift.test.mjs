import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const directory=mkdtempSync(join(tmpdir(),'trailersaas-shift-contract-'));
let api;
try {
  const cli=join(dirname(require.resolve('@redocly/cli/package.json')),'bin/cli.js');
  const output=join(directory,'contract.json');
  const result=spawnSync(process.execPath,[cli,'bundle',fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml',import.meta.url)),'--config',fileURLToPath(new URL('../redocly.yaml',import.meta.url)),'--output',output],{encoding:'utf8',timeout:30000,env:{...process.env,REDOCLY_TELEMETRY:'off'}});
  assert.ifError(result.error);assert.equal(result.status,0,result.stdout+result.stderr);
  api=JSON.parse(readFileSync(output,'utf8'));
} finally {rmSync(directory,{recursive:true,force:true});}
test('#43 four explicit strict commands preserve capability and UUID replay contracts', () => {
  const base='/api/v1/tenants/{tenantId}/driver/me';
  for(const [suffix,cap,request,response] of [
    ['/shifts/on-duty','SHIFT_START','DriverOnDutyRequest','DriverShift'],
    ['/shifts/{shiftId}/off-duty','SHIFT_END','DriverOffDutyRequest','DriverShift'],
    ['/inspections/pre-trip','PRETRIP_CREATE','DriverPreTripRequest','DriverInspection'],
    ['/inspections/post-trip','POSTTRIP_CREATE','DriverPostTripRequest','DriverInspection']]) {
    const op=api.paths[base+suffix].post;
    assert.equal(op['x-implementation-status'],'implemented-issue-43');
    assert(op.description.includes(cap));
    assert(op.parameters.some(p=>p.$ref==='#/components/parameters/IdempotencyKey'));
    assert.equal(op.requestBody.content['application/json'].schema.$ref,'#/components/schemas/'+request);
    assert.equal(op.responses['200'].content['application/json'].schema.$ref,'#/components/schemas/'+response);
    const dto=api.components.schemas[request];assert.equal(dto.additionalProperties,false);
    assert(dto.required.includes('occurredAt'));
    for(const forbidden of ['driverId','userId','tenantId','state','approved','preTripComplete'])assert(!Object.hasOwn(dto.properties,forbidden));
  }
});
test('#43 photo positions, own-home bounds and real departure are explicit without #45 completion',()=>{
  const s=api.components.schemas;
  assert.deepEqual(Object.keys(s.DriverPreTripRequest.properties.documentIds.properties),['front','rear','driverSide','passengerSide']);
  assert.equal(s.DriverPreTripRequest.properties.documentIds.additionalProperties,false);
  assert.deepEqual(s.DriverPreTripRequest.properties.tripId.type,['string','null']);
  for(const field of ['inspections','authorizedTractors','usedTractors'])assert.equal(s.DriverShiftHome.properties[field].maxItems,50);
  assert.match(api.paths['/api/v1/tenants/{tenantId}/trips/{tripId}/departures'].post.description,/real open own Shift/);
  for(const [path,item] of Object.entries(api.paths)) if(/delivery-completions|fuel-events|\/exceptions$/.test(path))assert(!item.post?.['x-implementation-status']?.startsWith('implemented'));
});
