import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { assertFull, runFull, fullJobs, fullCondition } from './ci-policy.mjs'

for (const action of ['opened', 'synchronize', 'reopened', 'ready_for_review']) {
  test(`${action}: Draft never runs full; Ready runs full`, () => {
    assert.equal(runFull('pull_request', { action, pull_request: { draft: true } }), false)
    assert.equal(runFull('pull_request', { action, pull_request: { draft: false } }), true)
  })
}
test('main retains full validation; malformed PR fails closed', () => {
  assert.equal(runFull('push', {}), true)
  assert.equal(runFull('pull_request', {}), false)
})
const success = Object.fromEntries(['policy', ...fullJobs].map(name => [name, { result: 'success' }]))
test('Draft is not merge approval even if individual skipped checks are considered green', () => {
  assert.throws(() => assertFull('pull_request', { pull_request: { draft: true } }, success))
})
for (const result of ['skipped', 'failure', 'cancelled', undefined]) {
  test(`full aggregate rejects ${result} results`, () => {
    for (const name of fullJobs) {
      assert.throws(() => assertFull('pull_request', { pull_request: { draft: false } }, { ...success, [name]: { result } }))
    }
  })
}
test('aggregate accepts only completed successes', () => assertFull('pull_request', { pull_request: { draft: false } }, success))
test('workflow preserves job names, conditions, test commands and current merge checkout', () => {
  const yaml = readFileSync(new URL('../workflows/ci.yml', import.meta.url), 'utf8').replaceAll('\r\n', '\n')
  for (const [id, name] of Object.entries({backend: 'Backend', mobile: 'Mobile', web: 'Web', openapi: 'OpenAPI'})) {
    assert.ok(yaml.includes(`  ${id}:\n    name: ${name}\n    if: ${fullCondition}`))
  }
  for (const action of ['opened', 'synchronize', 'reopened', 'ready_for_review', 'converted_to_draft']) assert.ok(yaml.includes(action))
  assert.ok(!yaml.includes('edited')) // PR-body evidence updates must not repeat the full suites.
  for (const command of ['clean verify', 'flutter pub get --enforce-lockfile', 'dart run build_runner build', 'flutter analyze --fatal-infos', 'flutter test --reporter expanded', 'npm run typecheck', 'npm run lint', 'npm run build', 'npm run validate', 'npm test']) assert.ok(yaml.includes(command))
  assert.ok(yaml.includes('cancel-in-progress: true'))
  assert.ok(yaml.includes('needs: [policy, backend, mobile, web, openapi]'))
  assert.ok(yaml.includes('    if: always()'))
  assert.ok(!yaml.includes('pull_request_target'))
  assert.ok(!yaml.includes('continue-on-error'))
  assert.ok(!yaml.includes('ref:')) // checkout uses GitHub PR merge ref, not stale artifacts/head-only
  assert.ok(!yaml.includes('Verify session widget regressions individually'))
  for (const file of ['mobile/test/app/trailer_saas_app_test.dart', 'mobile/test/session/session_view_test.dart']) {
    assert.ok(readFileSync(new URL(`../../${file}`, import.meta.url), 'utf8').includes('testWidgets('))
  }
})
