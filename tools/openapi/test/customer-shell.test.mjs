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
test('#58 adds Tenant status as a navigation hint without changing role or capability authority',()=>{
 const m=schemas.CurrentUserContext.properties.memberships.items;
 assert.equal(m.properties.tenantActive.type,'boolean');assert.ok(m.properties.tenantActive.description.includes('navigation hint'));
 assert.ok(!m.required.includes('tenantActive'));assert.ok(m.properties.capabilities.description.includes('Tenant-scope'));
 assert.deepEqual(m.properties.roles.items.required,['roleCode','scopeType','customerId']);
});
test('#58 reuses scoped Customer reads without a parallel Customer portal API',()=>{
 const root='/api/v1/tenants/{tenantId}/customers';
 for(const p of [root,`${root}/{customerId}`])assert.ok(api.paths[p].get.description.includes('ACTIVE Tenant'));
 assert.ok(api.paths[`${root}/{customerId}`].get.description.includes('CUSTOMER_SCOPE_DENIED'));
 assert.equal(api.paths['/api/v1/customer-portal'],undefined);assert.equal(api.paths['/api/v1/customer-login'],undefined);
 assert.deepEqual(api.security,[{firebaseBearer:[]}]);
});
test('#58 Customer response retains server identity/status and optimistic version; no browser scope grant',()=>{
 const s=schemas.Customer;for(const field of ['id','name','status','version','createdAt','updatedAt'])assert.ok(s.properties[field]);
 assert.equal(s.properties.id.format,'uuid');assert.deepEqual(s.properties.status.enum,['ACTIVE','INACTIVE']);
 assert.equal(s.properties.capabilities,undefined);assert.equal(s.properties.firebaseToken,undefined);
});
