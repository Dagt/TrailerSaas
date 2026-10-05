import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const require = createRequire(import.meta.url);
const cli = join(dirname(require.resolve('@redocly/cli/package.json')), 'bin/cli.js');
const configuration = fileURLToPath(new URL('../redocly.yaml', import.meta.url));
const contract = fileURLToPath(
  new URL('../../../docs/api/openapi-v1-draft.yaml', import.meta.url),
);

function validate(path) {
  const result = spawnSync(
    process.execPath,
    [cli, 'lint', path, '--config', configuration],
    {
      encoding: 'utf8',
      timeout: 30_000,
      env: { ...process.env, REDOCLY_TELEMETRY: 'off' },
    },
  );
  assert.ifError(result.error);
  assert.equal(result.signal, null);
  return result;
}

test('accepts the current OpenAPI 3.1 contract', () => {
  const result = validate(contract);
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

const invalidDocuments = [
  ['malformed YAML', 'openapi: [3.1.0\n', /parse|end of|flow collection/i],
  [
    'structurally invalid OpenAPI (missing info.version)',
    'openapi: 3.1.0\ninfo:\n  title: Invalid\npaths: {}\n',
    /version/,
  ],
  [
    'structurally invalid nested response',
    'openapi: 3.1.0\ninfo: { title: Invalid, version: 1.0.0 }\npaths:\n  /test:\n    get:\n      responses:\n        "200": {}\n',
    /description/,
  ],
  [
    'unresolved schema reference',
    'openapi: 3.1.0\ninfo: { title: Invalid, version: 1.0.0 }\npaths:\n  /test:\n    get:\n      responses:\n        "200":\n          description: OK\n          content:\n            application/json:\n              schema:\n                $ref: "#/components/schemas/Missing"\n',
    /no-unresolved-refs/,
  ],
];

for (const [name, contents, diagnostic] of invalidDocuments) {
  test('rejects ' + name + ' with a nonzero exit code', (context) => {
    const directory = mkdtempSync(join(tmpdir(), 'trailersaas-openapi-'));
    context.after(() => rmSync(directory, { recursive: true, force: true }));
    const path = join(directory, 'invalid.yaml');
    writeFileSync(path, contents);
    const result = validate(path);
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout + result.stderr, diagnostic);
  });
}
