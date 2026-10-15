export const fullJobs = ['backend', 'mobile', 'web', 'openapi']
export const fullCondition = "github.event_name != 'pull_request' || !github.event.pull_request.draft"
export function runFull(eventName, event) {
  return eventName !== 'pull_request' || event.pull_request?.draft === false
}
export function assertFull(eventName, event, results) {
  if (!runFull(eventName, event)) throw new Error('Full validation deferred: PR is Draft; this is not a test failure or merge approval.')
  for (const name of ['policy', ...fullJobs]) {
    if (results[name]?.result !== 'success') throw new Error(`Required validation did not succeed: ${name}`)
  }
}
