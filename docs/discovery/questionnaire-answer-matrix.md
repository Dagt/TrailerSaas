# Discovery Questionnaire — Answer Matrix

**Status:** Discovery evidence  
**Date:** 2026-10-03

This file summarizes the answered requirements questionnaire in a traceable, implementation-friendly form. Unknown or ambiguous answers are intentionally preserved as unresolved instead of being guessed.

## Business and scale

| Topic | Answer |
|---|---|
| Service | Deliver finished cardboard products, sheets, rolls, parts and supplies according to customer delivery needs |
| Customers | Multiple companies |
| Regions | Illinois, Wisconsin, Indiana, Michigan |
| Daily movements | >100 |
| Drivers | ~35 |
| Tractors | ~33 |
| Trailers | ~150 |
| Trailer ownership | Company-controlled, mostly leased |
| Main problem | Logistics control, trailer inventory/location, delivery instructions/status, delivery records and driver work |
| Other problem | Inaccurate records and unreliable trailer location |

## Order creation and dispatch

| Topic | Answer |
|---|---|
| Request channels | Email, shipping documents, phone messages, in person |
| Primary request identifier | Shipment number |
| Other identifiers | Order number, PO |
| Driver assignment | Shipping team |
| Tractor assignment | Usually organized by driver |
| Trailer assignment | Based on availability |
| Trailer known at start | Often only after BOL/shipping paperwork is received |
| Driver instructions | Phone or in person |
| Information given | BOL, trailer number, appointment time |
| Assignment changes | Yes |
| Who can change | Shipping / dispatchers |
| Scheduled + urgent jobs | Both |
| Multiple jobs | Yes |
| Priority | Appointment time |

## Trailer state and inventory

| Topic | Answer |
|---|---|
| Unique identifier | Yes |
| VIN/plate stored today | No |
| Permanent info expected | Trailer number and designated client |
| Types | 53' dry van, 48' dry van, low-profile 53' dry van |
| Heavy-load specialization | Some trailers for paper rolls |
| MT | Empty |
| FULL | Loaded |
| DROP | Left at customer |
| SHUTTLE | Transfer |
| DELIVERY | Order ready for delivery |
| LOADING | Loading in progress |
| TOOLING STORAGE | Machine/tool material storage |
| WIP STORAGE | Work-in-process storage |
| Multiple current statuses | No in current Sheet |
| What changes status | Content or location |
| Who changes status today | Administrators and drivers |
| CUSTOMER | At delivery/customer location |
| Reliable customer location | No; inferred from documents |
| Dwell time known | No |
| Can remain for days/weeks | Yes |
| Max allowed dwell | None defined |
| Stale trailer alerts | Desired |
| “Lost” trailer | Location unknown |
| Frequency | Happens often |
| Recorded vs physical mismatch | Happens often |
| Out of service | Yes, OOS |
| Record damage | Yes |
| Photos | Yes |

## Tractors

| Topic | Answer |
|---|---|
| Identifier | Number |
| Driver assignment | Generally assigned but tractors remain available |
| Drivers can change tractors | Yes |
| Track tractor used in movement | Yes |
| Need tractor location in product | No current requirement |
| Existing telematics | Some tractors |
| Provider | Samsara |
| Maintenance/fuel/etc. | Potentially useful, not confirmed |

## Drivers and mobile

| Topic | Answer |
|---|---|
| Driver master data | Name, license number, medical certificate expiration |
| Shift work | Yes |
| Track shift start/end | Yes |
| Multiple movements/shift | Yes |
| Handoff between drivers | Yes |
| Current reporting | Messages |
| Common missing data | Arrival/departure |
| Common error | Forgetting to mark arrival/departure |
| Smartphones | All drivers |
| Company-provided phones | No |
| Platforms | iOS and Android |
| Bad signal common | No |
| Temporary offline | Desired |
| Location always enabled | Uncertain |
| Location during deliveries | Likely acceptable |
| GPS policy | Unknown |
| Simple hook/drop action realistic | Yes |

## Locations and geofencing

| Topic | Answer |
|---|---|
| Company-owned yards | None |
| Customer yards used as bases | 3 |
| Destinations | >200 / variable |
| Repeat locations | Yes |
| Addresses known | Yes |
| Multiple docks | Yes |
| Exact dock important | Not very |
| Pre-register locations on map | Potentially useful |
| Auto-detect arrival | Desired |
| Auto-detect departure | Desired |
| Dwell time | Desired |
| Full route vs arrival/departure | Ambiguous answer |
| Arrival without work | Rare |
| Geography | Primarily around Illinois / Midwest |

## Hook / drop

| Topic | Answer |
|---|---|
| Current tractor ↔ trailer relationship known | No |
| Relationship currently recorded | No |
| Wrong/different trailer | Driver messages which trailer was picked up |
| Durable QR possible | Yes |
| QR scanning practical | Yes |
| Existing QR/barcode/RFID | No |
| RFID/NFC | Unknown |
| Hook information | Trailer number + order number |
| Drop information | Location |
| In-yard trailer moves | Must be recorded |

## Customers / recipients

| Topic | Answer |
|---|---|
| External users needed | Shipping/receiving personnel of main customers |
| Main customer needs visibility | Yes |
| Final third-party recipient provides info | Generally no |
| Final-party unique info | BOL noted in questionnaire; needs clarification |
| Delivery confirmation | Yes |
| Signature | Initially answered yes |
| Photos | Yes |
| Delivery documents | Yes |
| Confirmation on driver phone | Uncertain |
| Link without account | Uncertain |
| Permanent customer accounts | Yes |
| Customer visibility | Assigned trailer inventory + location |
| Customer movement requests | Yes |
| Tenant/customer data scoping | Customer sees only its trailers/jobs |
| Multiple contacts | Yes |

## Yard check

| Topic | Answer |
|---|---|
| Current owner | Example: Hood Container shipping lead |
| Frequency | Daily |
| Physical walk | Sometimes |
| Duration | ~1 hour |
| What is checked | Product in trailers |
| Missing trailer | Search documents |
| Wrong recorded location | Correct spreadsheet |
| History | Suggested ~15 days |
| QR yard check | Desired |
| Automatic missing list | Desired |
| Multiple yards | Yes |

## Spreadsheet

| Topic | Answer |
|---|---|
| Maintainer group | Yard spotter, shipping/receiving, administrators |
| Approx. editors | 7 |
| Drivers edit directly | No |
| Main columns | Trailer, location, status, order, date, checked |
| Dropdown | Yard/dock/location |
| Color use | E.g. red loaded, yellow WIP |
| Essential info | Load status, location, order |
| Frequent errors | Missing updates, incorrect data |
| Overwrites prior data | Yes |
| Who changed data known | No |
| Movement time known | No |
| Other sheet | No |
| Other systems | Yes |
| Named system | Amtech |

## Exceptions

| Situation | Current behavior |
|---|---|
| Assigned trailer missing | Driver searches |
| Different trailer found | Cannot simply take it |
| Wrong trailer taken | Operational error |
| Trailer left in wrong place | Becomes hard to locate |
| Customer rejects load | Return to base |
| Destination closed | Return to base |
| Trailer damaged | Load may be rejected |
| Tractor fails | Send another tractor to recover load |
| Destination changes mid-trip | Unknown |
| Driver handoff | Verbal explanation |
| No internet | Phone call |
| Phone dead | Driver relies on instructions/knowledge |
| GPS false arrival | Unknown |
| Correct bad data | Administration/qualified staff |
| Audit corrections | Desired |
| Cancel movement | Probably possible |
| Unusual case | Delivery address changes |

## Administration

| Capability | Answer |
|---|---|
| Desktop users | Administration, shipping leads/personnel |
| Same permissions | No |
| Create/modify trailers | Administrators |
| Create clients | Shipping personnel |
| Create locations | Shipping personnel |
| Assign trips | Administration/shipping |
| Modify active trip | Administration/shipping |
| Correct trailer location | Questionnaire says “everyone”; requires restriction/clarification |
| Different roles | Yes |
| Dispatchers/supervisors | Yes |
| Know who performed action | Yes |

## Dashboard and alerts

| Need | Answer |
|---|---|
| Morning priority | Pending orders and undelivered loads |
| Map of all trailers | No |
| Search trailer by number | Yes |
| Last movement | Yes |
| Last driver | Yes |
| Time at location | Yes |
| Empty trailers | Yes |
| Loaded trailers | Yes |
| At-customer trailers | Yes |
| In-transit trailers | Yes |
| Stale trailers | Yes |
| Stale threshold example | 3 days |
| Alert channel | App |
| Alert recipient | Administration |
| Reports | Probably |
| Current reports | Unknown |

## History / evidence

| Need | Answer |
|---|---|
| Trailer history | Prior content and movements |
| Retention | 1 month stated |
| Actor attribution | Yes |
| Exact date/time | “Dates” stated; precision must be clarified |
| GPS coordinates in history | No |
| Photos | Yes |
| Standalone signature | Later answer says no |
| Related order | Yes |
| Related client | Yes |
| Prove location/time of delivery | Yes |

## Automation

| Topic | Answer |
|---|---|
| Fully automatic tasks | Unknown |
| Driver manual checkpoints | Depart base, arrive customer, proof of delivery, return base |
| One-button actions | Entry/exit |
| QR action | Identify contents and BOL copy |
| GPS automation | Unknown |
| Most expensive admin activity | Physical checking |
| Repeated data | Location and delivery status |

## Success criteria

| Topic | Answer |
|---|---|
| #1 problem | Missing updates |
| “Solved” outcome | More automated logistics |
| #2 | Real inventory data |
| Nice-to-have | Delivery time, quantities, efficiency |
| First three capabilities | Location, delivery status, trailer contents |
| Costly error | Lost orders |
| Search/verification time | 1–2 hours |
| Current number of untrusted/lost trailers | Unknown |
| Additional requested feature | Driver clock-in/clock-out for payroll |

## Ambiguities carried forward

See `docs/product/open-questions.md`. No answer marked uncertain in this file should be silently converted into a hard requirement.
