# ADR-009 — Initial Deployment: Railway + Cloudflare

**Status:** Proposed  
**Date:** 2026-10-04

## Context

The first production target has roughly 35 drivers, 150 trailers and more than 100 movements/day. The project should minimize DevOps overhead while remaining portable.

## Decision

Initial deployment:

- React/Vite web app: **Cloudflare Pages**
- Spring Boot backend: **Railway**
- PostgreSQL: **Railway managed PostgreSQL** or compatible managed PostgreSQL
- object storage: **Cloudflare R2**
- source/CI: **GitHub + GitHub Actions**

Backend must remain packaged as a standard application/container so migration to another host remains practical.

## Rationale

- low operational overhead;
- existing familiarity with Railway and Cloudflare;
- ample capacity for initial scale;
- avoids premature AWS/Kubernetes complexity.

## Consequences

### Positive

- fast deployment;
- simple environments;
- low initial infrastructure maintenance.

### Negative / trade-offs

- platform limits/pricing must be monitored;
- future enterprise/compliance requirements may require different hosting.

## Follow-up

- define dev/staging/prod;
- define secrets management;
- define DB backup/restore;
- define health checks and deployment strategy.
