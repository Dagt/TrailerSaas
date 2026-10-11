# Issue #53 — operational evidence implementation plan

Baseline: `fb773e956fb977659126e0037d4fc6e6da487b79` (PR #99 merged).
Status: implementation in progress; #53 is not complete until reviewed and merged.

## Traceability prepared before programming

| Criterion | Existing implementation | Pending implementation | Required verification |
| --- | --- | --- | --- |
| Four Pre-Trip photos | Four accepted document types; private upload transport | Typed photo-set validator, own Driver and assigned Trip/Tractor context | Missing positions, duplicate/type mismatch, pending/foreign/other uploader, revocation |
| SIGNED_BOL | Uploaded BOL and authorized Stop links | Delivery-only Stop policy with current execution authority | Correct/wrong type, exact Stop association, assignment/Customer isolation |
| NO_RECEIVER_AVAILABLE | BOL and DELIVERY_PHOTO types | Two distinct finalized documents plus normalized nonblank comment | Each missing item, duplicates, whitespace, foreign context |
| Driver damage / OOS | DAMAGE_PHOTO catalog; existing exception/OOS capabilities | Driver-context validation and prepared contextual OOS policy | Type/state/actor/resource checks; no generic Trailer access from EXCEPTION_REPORT |
| Fuel receipt | FUEL_RECEIPT catalog; FUEL_EVENT_CREATE capability | Assigned Trip/Tractor receipt validation | Missing receipt, wrong type/state/owner, retry after error |
| delivery_evidence | Accepted schema only | V20, immutable insert-only persistence coordinated with a real completion transaction | FK/CHECK/RLS/grants, uniqueness, concurrent completion and joint rollback |
| Type-specific upload authority | Only POD_UPLOAD types implemented in #52 | PRETRIP_CREATE, FUEL_EVENT_CREATE and Driver EXCEPTION_REPORT for their types | All five transport endpoints, authoritative persisted type, revocation and ledger replay |
| Future workflow integration | Documents -> Trips public attachment directory | Typed synchronous Documents port, higher-level orchestration callback | Spring Modulith without reverse Trip dependency; real PostgreSQL completion harness |

## Dependency and transaction design

Documents continues to consume only public Identity, Drivers, Trips and a narrow
Trailer context boundary. Trips never imports Documents. The future #43/#45/#49
application orchestration calls both module APIs; it does not put a Documents
dependency inside Trips. Evidence validation is synchronous in an existing
Tenant-pinned transaction, not an asynchronous event that could approve a Stop
after its completion.

The delivery coordination port validates an IN_SERVICE Delivery Stop and evidence,
invokes the owning workflow's completion action in the SAME transaction, verifies
the persisted valid COMPLETED transition, then inserts delivery_evidence. A no-op
or failing callback cannot persist evidence. #45 owns the actual domain transition,
Stop event, Order fulfillment, Trip consequences and existing idempotency ledger.
#53 adds no production delivery-completions endpoint and never updates Stop state.

Pre-Trip and Fuel use currently real assigned Trip/Tractor context and TRIP
attachment identity, without inventing Shift/inspection/Fuel rows. #43 additionally
validates Shift and inspection ownership when those domains exist. Driver damage
uses assigned work; Trailer OOS validation requires its separate capability and
exact Trailer attachment. The public Yard/OOS upload/link workflow remains #49;
EXCEPTION_REPORT never grants arbitrary Trailer attachment or OOS mutation.

V20 will add delivery_evidence and extend the existing polymorphic attachment
catalog for explicit context validation; V1–V19 remain byte-for-byte unchanged.
R2 publication, byte verification and the ledger from #52 remain unchanged.

## Continuity checkpoint

Main/baseline and the merged PR #99 were verified. Working tree was clean; no
partial #53 implementation or stash was found. Branch:
`issue-53-operational-evidence-validation`. Next: implement V20, public typed
evidence policy/resource facts, type-specific authorization, then real SQL/HTTP
and transaction-harness tests. No production workflows from #43/#45/#49/#54/#55
are implemented by this plan.

## Implementation checkpoint: policies and persistence

V20, typed public evidence policy, type-specific upload authority and public
Trip/Trailer resource facts are implemented. The #52 staging/publication adapter,
SHA-256 verification and ledger are unchanged. Pre-Trip/Fuel/Driver damage now
have their existing capability gates; public Trailer/OOS attachment remains blocked.
The joint delivery port additionally verifies that both the Stop row and completion
event were written by its current PostgreSQL transaction, using tuple transaction
identity (including the 32-bit xid wrap), not a caller-supplied approval flag.

Initial affected validation: 164 tests passed with 0 failures/errors/skipped,
including 40 new evidence tests, all 100 Documents/R2 tests, runtime RLS and module
boundaries. The tuple transaction-identity check is being verified next. OpenAPI
validation and 9 contract tests passed. Historical exact schema inventories were
extended for V20 and delivery_evidence; no test assertion was dropped.

Remaining: strengthen edge-case tests and independent security review, full clean
verify and four-job final-head CI, then one open PR. This checkpoint does not
claim the issue merged or the future operational workflows implemented.

## Implemented ports and workflow ownership

`OperationalEvidencePolicy` exposes context-bound Pre-Trip, delivery, Driver damage,
Trailer OOS and Fuel checks, plus attachment-free Pre-Trip/Fuel upload checks for a
NEW future inspection/event. Accepted inspections allow nullable trip_id: #43 must
authorize its real Shift/Tractor first, validate the four own unlinked uploads,
then create the inspection and authorized attachments in one transaction. The
upload-only checks cannot approve a Shift, inspection, event or departure. Already
linked documents cannot be repurposed through this path.

For existing Trip context, Pre-Trip/Fuel checks require exact Trip attachment,
current Driver and current active Tractor, using the existing TractorPlanningPolicy.
#43 additionally validates its Shift/inspection identities; #45 owns Shift-only
Fuel context. The policy does not infer which equipment an image visually depicts.

For #45, call `validateDeliveryForReplay` BEFORE the existing ledger. It locks the
Trip exclusively, revalidates all documents/resources and allows a COMPLETED Stop
only with an actual delivery_evidence record for committed replay. The ledger
then either returns its saved result or invokes `recordDelivery` with the owning
workflow's completion action. This ordering prevents concurrent shared-lock
upgrades from deadlocking against a claimed idempotency row. The application
boundary maps the existing public IdempotencyKeyReusedException; no new ledger is
introduced. The real PostgreSQL HTTP harness verifies identical lost-ACK replay,
incompatible keys, fresh revoked-assignment denial and concurrent same-key calls.

The harness exists only in test sources and is excluded from component scanning;
it is explicitly imported for evidence tests. It performs guarded runtime SQL for
the valid IN_SERVICE -> COMPLETED transition and inserts the authenticated durable
completion event. No mocked validator approves it, and no production delivery
endpoint or Stop state mutation is added to Documents.

## Authorization and error boundary

Type mapping is BOL/POD/DELIVERY_PHOTO -> POD_UPLOAD; four PRETRIP positions ->
PRETRIP_CREATE; FUEL_RECEIPT -> FUEL_EVENT_CREATE; Driver DAMAGE_PHOTO ->
EXCEPTION_REPORT. All require the active own Driver. Concrete Trailer OOS checks
separately require TRAILER_OOS_MARK and exact Trailer attachment; its public
producer remains #49 because the generic init request has no authorized Trailer
context. Read grants confer no upload permission and role mappings are unchanged.

Driver delivery requires BOL (generic POD is not a signed-BOL substitute), plus a
distinct DELIVERY_PHOTO and normalized nonblank comment for NO_RECEIVER_AVAILABLE.
Both require the exact IN_SERVICE DELIVERY Stop on the current AT_STOP Trip and
all linked-resource access. Unicode surrounding/blank spacing is normalized;
comments are not treated as signature recognition. Document UUIDs are locked in
sorted order. Authorization is re-evaluated after lock waits before validation or
ledger replay, preserving the #52 linked-uploader correction.

Errors are safe EVIDENCE_REQUIRED, UPLOAD_NOT_COMPLETE, VALIDATION_FAILED,
RESOURCE_NOT_FOUND, CAPABILITY_DENIED, INVALID_STATE_TRANSITION or RESOURCE_CONFLICT.
Infrastructure exceptions remain safe 5xx, never evidence success. V20 grants only
SELECT/INSERT on delivery_evidence; no runtime UPDATE/DELETE, ownership, bypass,
PUBLIC grants or destructive cascade. Its unique Tenant/Stop index serves lookup
and concurrency; no speculative read indexes are added.

## Second security/architecture review

Reviewed the policy, transport changes, V20, resource ports, every modified test
inventory and OpenAPI. The review strengthened: permission checks after locks;
current-transaction completion/event proof; optional-Trip inspection staging;
exclusive-parent-before-ledger order; and whitespace-only comments.

The evidence and transaction suites prove persisted UPLOADED/type/owner checks,
exact context, shared-Customer denial, revocation, pending/foreign data rejection,
insert-only RLS history, uniqueness, joint rollback, lost-ACK recovery and no
duplicate event. Sources contain no foreign-module internal imports. Trips remains
independent of Documents, and module tests verify this direction. R2 adapter,
publication keys, SHA-256, ledger implementation and the 57 capabilities are intact.

No #43/#45/#49 production workflow, #54 media, #55 jobs, OCR/signature recognition,
antivirus or retention is implemented. #53's policy does not certify a valid visual
signature or photograph; future operational domains retain their own preconditions.

## Final local validation checkpoint

186 affected tests passed: 51 real PostgreSQL evidence tests, 11 unit normalization/
capability cases, all 100 existing Documents/R2 tests, 17 RLS tests and 7 application/
module tests; 0 failures, errors or skipped, BUILD SUCCESS. OpenAPI validation and
9 tests passed. The old six denied-type expectations were replaced by stronger
type-authority coverage of successful Driver upload/finalize/metadata/link/download,
read-role denial and revoked assignment/role replay; the test count is preserved.

The workflow harness originally needed its own HTTP mapping for the public ledger
exception because existing production advice is controller-scoped. It now reuses
the existing Documents error mapping at its test-only application boundary; no
production error contract or ledger implementation changed. Both same-key
concurrency and incompatible-key cases pass against real PostgreSQL.

Next checkpoint: publish reviewed changes, run complete four-job CI on that HEAD,
inspect exact totals/reviews, then mark the single PR Ready for Review. No merge.
