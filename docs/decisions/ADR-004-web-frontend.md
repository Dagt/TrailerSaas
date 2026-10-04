# ADR-004 — Web Frontend: React + TypeScript + Vite

**Status:** Accepted  
**Date:** 2026-10-04

## Context

The web application must serve:

- Tenant Administrators;
- 2 Fast / transportation operations;
- customer dispatch/shipping users.

The application is an authenticated operational dashboard, not an SEO-driven public website.

## Decision

Use:

- **React 19**
- **TypeScript**
- **Vite 8**
- TanStack Query for server-state/data fetching
- React Hook Form for complex forms
- Zod for client-side schema validation
- a reusable component system (final component library to be chosen during UX setup)

Use one web application with role/permission-driven navigation rather than separate admin and customer codebases.

## Rationale

- SPA architecture fits the authenticated dashboard use case.
- React ecosystem is mature for data-heavy tables/forms.
- TypeScript improves maintainability.
- Vite keeps build/dev tooling simple.
- One application avoids duplicating components and authorization logic.

## Consequences

### Positive

- fast development;
- broad ecosystem;
- easy shared design system;
- good fit for dashboards and customer portal.

### Negative / trade-offs

- server authorization must never depend on UI role hiding;
- large operational tables require deliberate query/pagination design;
- frontend state must remain separate from backend source of truth.

## Follow-up

- select component/design system;
- define route structure;
- define permission-aware UI shell;
- define table/filter conventions.
