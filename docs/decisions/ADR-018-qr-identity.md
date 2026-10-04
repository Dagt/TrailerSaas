# ADR-018 — Trailer QR Identity and Resolution

**Status:** Accepted  
**Date:** 2026-10-04

## Context

Trailer QR scanning is used by Drivers and Yard/Spotters for:

- Hook/Drop;
- Yard Move;
- Yard Check;
- Trailer lookup.

The visible Trailer number must not be used as technical identity, and QR values may need to be revoked/reissued.

## Decision

Each physical QR contains an **opaque public token**, not:

- Trailer operational number;
- database UUID alone;
- Tenant/customer information;
- authorization credentials.

The token is stored in `trailer_qr_identities.public_token`.

Use cryptographically random, non-sequential tokens with at least 128 bits of entropy.

## Encoded value

Prefer an HTTPS URL form:

```text
https://<configured-qr-host>/t/<opaque-token>
```

Benefits:

- native app can handle it through universal/app links;
- normal phone camera can open a web/deep-link fallback;
- QR format remains independent of internal API URL;
- token can be resolved server-side.

Do not hard-code a production domain into domain logic; QR host is environment configuration.

## Resolution

Authenticated app resolves token through the TrailerSaaS API.

Conceptually:

```text
scan URL/token
→ extract opaque token
→ authenticated API resolution
→ Tenant/customer authorization
→ Trailer summary
```

The QR token identifies a Trailer; it does not authorize the caller.

## Reissue / revocation

A Trailer may have historical QR identities.

Rules:

- only active token(s) resolve normally;
- compromised/damaged/replaced QR can be revoked;
- reissuing does not change Trailer UUID;
- revocation/issuance is auditable.

MVP should normally maintain one active physical QR identity per Trailer, while the schema can preserve revoked history.

## Offline scanning

The app may resolve a previously cached QR token offline only when the corresponding Trailer is already present in the authorized local cache.

Unknown QR token + no connectivity cannot be trusted/resolved from the token alone.

Offline wrong-Trailer rules still apply.

## Printing

QR label should also display the human operational Trailer number for visual identification, but that printed text is not the QR identity.

## Consequences

### Positive

- supports reissue/revocation;
- avoids leaking internal IDs/business data;
- works with app deep links and generic cameras;
- operational numbers can change without replacing technical identity.

### Negative / trade-offs

- token resolution requires API/cache;
- QR host/deep-link setup is required per environment.

## Follow-up

- define label dimensions/print workflow during UX implementation;
- configure universal/app links;
- generate tokens using a cryptographically secure source.
