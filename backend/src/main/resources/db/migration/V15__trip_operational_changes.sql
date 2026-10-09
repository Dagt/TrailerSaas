-- Bounded Trip domain history and authoritative Driver instruction projection; no outbox/Audit.
ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_trip_identity UNIQUE (tenant_id,trip_id,id);
CREATE TABLE trip_operational_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    trip_stop_id uuid NULL,
    event_type varchar NOT NULL CHECK (event_type IN ('TRIP_REROUTED','TRIP_CANCELLED','TRIP_CANCELLATION_STARTED','TRIP_CANCELLATION_FINISHED')),
    actor_user_id uuid NULL REFERENCES users(id),
    target_driver_id uuid NULL,
    occurred_at timestamptz NOT NULL,
    reason text NULL,
    metadata jsonb NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id,trip_stop_id) REFERENCES trip_stops(tenant_id,trip_id,id),
    FOREIGN KEY (tenant_id,target_driver_id) REFERENCES drivers(tenant_id,id),
    CHECK ((event_type='TRIP_REROUTED' AND trip_stop_id IS NOT NULL)
        OR (event_type<>'TRIP_REROUTED' AND trip_stop_id IS NULL))
);
CREATE INDEX trip_operations_chronological ON trip_operational_events(tenant_id,trip_id,occurred_at,id);
CREATE TABLE trip_driver_updates (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    update_type varchar NOT NULL CHECK (update_type IN ('TRIP_REROUTED','TRIP_CANCELLATION_STARTED','TRIP_CANCELLED','TRIP_CANCELLATION_FINISHED','TRIP_ASSIGNMENT_CHANGED','TRAILER_EXPECTATION_CHANGED')),
    occurred_at timestamptz NOT NULL,
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload)='object'),
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id)
);
CREATE INDEX trip_driver_updates_incremental ON trip_driver_updates(tenant_id,driver_id,created_at,id);
ALTER TABLE trip_operational_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY trip_operations_tenant ON trip_operational_events TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
ALTER TABLE trip_driver_updates ENABLE ROW LEVEL SECURITY;
CREATE POLICY trip_driver_updates_tenant ON trip_driver_updates TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON trip_operational_events,trip_driver_updates TO "${runtimeRole}";
GRANT UPDATE ON trip_stops TO "${runtimeRole}";
-- Projection starts at V15. No synthetic historical Driver delivery/backfill.
