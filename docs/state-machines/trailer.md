# Trailer State Model

**Status:** Accepted v0.1

## Principle

TrailerSaaS must **not** use one giant Trailer status enum.

A Trailer has several independent dimensions:

1. location/movement state;
2. load state;
3. coupling state;
4. serviceability;
5. designated Customer;
6. usage/purpose.

This avoids recreating the current spreadsheet problem where values such as MT, FULL, DROP, SHUTTLE and storage are mixed together.

---

# A. Location / Movement State

## States

- `AT_LOCATION`
- `IN_TRANSIT`
- `LOCATION_UNKNOWN`

## Transitions

```text
AT_LOCATION
 ├── confirmed departure ──────────► IN_TRANSIT
 ├── yard move ────────────────────► AT_LOCATION (location changes)
 └── confirmed missing/discrepancy ► LOCATION_UNKNOWN

IN_TRANSIT
 ├── confirmed arrival/drop ───────► AT_LOCATION
 └── severe reconciliation issue ──► LOCATION_UNKNOWN

LOCATION_UNKNOWN
 └── scan/confirmed location ──────► AT_LOCATION
```

### Invariants

- `AT_LOCATION` requires `current_location_id`.
- `IN_TRANSIT` normally has no stationary current location; last-known origin remains in history.
- `LOCATION_UNKNOWN` preserves:
  - last known location;
  - last seen timestamp;
  - last Driver.
- Entering `LOCATION_UNKNOWN` generates an Administration alert.

---

# B. Load State

## States

- `EMPTY`
- `LOADED`
- `UNKNOWN`

`LOADING` and `UNLOADING` are operational activities at a Stop, not persistent load states.

## Typical transitions

```text
EMPTY ── load confirmed ──► LOADED
LOADED ─ unload confirmed ► EMPTY

EMPTY/LOADED ─ uncertainty/correction ► UNKNOWN
UNKNOWN ─ reconciliation ─────────────► EMPTY or LOADED
```

Manifest/order allocations describe **what** is loaded.

---

# C. Coupling State

## States

- `UNCOUPLED`
- `COUPLED`

When `COUPLED`, the current coupling identifies:

- Trailer;
- Tractor;
- Driver;
- Trip;
- hook timestamp.

## Transitions

```text
UNCOUPLED ─ authorized HOOK ─► COUPLED
COUPLED ─ DROP ──────────────► UNCOUPLED
```

### Invariants

1. A Trailer cannot have two active couplings.
2. A Tractor cannot have two active Trailers in the MVP operating model.
3. Wrong-trailer substitution cannot create a coupling until authorized.
4. Live Load/Unload does not change coupling state unless the Trailer is physically dropped.
5. Drop & Hook closes one coupling and opens another.

---

# D. Serviceability

## States

- `IN_SERVICE`
- `OUT_OF_SERVICE`

## Transitions

```text
IN_SERVICE ─ mark OOS + evidence ─► OUT_OF_SERVICE
OUT_OF_SERVICE ─ authorized return ► IN_SERVICE
```

OOS is independent of load/location.

An OOS Trailer should not appear in normal available-equipment selection.

Authority for `OUT_OF_SERVICE → IN_SERVICE` remains a product-policy decision.

---

# E. Designated Customer

Designated Customer is an assignment, not a state machine.

Rules:

- remains until Administration changes it;
- change requires authorization and audit;
- independent of current Order, load and physical location.

---

# F. Usage / Purpose

Examples:

- DELIVERY
- SHUTTLE
- WIP_STORAGE
- TOOLING_STORAGE
- OTHER

Purpose is classification/context, not location or load state.

---

# Availability as a derived concept

Do not persist a simplistic `AVAILABLE` flag unless required for performance.

Equipment availability can be derived from conditions such as:

- serviceability = IN_SERVICE;
- coupling = UNCOUPLED or appropriate current Trip context;
- operational/location state permits assignment;
- no incompatible active reservation/allocation.

A read projection may materialize availability later.

## Suggested trailer events

- `TrailerHooked`
- `TrailerDropped`
- `TrailerDeparted`
- `TrailerArrived`
- `TrailerMovedWithinYard`
- `TrailerLocationCorrected`
- `TrailerLocationMarkedUnknown`
- `TrailerLocationRecovered`
- `TrailerLoadStateChanged`
- `TrailerMarkedOutOfService`
- `TrailerReturnedToService`
- `TrailerDesignatedCustomerChanged`
