import { readFileSync } from 'node:fs'
import { assertFull } from './ci-policy.mjs'
// The required aggregate check fails on Draft deliberately: skipped individual
// jobs are accepted by GitHub protection and must never masquerade as full green.
const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'))
assertFull(process.env.GITHUB_EVENT_NAME, event, JSON.parse(process.env.CI_RESULTS))
console.log(`Full CI succeeded for ${process.env.GITHUB_SHA}`)
