# ADR-008 — Object Storage: Cloudflare R2

**Status:** Proposed  
**Date:** 2026-10-04

## Context

TrailerSaaS must store BOL/POD photos, delivery photos, Pre-Trip photos, damage/OOS photos and fuel receipts. These binaries should not live inside PostgreSQL.

## Decision

Use **Cloudflare R2** as initial object storage.

PostgreSQL stores metadata/references only.

Objects are private by default. Where practical, clients upload/download through short-lived signed URLs instead of proxying large files through Spring Boot.

## Object metadata

Store at least:

- tenant ID;
- object key;
- document/media type;
- related business entity;
- uploader/source;
- created timestamp;
- content type;
- size;
- optional checksum.

## Key convention

Do not rely on object path for authorization.

Candidate layout:

```text
tenant/{tenantUuid}/{entityType}/{entityUuid}/{objectUuid}
```

Backend authorization is required before issuing access URLs.

## Consequences

### Positive

- keeps PostgreSQL small;
- S3-compatible tooling;
- direct uploads reduce backend bandwidth;
- familiar infrastructure.

### Negative / trade-offs

- orphan upload cleanup must be handled;
- object retention and DB retention need coordination;
- signed URL rules require careful security.

## Follow-up

- define upload init/finalize protocol;
- define file size/compression rules;
- define retention cleanup worker.
