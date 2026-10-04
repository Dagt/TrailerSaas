# Alerts, Notifications and Audit API v0.1

**Status:** Proposed

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

Server associates device with User/Tenant membership context.

Same FCM token registration should be safely upserted/rotated.

### DELETE /devices/{deviceId}

Deactivates/removes push registration for the authenticated User.

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
