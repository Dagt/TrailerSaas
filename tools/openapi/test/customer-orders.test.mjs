import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
const require=createRequire(import.meta.url), directory=mkdtempSync(join(tmpdir(),'trailersaas-customer-orders-'));
let api;
try {
  const cli=join(dirname(require.resolve('@redocly/cli/package.json')),'bin/cli.js'),output=join(directory,'api.json');
  const result=spawnSync(process.execPath,[cli,'bundle',fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml',import.meta.url)),'--config',fileURLToPath(new URL('../redocly.yaml',import.meta.url)),'--output',output],{encoding:'utf8',timeout:30000,env:{...process.env,REDOCLY_TELEMETRY:'off'}});
  assert.ifError(result.error);assert.equal(result.status,0,result.stdout+result.stderr);api=JSON.parse(readFileSync(output,'utf8'));
} finally {rmSync(directory,{recursive:true,force:true});}
const root='/api/v1/tenants/{tenantId}',s=api.components.schemas;
const resolve=value=>value.$ref?value.$ref.split('/').slice(1).reduce((node,key)=>node[key],api):value;
test('#59 Order queries preserve real keyset filters and no fictitious appointment/active filter',()=>{
  const names=api.paths[root+'/orders'].get.parameters.map(p=>resolve(p).name);
  assert.deepEqual(names.slice().sort(),['customerId','status','unplanned','cursor','limit'].sort());
  assert.equal(s.OrderList.additionalProperties,false);assert.deepEqual(s.OrderList.required,['items','nextCursor']);
  assert.match(api.paths[root+'/orders'].get.description,/Customer|customer/);
});
test('#59 creation is strict, multi-Stop, optional cargo/Trailer and has no invented replay key',()=>{
  const body=s.CreateOrderRequestImplemented;assert.equal(body.additionalProperties,false);assert.deepEqual(body.required,['customerId','orderNumber','requestedStops']);
  assert.equal(body.properties.requestedStops.minItems,1);assert.equal(body.properties.cargoLines.type,'array');
  assert.ok(!body.required.includes('plannedTrailerId'));assert.ok(!body.required.includes('cargoLines'));
  assert.deepEqual(s.OrderCargoLineInputImplemented.properties.quantity.type,['number','null']);
  assert.equal(s.OrderRequestedStopInputImplemented.additionalProperties,false);
  assert.match(s.OrderRequestedStopInputImplemented.description,/associated/);
  assert.ok(!(api.paths[root+'/orders'].post.parameters??[]).some(p=>resolve(p).name==='Idempotency-Key'));
  for(const field of ['actorUserId','tripId','status','clientOperationId'])assert.equal(body.properties[field],undefined);
});
test('#59 PATCH extends only uncommitted request content and preserves strict version/reference contract',()=>{
  const operation=api.paths[root+'/orders/{orderId}'].patch,body=operation.requestBody.content['application/json'].schema;
  assert.equal(body.additionalProperties,false);assert.deepEqual(body.required,['expectedVersion']);
  assert.deepEqual(Object.keys(body.properties).sort(),['expectedVersion','poNumber','shipmentNumber','bolReference','cargoLines','requestedStops'].sort());
  assert.equal(body.properties.requestedStops.minItems,1);assert.equal(body.properties.cargoLines.type,'array');
  assert.match(operation.description,/OPEN.*no\s+Manifest/s);assert.match(operation.description,/STALE_VERSION/);assert.match(operation.description,/INVALID_STATE_TRANSITION/);
});
test('#59 execution projection is Order-authorized and closed without shared Orders or private Driver data',()=>{
  const p=api.paths[root+'/orders/{orderId}/progress'].get;
  assert.equal(p['x-implementation-status'],'implemented-issue-59');assert.match(p.description,/actual Order-to-Trip-to-Stop/);assert.match(p.description,/Repeatable-read/);
  assert.deepEqual(p.parameters.map(p=>p.name),['cursor','limit']);assert.equal(p.parameters[1].schema.maximum,50);
  for(const name of ['CustomerOrderProgress','CustomerOrderTrip','CustomerOrderProgressStop','CustomerOrderChange'])assert.equal(s[name].additionalProperties,false);
  for(const name of ['CustomerOrderTrip','CustomerOrderProgressStop','CustomerOrderChange'])for(const field of ['driverId','currentDriverId','currentTractorId','orderIds','customerIds','actorUserId','metadata'])assert.equal(s[name].properties[field],undefined);
  assert.equal(s.CustomerOrderTrip.properties.stops.maxItems,500);assert.equal(s.CustomerOrderTrip.properties.history.maxItems,200);
});
test('#59 effective hints do not introduce TRIP_VIEW_ALL into Customer Dispatcher roles',()=>{
  const p=api.paths[root+'/orders/customer-context'].get;assert.match(p.description,/ACTIVE Tenant\/membership/);assert.equal(p.parameters[0].required,true);
  assert.equal(s.CustomerOrderContext.additionalProperties,false);assert.ok(s.CustomerOrderContext.properties.capabilities.description.includes('never'));
  const roles=readFileSync(fileURLToPath(new URL('../../../backend/src/main/java/com/trailersaas/identity/internal/RoleCapabilities.java',import.meta.url)),'utf8');
  const customer=/RoleCode\.CUSTOMER_DISPATCHER, Set\.of\(([\s\S]*?)\)/.exec(roles)[1];
  assert.ok(customer.includes('ORDER_CREATE'));assert.ok(customer.includes('TRIP_REROUTE'));assert.ok(!customer.includes('TRIP_VIEW_ALL'));
});
test('#59 existing commands keep their actual version/expectation guards and safe Problem Details',()=>{
  assert.ok(api.paths[root+'/orders/{orderId}/completion'].post);
  const substitution=s.TrailerSubstitutionDecisionRequestImplemented;
  assert.equal(substitution.additionalProperties,false);assert.equal(substitution.properties.expectedVersion,undefined);
  assert.deepEqual(substitution.properties.decision.enum,['AUTHORIZE','REJECT']);
  for(const path of ['/orders/{orderId}/cancellations','/orders/{orderId}/completion','/trips/{tripId}/reroutes','/trips/{tripId}/trailer-substitution-decisions'])assert.equal(api.paths[root+path].post.responses.default.$ref,'#/components/responses/Problem');
  assert.equal(api.paths[root+'/customer-orders'],undefined);assert.equal(api.paths[root+'/customer-trips'],undefined);
});
test('#59 Site/Location choices have optional explicit Customer filters with real authorization',()=>{
  for(const path of ['/sites','/sites/{siteId}/locations']) {
    const p=api.paths[root+path].get.parameters.find(p=>p.name==='customerId');
    assert.equal(p.required,false);assert.equal(p.schema.format,'uuid');assert.match(p.description,/SITE_VIEW/);assert.match(p.description,/Tenant|tenant/);
  }
});
test('#59 specific human request decisions remain distinct from physical fulfillment and generic Audit',()=>{
  const changes=s.CustomerOrderProgress.properties.changes;assert.equal(changes.maxItems,20);assert.match(changes.description,/not a generic audit/);
  assert.equal(changes.items.additionalProperties,false);assert.deepEqual(changes.items.properties.changeType.enum,['ORDER_EDITED','ORDER_CANCELLED','ORDER_CANCELLATION_REQUESTED','ORDER_COMPLETED']);
  assert.equal(changes.items.properties.actorUserId,undefined);
  assert.match(s.OrderStatus.description,/DELIVERED.*COMPLETED/);
});
