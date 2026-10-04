# ADR-007 — Authentication: Firebase Authentication, Authorization in TrailerSaaS

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS needs authentication for the React web app and Flutter iOS/Android app, plus password recovery and future MFA/federation.

Business authorization is domain-specific:

- tenant membership;
- customer scope;
- Admin / Transport Operations / Customer Dispatcher / Yard / Driver permissions.

Identity and business authorization should remain separate concerns.

## Decision

Use **Firebase Authentication** as the initial managed identity provider.

Use TrailerSaaS/PostgreSQL as the source of truth for:

- tenant membership;
- customer scope;
- business role;
- permissions;
- application-level enabled/disabled state.

The backend validates Firebase identity tokens and maps the external UID to an internal TrailerSaaS User.

Do not trust tenant/customer/role data supplied by clients.

## Why Firebase for MVP

- official Flutter SDK;
- official Web/JavaScript SDK;
- email/password and federated identity support;
- avoids building password storage/reset flows;
- future Identity Platform features can extend enterprise identity needs.

## Tenant model

Firebase users are identities, not SaaS tenants.

```text
Firebase UID
   ↓
TrailerSaaS User
   ↓
TenantMembership
   ├── role
   └── customer scope (optional)
```

## Invitations

TrailerSaaS owns invitation/business onboarding state. The backend links a newly authenticated Firebase UID to the pending internal invitation/membership.

## Consequences

### Positive

- strong Flutter/web support;
- fast secure MVP authentication;
- password lifecycle outsourced;
- business authorization stays under our control.

### Negative / trade-offs

- vendor dependency for identity;
- organization invitation logic remains TrailerSaaS domain logic;
- enterprise SSO may later require Identity Platform or a different provider.

## Follow-up

- define Spring Security token validation;
- define invitation flow;
- define email-verification policy;
- decide MFA requirements for privileged roles before production.
