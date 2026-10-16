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

const root='/api/v1/tenants/{tenantId}/audit-events',s=api.components.schemas;
const resolve=v=>v.$ref?v.$ref.split('/').slice(1).reduce((n,k)=>n[k],api):v;
test('#61 uses exactly two read-only Audit routes with current authorization',()=>{
 for(const path of [root,root+'/{auditEventId}']){
  const p=api.paths[path];assert.equal(p.get['x-implementation-status'],'implemented-issue-61');
  for(const verb of ['post','put','patch','delete'])assert.equal(p[verb],undefined);
  assert.match(p.get.description,/AUDIT_VIEW/);assert.match(p.get.description,/ACTIVE Tenant/);
  for(const status of ['400','401','403'])assert.ok(p.get.responses[status]);
 }
 assert.ok(api.paths[root+'/{auditEventId}'].get.responses['404']);
 assert.match(api.paths[root].get.description,/Customer Dispatcher, Driver and Yard/);
});
test('#61 closed list filters and bounded database cursor keep User and Tenant context',()=>{
 const p=api.paths[root].get;assert.deepEqual(p.parameters.map(i=>i.name),['actorUserId','actionCode','entityType','entityId','from','to','limit','cursor']);
 const limit=p.parameters.find(i=>i.name==='limit').schema;assert.equal(limit.maximum,100);assert.equal(limit.minimum,1);assert.equal(limit.default,50);
 assert.match(p.description,/occurredAt DESC\/id DESC/);assert.match(p.description,/authenticated User, Tenant and all filters/);assert.match(p.description,/no duplicate/);
 assert.deepEqual(s.AuditEventPage.required,['items','nextCursor']);assert.equal(s.AuditEventPage.additionalProperties,false);assert.equal(s.AuditEventPage.properties.items.maxItems,100);
});
test('#61 exposes actual actor/provenance and historical membership without credentials',()=>{
 assert.deepEqual(s.AuditActor.properties.type.enum,['HUMAN_USER','SYSTEM','INTEGRATION']);
 assert.deepEqual(s.AuditEvent.properties.sourceType.enum,['USER_ACTION','MOBILE_AUTOMATION','SYSTEM_PROCESS','EXTERNAL_INTEGRATION']);
 assert.equal(s.AuditActor.additionalProperties,false);assert.deepEqual(s.AuditActor.properties.userId.type,['string','null']);assert.deepEqual(s.AuditActor.properties.membershipId.type,['string','null']);
 assert.equal(s.AuditEvent.additionalProperties,false);assert.equal(s.AuditEvent.properties.occurredAt.format,'date-time');assert.deepEqual(s.AuditEvent.properties.reason.type,['string','null']);
 for(const field of ['email','displayName','firebaseToken','tenantDirectory','actor_type'])assert.equal(s.AuditEvent.properties[field],undefined);
});
test('#61 typed action catalog does not duplicate physical timelines',()=>{
 assert.equal(s.AuditActionCode.enum.length,24);
 for(const a of ['TENANT_SETTINGS_UPDATED','CUSTOMER_CREATED','CUSTOMER_UPDATED','CUSTOMER_SITE_ASSOCIATED','DRIVER_USER_UNLINKED','TRAILER_QR_REVOKED','INVITATION_ACCEPTED','MEMBERSHIP_CREATED'])assert.ok(s.AuditActionCode.enum.includes(a));
 for(const a of ['YARD_MOVED','HOOKED','ARRIVED','REQUEST_LOGGED'])assert.equal(s.AuditActionCode.enum.includes(a),false);
 assert.equal(s.AuditEntityType.enum.length,10);
});
test('#61 snapshots are closed minimized business projections',()=>{
 const snapshots=s.AuditSnapshot.anyOf.filter(i=>i.$ref).map(resolve);assert.equal(snapshots.length,11);
 for(const p of snapshots){assert.equal(p.additionalProperties,false);for(const key of ['token','password','firebaseToken','fcmToken','publicToken','acceptanceToken','tokenHash','signedUrl','email','displayName','licenseNumber','medicalCertificateExpires','capabilityNotes'])assert.equal(p.properties[key],undefined);}
 assert.deepEqual(s.AuditDriverSnapshot.properties.redactedChanges.items.enum,['licenseNumber','medicalCertificateExpires']);
 assert.deepEqual(s.AuditTrailerSnapshot.properties.redactedChanges.items.enum,['capabilityNotes']);
 assert.match(s.AuditSnapshot.description,/64 KiB/);assert.equal(s.AuditRole.additionalProperties,false);assert.equal(s.AuditBusinessAddress.additionalProperties,false);
});
