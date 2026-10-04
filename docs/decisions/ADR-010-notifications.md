# ADR-010 — Push Notifications: Firebase Cloud Messaging

**Status:** Accepted  
**Date:** 2026-10-04

## Context

The mobile product needs push notifications for assignments, stale/missing trailers, reroutes and operational exceptions.

## Decision

Use **Firebase Cloud Messaging (FCM)** for mobile push delivery.

Notification business rules remain in TrailerSaaS backend. FCM is a delivery channel, not the source of truth.

Persist operational alerts in PostgreSQL when they need inbox/history/acknowledgement semantics.

## Rationale

- good fit with Flutter, Android and iOS;
- compatible with proposed Firebase Authentication;
- avoids building push infrastructure.

## Consequences

### Positive

- one mobile push integration;
- appropriate for near-real-time operational messaging;
- backend remains authoritative.

### Negative / trade-offs

- device token lifecycle must be managed;
- push delivery is not guaranteed;
- APNs setup remains necessary for iOS.

## Follow-up

- define device registration;
- define notification categories/priorities;
- define acknowledgement model;
- decide whether email/SMS channels are later needed.
