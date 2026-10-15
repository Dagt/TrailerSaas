# Alerts, Notifications and Audit API v0.1

**Status:** Accepted v0.1

Base:

`/api/v1/tenants/{tenantId}`

## 1. Alerts

### GET /alerts

Capability: `ALERT_VIEW`.

Filters:

- status;
- alertType;
- severity;
- relatedEntityType;
- from/to.

Customer-scoped users receive only alerts explicitly visible within their scope.

### GET /alerts/{alertId}

Returns alert detail and permitted related-resource summary.

## 2. Acknowledge Alert

### POST /alerts/{alertId}/acknowledgement

Request:

```json
{
  "expectedVersion": 2
}
```

Transitions:

- OPEN -> ACKNOWLEDGED.

If already acknowledged by an equivalent request, API may return current state idempotently.

Acknowledgement means attention/ownership, not resolution.

## 3. Resolve Alert

### POST /alerts/{alertId}/resolution

Request:

```json
{
  "expectedVersion": 3,
  "reason": "Trailer location recovered"
}
```

Allowed only when caller has resolution authority and the underlying condition permits/manual resolution.

Some Alerts resolve automatically from domain events.

Example:

- TRAILER_MISSING can resolve when Trailer location is recovered;
- STALE_TRAILER can resolve on meaningful movement.

## 4. Driver/User operational updates

### GET /driver/me/updates

Defined in Mobile Execution API.

Updates may be backed by domain events/alert/notification projections.

Push notification is never the sole source of an operational instruction.

## 5. Device registration

### POST /devices

Authenticated User.

Request:

```json
{
  "platform": "ANDROID",
  "fcmToken": "...",
  "deviceName": "optional"
}
```

Implemented global path: `/api/v1/devices` (not under a Tenant).
Server associates the registration with the authenticated global User. Membership
and Alert visibility are revalidated separately for each Tenant delivery.

Same FCM token registration should be safely upserted/rotated.

### DELETE /devices/{deviceId}

Deactivates the authenticated User's registration and clears its token, retaining
immutable ownership/history. It does not delete the registration row.

This is not deletion of the User account.

## 6. Notification delivery

No client API may mark a business action complete merely because push was delivered.

Backend notification worker may update `alert_recipients.push_status` for diagnostics.

Business state is always read from authoritative API resources.

## 7. Audit

### GET /audit-events

Capability: `AUDIT_VIEW`.

Filters:

- actorUserId;
- actionCode;
- entityType;
- entityId;
- from/to.

Cursor pagination required.

### GET /audit-events/{auditEventId}

Returns:

- actor;
- source type;
- action;
- entity;
- before/after data if permitted;
- reason;
- timestamp.

Audit is read-only.

## 8. Operational timeline vs Audit

Use resource-specific timeline endpoints for operational UX:

`GET /trips/{tripId}/timeline`

Potential future:

`GET /trailers/{trailerId}/timeline`

Audit API is for compliance/admin tracing and should not replace purpose-built operational timelines.

## 9. Data minimization

Customer Dispatcher must not receive:

- another Customer's audit data;
- internal membership/role changes;
- internal administrative metadata unrelated to its Customer scope.

Driver audit visibility, if exposed at all, should be limited to own operational actions.

## 10. Alert creation

Clients generally do not create arbitrary Alerts.

Alerts are created by server-side domain behavior such as:

- missing Trailer reconciliation;
- stale Trailer detector;
- reroute/operational exception when product policy calls for an Alert.

This prevents users from bypassing the domain by posting arbitrary Alert rows.

## #56 implemented boundary

Only existing `TRAILER_MISSING/WARNING` occurrences are implemented; future
stale detection and automatic resolution are not part of #56. List/detail require
ACTIVE Tenant/membership, `ALERT_VIEW` and related Trailer/Customer read scope.
Filters are closed and cursor/limit is bounded (1–100, default 50).

Writes require separate `ALERT_ACKNOWLEDGE` / `ALERT_RESOLVE` plus read scope.
Their role assignments remain unapproved and unassigned (deny-by-default), never
inferred from `ALERT_VIEW`. Optimistic `expectedVersion` conflicts, including
repeated stale writes, return 409 `STALE_VERSION`; invalid transitions return
409 `INVALID_STATE_TRANSITION`. The optional idempotent acknowledgement allowance
above is not used by this implementation. No mobile ledger or idempotency key is
required for these synchronous versioned Web commands. Resolution requires a
current active Trailer physically recorded AT_LOCATION under lock and a nonblank
reason (max 4000). It cannot repair the physical projection or reopen history.

Device registration accepts optional stable `deviceId`, nullable/omitted `fcmToken`
(max 4096, no whitespace/control characters), ANDROID/IOS and optional validated
`deviceName` (not retained). Up to 32 active registrations per User, including
reactivation; foreign IDs/platform changes reject safely. Optional proposed UUID
supports lost-ACK retry for the same owner; a new account needs a new registration.
Responses omit the token. Devices have no Tenant column and are not subscriptions.

The compiled recipient-policy registry has no production default while OQ-059 is
open. An approved internal producer may atomically create a recipient and enqueue
`ALERT_FCM` v1 through #55; there is no arbitrary client recipient API. The payload
contains recipientId only (Alert aggregate reference), not FCM tokens or text.
Worker diagnostics distinguish eligibility, no device, attempts, acceptance,
retry/permanent/partial failure and DEAD. Accepted by FCM never means delivered
or displayed. V24 Missing Alert persistence remains synchronous and unchanged.
See [implementation/configuration](../backend/issue-56-alerts-fcm-delivery.md).
