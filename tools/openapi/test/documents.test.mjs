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
const directory = mkdtempSync(join(tmpdir(), 'trailersaas-documents-contract-'));
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

test('#52 implements exactly the authorized transport endpoints, not evidence completion', () => {
  const base = '/api/v1/tenants/{tenantId}/documents';
  const paths = api.paths;
  for (const [path, method] of [[`${base}/uploads`, 'post'], [`${base}/{documentId}`, 'get'],
    [`${base}/{documentId}/upload-completion`, 'post'], [`${base}/{documentId}/links`, 'post'],
    [`${base}/{documentId}/download-url`, 'post']]) {
    assert.equal(paths[path][method]['x-implementation-status'], 'implemented-issue-52');
    assert.deepEqual(paths[path][method].responses.default, { $ref: '#/components/responses/Problem' });
  }
  assert.match(paths[`${base}/uploads`].post.description, /POD_UPLOAD/);
  assert.match(paths[`${base}/{documentId}/links`].post.description, /assigned.*Trip/s);
  assert.match(paths[`${base}/{documentId}/download-url`].post.description, /all linked work/);
  assert.equal(paths[`${base}/{documentId}`].patch, undefined);
  assert.equal(paths[`${base}/{documentId}`].delete, undefined);
});

test('#52 strict upload/finalization/link shapes retain closed catalogs and UUID idempotency', () => {
  const schemas = api.components.schemas;
  const init = schemas.InitializeUploadRequestImplemented;
  assert.equal(init.additionalProperties, false);
  assert.deepEqual(Object.keys(init.properties), ['documentType', 'contentType', 'sizeBytes', 'checksumSha256']);
  assert.deepEqual(init.required, ['documentType', 'contentType', 'sizeBytes']);
  assert.equal(init.properties.documentType.enum.length, 9);
  assert.equal(init.properties.sizeBytes.minimum, 1);
  assert.equal(schemas.CompleteUploadRequestImplemented.additionalProperties, false);
  assert.deepEqual(Object.keys(schemas.CompleteUploadRequestImplemented.properties), ['checksumSha256']);
  assert.deepEqual(schemas.DocumentLinkRequestImplemented.properties.entityType.enum, ['TRIP_STOP']);
  const completion = api.paths['/api/v1/tenants/{tenantId}/documents/{documentId}/upload-completion'];
  assert(completion.parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'));
  assert.equal(api.components.parameters.IdempotencyKey.required, true);
  assert.equal(api.components.parameters.IdempotencyKey.schema.format, 'uuid');
  assert.match(completion.post.description, /UPLOAD_NOT_COMPLETE/);
  assert.match(completion.post.description, /IDEMPOTENCY_KEY_REUSED/);
});

test('#52 metadata excludes storage secrets and downloads acknowledge temporary bearer semantics', () => {
  const metadata = api.components.schemas.DocumentMetadataImplemented;
  assert.equal(metadata.additionalProperties, false);
  assert.deepEqual(Object.keys(metadata.properties), ['id', 'documentType', 'contentType', 'sizeBytes',
    'checksumSha256', 'uploadStatus', 'createdAt', 'uploadedAt', 'links']);
  assert.equal(metadata.properties.links.maxItems, 50);
  assert.deepEqual(api.components.schemas.InitializeUploadResponseImplemented.properties.uploadStatus.enum, ['PENDING']);
  const completion = api.paths['/api/v1/tenants/{tenantId}/documents/{documentId}/upload-completion'].post;
  assert.match(completion.description, /old PUT cannot modify published bytes/);
  const download = api.paths['/api/v1/tenants/{tenantId}/documents/{documentId}/download-url'].post;
  assert.match(download.description, /temporary bearer grant/);
  assert.match(download.description, /uploader identity alone is insufficient/);
});
