import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
const require=createRequire(import.meta.url), directory=mkdtempSync(join(tmpdir(),'trailersaas-alerts-contract-'));
let api;
try {
 const cli=join(dirname(require.resolve('@redocly/cli/package.json')),'bin/cli.js'), output=join(directory,'api.json');
 const result=spawnSync(process.execPath,[cli,'bundle',fileURLToPath(new URL('../../../docs/api/openapi-v1-draft.yaml',import.meta.url)),'--config',fileURLToPath(new URL('../redocly.yaml',import.meta.url)),'--output',output],{encoding:'utf8',timeout:30000,env:{...process.env,REDOCLY_TELEMETRY:'off'}});
 assert.ifError(result.error);assert.equal(result.status,0,result.stdout+result.stderr);api=JSON.parse(readFileSync(output,'utf8'));
} finally {rmSync(directory,{recursive:true,force:true});}
const root='/api/v1/tenants/{tenantId}/alerts', schemas=api.components.schemas;
test('#56 exposes only authorized inbox/lifecycle and global device routes',()=>{
 const paths=Object.entries(api.paths).filter(([,p])=>Object.values(p).some(op=>op?.['x-implementation-status']==='implemented-issue-56')).map(([p])=>p).sort();
 assert.deepEqual(paths,[root,`${root}/{alertId}`,`${root}/{alertId}/acknowledgement`,`${root}/{alertId}/resolution`,'/api/v1/devices','/api/v1/devices/{deviceId}'].sort());
 assert.equal(api.paths[root].post,undefined);assert.equal(api.paths['/api/v1/tenants/{tenantId}/devices'],undefined);
 assert.deepEqual(api.security,[{firebaseBearer:[]}]);
});
test('#56 inbox is bounded and preserves fresh resource/Customer authorization',()=>{
 const op=api.paths[root].get, q=Object.fromEntries(op.parameters.map(p=>[p.name,p.schema]));
 for(const text of ['ACTIVE membership','ACTIVE Tenant','ALERT_VIEW','Trailer/Customer','never authority'])assert.ok(op.description.includes(text));
 assert.deepEqual(Object.keys(q),['status','alertType','severity','relatedEntityType','from','to','cursor','limit']);
 assert.deepEqual(q.status.enum,['OPEN','ACKNOWLEDGED','RESOLVED']);assert.equal(q.limit.maximum,100);assert.equal(q.limit.minimum,1);
 assert.equal(schemas.AlertInboxPage.properties.items.maxItems,100);assert.equal(schemas.AlertInboxPage.additionalProperties,false);
});
test('#56 commands are strict, versioned and do not accept actors or physical corrections',()=>{
 for(const [path,name,fields,cap] of [['acknowledgement','AlertAcknowledgementRequest',['expectedVersion'],'ALERT_ACKNOWLEDGE'],['resolution','AlertResolutionRequest',['expectedVersion','reason'],'ALERT_RESOLVE']]){
  const op=api.paths[`${root}/{alertId}/${path}`].post,s=schemas[name];assert.equal(s.additionalProperties,false);assert.deepEqual(s.required,fields);assert.deepEqual(Object.keys(s.properties),fields);
  assert.equal(s.properties.expectedVersion.minimum,0);for(const text of [cap,'ALERT_VIEW','deny-by-default','STALE_VERSION'])assert.ok(op.description.includes(text));
 }
 assert.equal(schemas.AlertResolutionRequest.properties.reason.maxLength,4000);assert.equal(schemas.AlertResolutionRequest.properties.reason.pattern,'\\S');
 assert.ok(api.paths[`${root}/{alertId}/resolution`].post.description.includes('Never repairs Trailer location'));
});
test('#56 device owner is global immutable identity; token is optional and never returned',()=>{
 const input=schemas.UserDeviceRegistrationRequest,result=schemas.UserDeviceRegistration;
 assert.equal(input.additionalProperties,false);assert.deepEqual(input.required,['platform']);assert.deepEqual(input.properties.platform.enum,['ANDROID','IOS']);
 assert.deepEqual(input.properties.fcmToken.type,['string','null']);assert.equal(input.properties.fcmToken.maxLength,4096);
 assert.equal(result.additionalProperties,false);assert.equal(result.properties.fcmToken,undefined);assert.equal(result.properties.tenantId,undefined);assert.equal(input.properties.userId,undefined);
 assert.equal(api.paths['/api/v1/devices/{deviceId}'].delete.responses['204'].description,'Registration deactivated, history retained');
});
test('#56 detail minimizes original context and provider acceptance never claims delivered',()=>{
 const s=schemas.AlertOccurrence;assert.equal(s.additionalProperties,false);assert.deepEqual(s.properties.status.enum,['OPEN','ACKNOWLEDGED','RESOLVED']);assert.equal(s.properties.version.minimum,0);
 assert.equal(s.properties.context.additionalProperties,false);assert.deepEqual(Object.keys(s.properties.context.properties),['yardCheckId','itemId','movementEventId']);
 assert.equal(s.properties.relatedResource.additionalProperties,false);assert.ok(s.properties.pushSummary.propertyNames.enum.includes('DEAD'));
 assert.ok(s.properties.pushSummary.description.includes('not device delivery'));assert.equal(s.properties.message_data,undefined);
});
