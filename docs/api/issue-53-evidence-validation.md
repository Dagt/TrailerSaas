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
