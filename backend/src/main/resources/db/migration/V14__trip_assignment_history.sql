CREATE TABLE trip_assignment_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    event_type varchar NOT NULL CHECK (event_type IN (
        'DRIVER_ASSIGNED','DRIVER_REASSIGNED','TRACTOR_ASSIGNED','TRACTOR_REASSIGNED',
        'TRAILER_PLANNED','TRAILER_SUBSTITUTION_AUTHORIZED','TRAILER_SUBSTITUTION_REJECTED')),
    driver_id uuid NULL,
    tractor_id uuid NULL,
    trailer_id uuid NULL,
    actor_user_id uuid NULL REFERENCES users(id),
    occurred_at timestamptz NOT NULL,
    reason text NULL,
    metadata jsonb NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips (tenant_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers (tenant_id,id),
    FOREIGN KEY (tenant_id,tractor_id) REFERENCES tractors (tenant_id,id),
    FOREIGN KEY (tenant_id,trailer_id) REFERENCES trailers (tenant_id,id),
    CHECK (
        (event_type IN ('DRIVER_ASSIGNED','DRIVER_REASSIGNED')
            AND driver_id IS NOT NULL AND tractor_id IS NULL AND trailer_id IS NULL)
        OR (event_type IN ('TRACTOR_ASSIGNED','TRACTOR_REASSIGNED')
            AND tractor_id IS NOT NULL AND driver_id IS NULL AND trailer_id IS NULL)
        OR (event_type IN ('TRAILER_PLANNED','TRAILER_SUBSTITUTION_AUTHORIZED','TRAILER_SUBSTITUTION_REJECTED')
            AND trailer_id IS NOT NULL AND driver_id IS NULL AND tractor_id IS NULL)
    )
);
CREATE INDEX trip_assignments_chronological ON trip_assignment_events (tenant_id,trip_id,occurred_at,id);
ALTER TABLE trip_assignment_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY trip_assignments_tenant ON trip_assignment_events TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON trip_assignment_events TO "${runtimeRole}";
-- Pre-V14 projections remain authoritative; no synthetic actor/history backfill.
