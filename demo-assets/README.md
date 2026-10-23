# TrailerSaaS commercial demonstration package

**Owner review copy • US English • Issue #125 / UX-08 • Not a production release**

TrailerSaaS supports trailer visibility and coordinated work across dispatch,
drivers, yards and customers. This package shows implemented interfaces with
synthetic data. It does not claim a live carrier deployment, measured savings,
production readiness or a completed shipment.

## Start here

- [Commercial presentation](ux08/presentation/trailersaas-commercial-demo.pptx) — editable slides with actual screenshots and presenter notes.
- [Asset index](asset-index.md) — every screenshot's route/widget, role, dimensions and authenticity.
- [Captions](notes/captions.md) — approved factual benefit and qualification for every asset.
- [13-minute walkthrough](notes/suggested-client-walkthrough.md) — navigation, narration and safe stopping points.
- [Capability and claims review](notes/capabilities-and-claims.md) — implemented versus demonstrated versus unvalidated.
- [Local runbook](notes/local-demo-runbook.md) — no production services or credentials required.
- [Limitations and owner checklist](notes/limitations.md).
- [Validation and acceptance](notes/validation-and-acceptance.md).

## Exact application source

All new captures run the application source from current `main` at
`fa0898e12cbdc358ea7cd7119c76a1e3a11e248c` (owner-merged PR #133).
Web tree: `4a729ce9d63d99fb9386b84e279264df1cc089b2`.
Mobile tree: `f7fb8cdf2d89a4e961bda8d822f3785526a6b320`.
This PR adds assets, documentation and asset tooling only. Application trees
must remain identical; [the verifier](tools/verify-package.py) checks this bridge
from capture commit to the final PR source. Capture provenance records the actual
capture source, never a guessed future commit. Final PR HEAD belongs in the PR
description and delivery record, not retroactively in image metadata.

## Authenticity and preservation

`ux08/web/` contains unretouched browser PNGs of actual React route components in
the existing DEV-only isolated entries. `ux08/mobile/` contains unretouched Android
emulator screenshots of actual Flutter widgets from the existing explicit debug
review entry. Both use synthetic reads and reject writes. Their visible disclosure
bars remain in the source screenshots. These are **ISOLATED DEMO**, not real
Firebase authentication or backend-confirmed operations. Customer “Access verified”
and accepted-document metadata in these fixtures are synthetic UI states.

Names, identifiers, addresses, dates and counts are illustrative. The three Web
fixtures and Mobile fixture are independent datasets, not a continuous shipment
simulation. There are no real customers, private documents or download URLs in
this package. No generated product imagery or staged success overlays are used.

Existing local `demo-assets/` in the original checkout is untouched (112 files
inventoried externally before this work). New evidence uses the additive `ux08/`
namespace in this separate worktree. Capture scripts refuse existing output
directories. Old UX evidence remains unchanged. No APKs, databases, credentials,
node_modules or SDK distributions are committed.

Nothing is sent to a prospect or deployed by this PR. The owner reviews the
materials and chooses any subsequent sharing or commercial terms.
