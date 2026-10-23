# TrailerSaaS — 13-minute commercial walkthrough

Audience: US carrier owner, operations manager, dispatch lead and yard lead.
Use the [runbook](local-demo-runbook.md), [index](../asset-index.md) and slides.
Open Admin, Operations, Customer and the Android review menu before the meeting.
Keep screenshots available if local SDK/browser execution is unavailable.
No passwords, real driver records or production data should be shown.

| Time | Screen / navigation | Suggested narration and safe action |
|---|---|---|
| 0:00–1:00 | Slide 1–2 / framing | “Where does your team lose time reconciling Trailer location, dispatch instructions and Customer requests? TrailerSaaS brings those recorded facts into connected, role-specific workflows. Today these are the actual interfaces with synthetic data, not a live deployment.” State explicitly that writes are rejected and the role demos use independent datasets. |
| 1:00–2:30 | Operations → Trailers → `DEMO-TR-104`; use direct Trailer detail URL if desired | “This Trailer's current location is unknown. The last recorded Dock and time are retained so the team has a starting point, without claiming the Trailer is still there.” Point to separate load, serviceability and physical coupling. Scroll to movement history. Explain that a planned assignment is not a physical Hook; there is no continuous GPS or ETA. |
| 2:30–4:00 | Operations Dashboard → Orders | Review pending work, active Trips and attention; say “bounded previews” rather than fleet totals. Use Customer/Driver/Trip filters as appropriate. Open the Orders queue, select eligible `DEMO-ORD-104` and choose planning. Do not select an ineligible completed Order. Explain PO/BOL references and why the planning queue supports fewer repeated lookups. |
| 4:00–5:15 | Plan a Trip | Select the first eligible Order. Continue to **Stops**, review the existing destinations/order controls, then **Resources**, then **Review** if inputs allow. Do not invent resources or submit creation. “These four steps prepare the plan; they do not prove physical execution.” If fixture data does not permit continuation, stop at the supported step and show the captured review/selection asset; do not claim creation. |
| 5:15–6:15 | Trips → first in-transit Trip → Operational timeline | Show expected Driver/Tractor/Trailer, executable Stops and linked Orders. Scroll to history and **Load more Trip events**. Read the recorded reason/actor/time. “Assignments and instructions remain inspectable. The physical Stop projection and administrative Audit are separate.” Do not trigger Cancel, reroute or assignment submissions. |
| 6:15–7:30 | Android review menu → Assigned Trip native QR; Back → Road and Yard workspace | Show actual Road task/Stop controls and expected equipment. “A scan identifies a Trailer; permission and physical confirmation still determine whether an action is allowed.” Show review controls without confirming Hook/Drop, arrival or delivery. Scroll to updates/sync. An empty synthetic queue is not a synchronization demonstration. Explain local queued versus confirmed, conflict versus uncertain ACK, and upload versus accepted evidence. |
| 7:30–8:45 | Android → Yard Mode or Yard movement review fixture | Select the existing synthetic Site and use its synthetic scan. Review recorded origin, allowed destination and confirmation. **Not Yet** closes review. “Yard staff review what was found and where it will move; scanning does not silently correct location.” No physical Trailer moved and no server accepted an operation. Mention Yard Check workflow only as implemented functionality; do not present an unexecuted check as completed. |
| 8:45–9:45 | Admin Overview → Customers / Trailers → Audit | Show shared facility/equipment context and the read-only Customer change event. “Administration maintains the records; Audit explains recorded changes.” Note that Audit access is capability-filtered. Do not claim a general user-management console, Undo feature or audit compliance certification. |
| 9:45–11:15 | Customer Dashboard → Orders → Deliveries → Documents | “A Customer sees its authorized work and designated equipment, without internal Driver/Tractor or other Customer data.” Show friendly status distinctions and accepted evidence metadata. Open document metadata to show the honest unavailable-download state: “Real downloads require fresh authorization and configured private storage; neither is demonstrated here.” Explain that synthetic Access verified is a fixture state, not real Firebase verification. |
| 11:15–12:15 | Customer mobile screenshot / Operations tablet / limitations slide | Show responsive navigation and states. “These screenshots come from running React and native Android. We have not validated your infrastructure, actual device fleet, iOS, camera lifecycle, storage or production delivery.” State remote CI is **NOT VERIFIED — BILLING BLOCKED**; local tests are separate engineering evidence. |
| 12:15–13:00 | Closing slide / discovery questions | “Which handoff would be most valuable to validate with your team: locating a Trailer, dispatching a Trip, recording a Yard move or sharing delivery evidence?” Ask about existing tools, roles, device/connectivity conditions and interest in a scoped evaluation. No pricing, rollout date, SLA, savings guarantee or production deployment commitment. |

## Presenter guardrails and fallback

Do not run a real end-to-end shipment across these independent fixture datasets.
No synthetic command is a backend success. Leave all demo disclosure bars visible.
For backend/storage/camera questions, use the claims matrix and say what is
implemented versus what has not been validated. If an interface is unavailable,
use its indexed screenshot and name its environment; do not improvise fake data,
disable authorization, alter production settings or expose the dev server.

Reserve questions after the 13-minute walkthrough; shorten by omitting Admin
directories and planning navigation, never by dropping authenticity disclosure.
No recording is supplied: still screenshots meet the required scope and avoid
suggesting a continuous live operational flow.
