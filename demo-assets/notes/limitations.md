# Limitations and owner review

## Demonstrated versus unvalidated

- **ISOLATED DEMO:** all interior Web and Android evidence uses synthetic reads,
  existing test identities and rejected HTTP writes. No real Firebase session,
  tenant membership, customer assignment, dispatch acceptance or physical action
  is demonstrated. A fixture's “Access verified” or native “Connected — server
  validated” is only its rendered synthetic state, not real identity/server proof.
- **NOT VERIFIED:** real Firebase/Backend/PostgreSQL integration, R2 protected
  downloads, actual uploaded/accepted POD, FCM delivery, offline replay against a
  live server, field geofence accuracy and camera capture/lifecycle on physical
  devices. No production credentials were requested or used.
- **Android emulator only:** AOSP API 35, x86_64, software GPU. This is native
  Flutter execution, not a widget-test rendering, physical-device certification
  or iOS verification. Evidence does not prove all device sizes or OS versions.
- **Independent fixtures:** dates are fixed sample dates, counts are small
  previews, and IDs differ across roles. These are not one shipment or measured
  fleet performance. The Alert is a synthetic read, not an automatically triggered
  production detector. Administrative Audit is not the movement ledger.
- **NOT VERIFIED — BILLING BLOCKED:** hosted GitHub Actions remains unavailable
  under the owner's stated account restriction. No workflow, CI policy or branch
  protection is disabled or bypassed. A local pass is not remote CI green.
- Production readiness remains outside UX-08, including the deployment/security/
  native release gates governed by #63–65. No hosted demo, release signing,
  production migration, customer deployment or service commitment is delivered.

## Existing development dependency advisory

The unchanged Web lockfile contains Vite 8.3.2. The existing UX-07 review recorded
a High development-server advisory, with 8.3.3 identified as patched at that time.
UX-08 does not change dependencies or claim the advisory is resolved. Run the
isolated server on loopback only. Current advisory audit output, if executed,
is recorded with this package's validation results; it is not a production
security certification. Track dependency remediation separately from demo assets.

The fresh local npm audit reports **1 affected dependency, High aggregate**,
covering six advisories, including the Windows development-editor issue
[GHSA-587h-7wmj-h5pm](https://github.com/advisories/GHSA-587h-7wmj-h5pm).
This advisory remains unresolved; no automatic audit fix or lockfile update ran.

## Capture coverage limits

The six Android frames cover Road Home, assigned Trip/Hook entry, empty sync,
Yard controls, explicit movement review and the actual camera-unavailable QR
state. Successful optical QR recognition, physical Hook/Drop, inspection/POD
capture, media upload, Yard Check completion, populated offline/conflict queues
and live replay are not demonstrated in this package. Their implemented code is
identified in the claims inventory; this is not an end-to-end test of those flows.
The Yard scanner used for movement review is explicitly synthetic.

## Owner review before sharing

- Review the presentation, captions and actual PNGs; keep isolation disclosures.
- Confirm commercial wording and which implemented workflows are relevant to the
  prospect; no customer references, measured ROI, promised deployment or pricing.
- Confirm the intended recipients and sharing method independently of this PR.
- Decide whether a later controlled infrastructure evaluation is warranted based
  on actual interest. It is not required to run this isolated package.
- Keep the PR Draft until owner review. No automatic merge or prospect delivery.
