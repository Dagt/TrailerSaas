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
test('#57 extends the existing inbox filter without a second API',()=>{
 assert.deepEqual(api.paths[root].get.parameters.find(p=>p.name==='alertType').schema.enum,['TRAILER_MISSING','STALE_TRAILER']);
 assert.equal(api.paths[root].post,undefined);assert.equal(api.paths['/api/v1/tenants/{tenantId}/stale-alerts'],undefined);assert.ok(api.paths[root].get.description.includes('STALE_TRAILER'));
});
test('#57 exposes closed immutable physical detection evidence',()=>{
 const s=schemas.AlertOccurrence,d=s.properties.detection;assert.deepEqual(s.properties.alertType.enum,['TRAILER_MISSING','STALE_TRAILER']);
 assert.equal(s.additionalProperties,false);assert.equal(d.additionalProperties,false);assert.deepEqual(d.type,['object','null']);assert.deepEqual(d.required,['detectedAt','lastSeenAt','thresholdHours','elapsedHours','jobId']);
 assert.equal(d.properties.thresholdHours.minimum,1);assert.equal(d.properties.thresholdHours.maximum,2147483647);assert.equal(d.properties.lastSeenAt.format,'date-time');assert.equal(d.properties.jobId.format,'uuid');assert.ok(d.description.includes('not from browser clock'));
});
test('#57 system recovery does not fabricate a human or mobile operation',()=>{
 const s=schemas.AlertOccurrence,r=s.properties.resolutionEvidence;assert.deepEqual(s.properties.originActorType.enum,['HUMAN_USER','SYSTEM']);assert.ok(s.properties.resolutionActorType.enum.includes('SYSTEM'));assert.ok(s.properties.resolvedBy.type.includes('null'));
 assert.equal(r.additionalProperties,false);assert.deepEqual(r.required,['lastSeenAt','thresholdHours','jobId']);for(const key of ['originActorType','resolutionActorType','detection','resolutionEvidence'])assert.ok(s.required.includes(key));assert.equal(s.properties.clientOperationId,undefined);
 for(const n of ['AlertResolutionRequest','AlertAcknowledgementRequest']){assert.equal(schemas[n].properties.actorType,undefined);assert.equal(schemas[n].properties.resolutionActorType,undefined);assert.equal(schemas[n].additionalProperties,false);}
});
test('#57 keeps Missing recovery distinct and human writes deny by default',()=>{
 const op=api.paths[`${root}/{alertId}/resolution`].post;for(const text of ['AT_LOCATION','STALE_TRAILER','ALERT_RESOLVE','deny-by-default','SYSTEM'])assert.ok(op.description.includes(text),text);
 assert.deepEqual(schemas.AlertOccurrence.properties.severity,{type:'string',const:'WARNING'});assert.equal(api.paths['/api/v1/tenants/{tenantId}/alerts/recipients'],undefined);
});
