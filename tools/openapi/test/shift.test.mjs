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
test('#43 photo positions, own-home bounds and real departure remain separate from #45 completion',()=>{
  const s=api.components.schemas;
  assert.deepEqual(Object.keys(s.DriverPreTripRequest.properties.documentIds.properties),['front','rear','driverSide','passengerSide']);
  assert.equal(s.DriverPreTripRequest.properties.documentIds.additionalProperties,false);
  assert.deepEqual(s.DriverPreTripRequest.properties.tripId.type,['string','null']);
  for(const field of ['inspections','authorizedTractors','usedTractors'])assert.equal(s.DriverShiftHome.properties[field].maxItems,50);
  assert.match(api.paths['/api/v1/tenants/{tenantId}/trips/{tripId}/departures'].post.description,/real open own Shift/);
  for(const path of ['/api/v1/tenants/{tenantId}/stops/{stopId}/delivery-completions','/api/v1/tenants/{tenantId}/driver/me/fuel-events','/api/v1/tenants/{tenantId}/trips/{tripId}/exceptions'])assert.equal(api.paths[path].post['x-implementation-status'],'implemented-issue-45');
});
test('#45 strict evidence commands expose safe typed results and fresh capability gates',()=>{
  const root='/api/v1/tenants/{tenantId}';
  for(const [path,cap,request,response] of [
    ['/stops/{stopId}/delivery-completions','POD_UPLOAD','DeliveryCompletionRequest','DeliveryCompletionResultImplemented'],
    ['/driver/me/fuel-events','FUEL_EVENT_CREATE','FuelEventRequestImplemented','FuelEventResultImplemented'],
    ['/trips/{tripId}/exceptions','EXCEPTION_REPORT','OperationalExceptionRequestImplemented','OperationalExceptionResultImplemented']]) {
    const route=api.paths[root+path],op=route.post;
    assert.equal(op['x-implementation-status'],'implemented-issue-45');assert(op.description.includes(cap));assert(route.parameters.some(p=>p.$ref==='#/components/parameters/IdempotencyKey'));
    assert.equal(op.requestBody.content['application/json'].schema.$ref,'#/components/schemas/'+request);assert.equal(op.responses['200'].content['application/json'].schema.$ref,'#/components/schemas/'+response);
    const dto=api.components.schemas[request];
    for(const branch of dto.oneOf??[dto]){assert.equal(branch.additionalProperties,false);assert(branch.required.includes('occurredAt'));for(const forbidden of ['tenantId','driverId','userId','status','approved','serviceabilityState'])assert(!Object.hasOwn(branch.properties,forbidden));}
    const result=api.components.schemas[response];assert.equal(result.additionalProperties,false);for(const forbidden of ['tenantId','actorUserId','objectKey','claims'])assert(!Object.hasOwn(result.properties,forbidden));
  }
});
test('#45 exact POD modes, receipt-only Fuel and exception categories preserve scope',()=>{
  const s=api.components.schemas;
  assert.deepEqual(s.DeliveryCompletionRequest.oneOf.map(x=>x.properties.evidenceMode.const),['SIGNED_BOL','NO_RECEIVER_AVAILABLE']);
  assert.deepEqual(s.DeliveryCompletionRequest.oneOf[1].required,['evidenceMode','bolDocumentId','deliveryPhotoDocumentId','comment','occurredAt']);
  assert.equal(s.DeliveryCompletionRequest.oneOf[1].properties.comment.maxLength,4000);
  assert.deepEqual(Object.keys(s.FuelEventRequestImplemented.properties),['tripId','tractorId','odometer','receiptDocumentId','occurredAt']);
  assert.deepEqual(s.OperationalExceptionRequestImplemented.properties.category.enum,['DELAY_TRAFFIC','EQUIPMENT_DAMAGE','DESTINATION_CUSTOMER_ISSUE','LOAD_REJECTED','OTHER']);
  assert.equal(s.OperationalExceptionRequestImplemented.properties.documentIds.uniqueItems,true);
});
test('#45 exact result fields and safe Problem Details preserve UUID idempotency',()=>{
  const s=api.components.schemas;
  for(const [name,fields] of [
    ['DeliveryCompletionResultImplemented',['stopId','tripId','stopStatus','tripStatus','stopVersion','tripVersion','completedAt']],
    ['FuelEventResultImplemented',['id','tripId','tractorId','odometer','receiptDocumentId','occurredAt','createdAt']],
    ['OperationalExceptionResultImplemented',['id','tripId','stopId','category','comment','documentIds','occurredAt','recordedAt']]]){
    assert.deepEqual(Object.keys(s[name].properties),fields);assert.deepEqual(s[name].required,fields);
  }
  assert.equal(api.components.parameters.IdempotencyKey.required,true);
  assert.equal(api.components.parameters.IdempotencyKey.schema.format,'uuid');
  for(const path of ['/stops/{stopId}/delivery-completions','/driver/me/fuel-events','/trips/{tripId}/exceptions']){
    const route=api.paths['/api/v1/tenants/{tenantId}'+path];
    assert.deepEqual(Object.keys(route).filter(x=>['get','post','put','patch','delete'].includes(x)),['post']);
    assert.equal(route.post.responses.default.$ref,'#/components/responses/Problem');
    for(const code of ['VALIDATION_FAILED','CAPABILITY_DENIED','RESOURCE_NOT_FOUND','EVIDENCE_REQUIRED','UPLOAD_NOT_COMPLETE','INVALID_STATE_TRANSITION','RESOURCE_CONFLICT','IDEMPOTENCY_KEY_REUSED'])assert(route.post.description.includes(code));
  }
});
