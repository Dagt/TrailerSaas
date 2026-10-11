-- Immutable execution facts, separate from dispatch instructions and physical Trailer facts.
CREATE TABLE trip_stop_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_stop_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    event_type varchar NOT NULL CHECK (event_type IN ('ACTIVATED','ARRIVAL_CONFIRMED','SERVICE_STARTED','EXCEPTION_RAISED','EXCEPTION_RESOLVED','COMPLETED','CANCELLED','DEPARTED')),
    actor_type varchar NOT NULL CHECK (actor_type IN ('HUMAN_USER','SYSTEM','INTEGRATION')),
    source_type varchar NOT NULL CHECK (source_type IN ('USER_ACTION','MOBILE_AUTOMATION','SYSTEM_PROCESS','EXTERNAL_INTEGRATION')),
    actor_user_id uuid NULL REFERENCES users(id),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    client_operation_id uuid NULL,
    metadata jsonb NULL CHECK (metadata IS NULL OR jsonb_typeof(metadata)='object'),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id,trip_stop_id) REFERENCES trip_stops(tenant_id,trip_id,id),
    CHECK (actor_type <> 'HUMAN_USER' OR actor_user_id IS NOT NULL)
);
CREATE INDEX trip_stop_execution_history ON trip_stop_events(tenant_id,trip_id,occurred_at,id);
ALTER TABLE trip_stop_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY trip_stop_execution_tenant ON trip_stop_events TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON trip_stop_events TO "${runtimeRole}";
