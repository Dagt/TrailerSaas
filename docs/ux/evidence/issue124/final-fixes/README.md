# UX-07 final corrections — M1 and L1

Actual source: `cfd077036bcdde68245b2a512925ea066ad35a36`. Complete Web tree: `4a729ce9d63d99fb9386b84e279264df1cc089b2`. The final evidence/documentation commit retains that entire Web tree. Final HEAD and actual complete local results are in [PR133](https://github.com/Dagt/TrailerSaas/pull/133); these captures keep their actual source SHA.

[Provenance, checks and SHA256](provenance.json) · [Route/role/state matrix](route-role-state-matrix.csv).

These are unchanged screenshots of real React routes in Microsoft Edge, with the existing explicit **ISOLATED DEMO** entries and synthetic read fixtures. No Firebase/Backend session or real Customer data. Writes are rejected; no real cancellation or protected download was performed. No generated/edited pixels. Native images in earlier evidence remain separate and their complete Mobile tree is unchanged.

| Scenario | Before | Corrected source |
|---|---|---|
| Cancel Trip390×844 | [Actual clipped heading](before-cancel-trip-390x844.png), cba7e1d | [Visible heading and safe Cancel focus](cancel-trip-390x844.png) |
| Invalid cancellation390×844 | Existing rules/messages retained | [Long validation wraps inside modal](cancel-trip-validation-390x844.png) |
| Modal720×450 reflow | Equivalent reflow, not actual browser200% zoom | [Heading stays visible](cancel-trip-720x450.png) |
| Customer Dashboard1440×900 | [Technical labels](../refinement/after/customer-dashboard-1440x900.png) | [Friendly labels; same bounded previews](customer-1440x900.png) |
| Customer Orders390×844 | Original filter enums | [Partially delivered, value PARTIALLY_DELIVERED](customer-orders-filtered-390x844.png) |
| Customer Trailers390×844 | Original state dimensions | [Location labels and controlled table scrolling](customer-trailers-390x844.png) |
| Customer Deliveries768×1024 | Original delivery scope | [Readable delivery states](customer-deliveries-768x1024.png) |
| Customer Documents1440×900 | Original accepted-file associations | [Uploaded remains separate from accepted evidence](customer-documents-1440x900.png) |

26 current images are indexed;8 after images plus1 actual before image are selected here. The remaining originals are delivered externally with per-file hashes. The committed optional browser regression is `web/scripts/issue124-browser-regression.cjs`; it uses an external installed Playwright runtime, introducing no production dependency. Run a local Vite server on127.0.0.1:5340, set `PLAYWRIGHT_MODULE` and optionally `BROWSER_EXECUTABLE`, choose an external `UX124_OUTPUT`, then run `node web/scripts/issue124-browser-regression.cjs` from the repository root. It requires a clean committed capture source.

Fresh browser results:22PASS focused checks,26 screenshots,15 accessible-name snapshots,0 page/console errors. M1 covers320×844,390×844,768×1024,1280×800,1440×900,1920×1080 and720×450: initial safe focus, visible heading, backward keyboard traversal of every form control without occlusion, mandatory confirmation, actual required-field feedback, long-error wrapping and Escape/Cancel focus restoration. L1 covers Dashboard/Orders/Trailers/Deliveries/Documents at390/768/1440 with readable labels and original filter values. Broader replay adds46PASS checks and12OBSERVED dialog observations,50 additional images,12 AX snapshots and0page errors.16 actual token contrast pairs pass the applicable4.5:1 text/3:1 nontext threshold. These are scoped automated/browser checks, not blanket WCAG certification.

The first capture helper tried to Tab from the last modal control into native browser chrome, then was corrected to exercise the actual form controls backwards from Cancel. A controlled filter needed an explicit wait for React/router completion; the enum assertion was retained. Visual inspection also caught existing long validation copy extending outside its field and content painting into the top inset at narrow reflow; only scoped CSS was corrected. Earlier exploratory outputs remain external, excluded from this final-source record.

Limits: actual browser-chrome200% zoom, manual TalkBack/VoiceOver, real Firebase/Backend/R2 grants, physical camera/QR/printer and iOS/device certification were not executed. Existing Flutter semantics/reflow/operational tests are distinct from those checks. Complete final local suites must be reported against the final revision; capture metadata does not predeclare future validation. Remote CI is **NOT VERIFIED — BILLING BLOCKED** by owner instruction; no Billing, CI policy or protection change is part of this work.
