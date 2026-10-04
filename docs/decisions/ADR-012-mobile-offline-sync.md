# ADR-012 — Mobile Offline Synchronization Architecture

**Status:** Proposed  
**Date:** 2026-10-04

## Context

Road Driver and Yard/Spotter workflows must tolerate temporary connectivity loss.

The app performs stateful actions involving:

- Shift;
- inspections;
- Trailer Hook/Drop;
- Trip/Stop transitions;
- POD/evidence;
- Yard Moves;
- Yard Checks.

The system must avoid duplicate actions, preserve evidence and re-authorize against current server state after reconnect.

## Decision

Use an **offline command queue + media upload queue** in the Flutter local SQLite/Drift database.

Do not mirror the entire server database and do not introduce a general-purpose CDC synchronization platform for MVP.

Each critical queued command uses a client-generated UUID as the HTTP `Idempotency-Key`.

Commands are replayed to the same explicit REST command endpoints used online.

Dependent commands are serialized through explicit dependency relationships.

After replay, the client performs targeted authoritative refresh of the active Driver/Trip/Yard resources.

## Media

Media captured offline is stored in app-private local storage.

On reconnect:

1. initialize Document;
2. upload directly to R2;
3. finalize Document;
4. release dependent domain command.

## Conflict model

The server remains authoritative.

Retryable infrastructure failures are retried automatically.

State/authorization/business conflicts become explicit client sync issues requiring refresh/reconciliation.

Conflicting commands are never silently discarded.

## Concurrency

For offline physical commands, prefer semantic domain preconditions over exact aggregate-version equality where unrelated changes could cause false conflicts.

Administrative/correction mutations continue to use strict optimistic concurrency.

## Background execution

Sync may use OS background capabilities as best effort, but correctness must not depend on them.

Foreground/reconnect sync is sufficient for correctness.

## Why not full local replication

Rejected for MVP:

- full PostgreSQL-to-SQLite replication;
- generic bidirectional entity synchronization;
- event-sourcing client replica;
- custom CRDT model.

These approaches add substantial conflict/security complexity without being required by the defined operational workflows.

## Why not generic /sync batch command API initially

The existing domain command endpoints already provide:

- authorization;
- validation;
- idempotency;
- domain state transitions.

Replaying those endpoints avoids maintaining two mutation surfaces.

A future batch transport optimization can be added without changing command semantics.

## Consequences

### Positive

- understandable failure model;
- preserves domain APIs;
- durable offline queue;
- supports photos/evidence;
- no dependency on continuous background execution;
- easier to test command-by-command.

### Negative / trade-offs

- dependent command sequencing must be implemented carefully;
- conflicts can require user/Dispatch attention;
- media sync is separate from structured command sync;
- app must maintain targeted caches for current work.

## Follow-up

- review API v0.1 mobile commands for strict-version fields that should become semantic preconditions;
- define Drift tables/migrations;
- define sync worker retry/backoff implementation;
- define secure local media storage;
- integration-test full offline sequences on real iOS/Android devices.
