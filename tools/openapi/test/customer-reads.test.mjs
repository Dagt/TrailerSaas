import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
const require=createRequire(import.meta.url),dir=mkdtempSync(join(tmpdir(),'trailersaas-customer-reads-'));
let api;
try {
  const cli=join(dirname(require.resolve('@redocly/cli/package.json')),'bin/cli.js'),output=join(dir,'api.json');
  const result=spawnSync(process.execPath,[cli,'bundle',fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml',import.meta.url)),'--config',fileURLToPath(new URL('../redocly.yaml',import.meta.url)),'--output',output],{encoding:'utf8',timeout:30000,env:{...process.env,REDOCLY_TELEMETRY:'off'}});
  assert.ifError(result.error);assert.equal(result.status,0,result.stdout+result.stderr);api=JSON.parse(readFileSync(output,'utf8'));
} finally {rmSync(dir,{recursive:true,force:true});}
const root='/api/v1/tenants/{tenantId}',customer=root+'/customers/{customerId}',s=api.components.schemas;
const resolve=v=>v.$ref?v.$ref.split('/').slice(1).reduce((n,k)=>n[k],api):v;
test('#60 Trailer keysets are opt-in and retain strict real filters without broadening scope',()=>{
  const p=api.paths[root+'/trailers'].get,params=p.parameters.map(resolve);
  assert.equal(params.find(p=>p.name==='limit').schema.maximum,100);
  assert.deepEqual(params.map(p=>p.name).sort(),['limit','cursor','operationalNumber','designatedCustomerId','locationState','currentLocationId','loadState','serviceabilityState','active','coupled'].sort());
  assert.match(p.description,/Legacy.*unpaged/);assert.match(p.description,/ACTIVE Tenant/);assert.match(p.description,/Portal always pages/);
  assert.deepEqual(s.TrailerList.required,['items','nextCursor']);assert.equal(s.TrailerList.additionalProperties,false);assert.deepEqual(s.TrailerList.properties.nextCursor.type,['string','null']);
});
test('#60 explicit Customer narrows additive Trailer and document authority on existing endpoints',()=>{
  for(const [path,method] of [['/trailers/{trailerId}','get'],['/trailers/{trailerId}/timeline','get'],['/documents/{documentId}','get'],['/documents/{documentId}/download-url','post']]){
    const p=api.paths[root+path][method],param=p.parameters.map(resolve).find(p=>p.name==='customerId');
    assert.equal(param.required,false);assert.equal(param.schema.format,'uuid');assert.match(param.description,/freshly authorized/);assert.equal(p['x-customer-read-extension'],'implemented-issue-60');
  }
  assert.match(api.paths[root+'/trailers/{trailerId}/timeline'].get.description,/current designation period/);
  assert.match(api.paths[root+'/trailers/{trailerId}'].get.description,/Driver\/Tractor/);
});
test('#60 delivery discovery is bounded composite Order/Stop data with no broad Trip directory',()=>{
  const p=api.paths[customer+'/deliveries'].get;
  assert.equal(p['x-implementation-status'],'implemented-issue-60');assert.deepEqual(p.parameters.map(p=>p.name),['limit','cursor','status','active','orderId']);assert.equal(p.parameters[0].schema.maximum,50);
  assert.match(p.description,/actual Order\/Trip/);assert.match(p.description,/one bulk evidence query/);assert.match(p.description,/Shared Stop/);
  const detail=api.paths[customer+'/deliveries/{orderId}/{stopId}'];assert.equal(detail.get.operationId,'getCustomerDelivery');assert.equal(detail.post,undefined);
  assert.equal(s.CustomerDelivery.additionalProperties,false);assert.equal(s.CustomerDelivery.properties.documents.maxItems,2);
  for(const field of ['driverId','tractorId','metadata','reason','orderIds','actorUserId'])assert.equal(s.CustomerDelivery.properties[field],undefined);
  assert.ok(s.CustomerDelivery.required.includes('orderStatus'));assert.ok(s.CustomerDelivery.required.includes('status'));
});
test('#60 document index distinguishes publication, exact acceptance and legacy ambiguous receipts',()=>{
  const p=api.paths[customer+'/documents'].get;
  assert.deepEqual(p.parameters.map(p=>p.name),['limit','cursor','documentType','orderId','stopId','documentId']);assert.equal(p.parameters[0].schema.maximum,50);
  assert.match(p.description,/exact accepted/);assert.match(p.description,/All linked resources/);assert.match(p.description,/only this selected Customer/);assert.match(p.description,/ambiguous historical receipt/);
  const d=s.CustomerDeliveryDocument;assert.equal(d.additionalProperties,false);assert.equal(d.properties.uploadStatus.const,'UPLOADED');assert.deepEqual(d.properties.documentType.enum,['BOL','POD','DELIVERY_PHOTO']);
  for(const field of ['downloadUrl','objectKey','uploaderId','metadata'])assert.equal(d.properties[field],undefined);
});
test('#60 pages and file associations are closed and bounded without fabricated totals',()=>{
  for(const name of ['CustomerDeliveryPage','CustomerDeliveryDocumentPage']){
    assert.equal(s[name].additionalProperties,false);assert.deepEqual(s[name].required,['customerId','items','nextCursor']);assert.equal(s[name].properties.items.maxItems,50);assert.equal(s[name].properties.total,undefined);
  }
  assert.match(s.CustomerDelivery.properties.fileAssociationRecorded.description,/not a guarantee/);
  assert.deepEqual(s.CustomerDelivery.properties.acceptedAt.type,['string','null']);
});
test('#60 download remains explicit existing private transport with safe storage-unavailable handling',()=>{
  const p=api.paths[root+'/documents/{documentId}/download-url'].post;
  assert.match(p.description,/503 OBJECT_STORAGE_UNAVAILABLE/);assert.match(p.description,/maximum 300/);assert.match(p.description,/already-issued URL/);
  assert.equal(p.requestBody.content['application/json'].schema.additionalProperties,false);assert.deepEqual(p.requestBody.content['application/json'].schema.properties,{});
  for(const path of ['/customer-documents','/customer-trips','/documents/{documentId}/public-url'])assert.equal(api.paths[root+path],undefined);
  for(const path of [customer+'/deliveries',customer+'/documents',customer+'/deliveries/{orderId}/{stopId}'])assert.deepEqual(api.paths[path].get.responses.default,{$ref:'#/components/responses/Problem'});
});
