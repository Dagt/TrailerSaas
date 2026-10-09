-- Physical coupling is independent of planning expectation and Trip lifecycle.
CREATE TABLE trailer_movement_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    trailer_id uuid NOT NULL,
    trip_id uuid NULL,
    trip_stop_id uuid NULL,
    driver_id uuid NULL,
    tractor_id uuid NULL,
    location_id uuid NULL,
    event_type varchar NOT NULL CHECK (event_type IN ('HOOKED','DROPPED','DEPARTED','ARRIVED','YARD_MOVED','LOCATION_CORRECTED','LOCATION_MARKED_UNKNOWN','LOCATION_RECOVERED','LOAD_STATE_CHANGED','MARKED_OOS','RETURNED_TO_SERVICE','DESIGNATED_CUSTOMER_CHANGED')),
    actor_type varchar NOT NULL CHECK (actor_type IN ('HUMAN_USER','SYSTEM','INTEGRATION')),
    source_type varchar NOT NULL CHECK (source_type IN ('USER_ACTION','MOBILE_AUTOMATION','SYSTEM_PROCESS','EXTERNAL_INTEGRATION')),
    actor_user_id uuid NULL REFERENCES users(id),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    client_operation_id uuid NULL,
    reason text NULL,
    metadata jsonb NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_stop_id) REFERENCES trip_stops(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id,trip_stop_id) REFERENCES trip_stops(tenant_id,trip_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    FOREIGN KEY (tenant_id,tractor_id) REFERENCES tractors(tenant_id,id),
    FOREIGN KEY (tenant_id,location_id) REFERENCES operational_locations(tenant_id,id),
    CHECK (trip_stop_id IS NULL OR trip_id IS NOT NULL),
    CHECK (actor_type <> 'HUMAN_USER' OR actor_user_id IS NOT NULL)
);
CREATE INDEX trailer_movements_history ON trailer_movement_events(tenant_id,trailer_id,occurred_at DESC,id DESC);
CREATE INDEX trailer_movements_trip ON trailer_movement_events(tenant_id,trip_id,occurred_at,id);
CREATE UNIQUE INDEX trailer_movements_operation ON trailer_movement_events(tenant_id,client_operation_id) WHERE client_operation_id IS NOT NULL;

CREATE TABLE trailer_couplings (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    trailer_id uuid NOT NULL,
    tractor_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    trip_id uuid NULL,
    hooked_at timestamptz NOT NULL,
    dropped_at timestamptz NULL,
    hook_event_id uuid NULL,
    drop_event_id uuid NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
    FOREIGN KEY (tenant_id,tractor_id) REFERENCES tractors(tenant_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,hook_event_id) REFERENCES trailer_movement_events(tenant_id,id),
    FOREIGN KEY (tenant_id,drop_event_id) REFERENCES trailer_movement_events(tenant_id,id),
    CHECK (hook_event_id IS NOT NULL),
    CHECK (dropped_at IS NULL OR dropped_at >= hooked_at),
    CHECK ((dropped_at IS NULL AND drop_event_id IS NULL) OR (dropped_at IS NOT NULL AND drop_event_id IS NOT NULL))
);
CREATE UNIQUE INDEX trailer_couplings_active_trailer ON trailer_couplings(tenant_id,trailer_id) WHERE dropped_at IS NULL;
CREATE UNIQUE INDEX trailer_couplings_active_tractor ON trailer_couplings(tenant_id,tractor_id) WHERE dropped_at IS NULL;
-- Actual query: locked Trip's open coupling, including the Drop -> Hook semantic guard.
CREATE INDEX trailer_couplings_active_trip ON trailer_couplings(tenant_id,trip_id) WHERE dropped_at IS NULL;

-- Invoker trigger: runtime can close an interval once, never rewrite or reopen history.
CREATE FUNCTION guard_trailer_coupling_close() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.dropped_at IS NOT NULL
       OR NEW.dropped_at IS NULL
       OR (to_jsonb(NEW) - 'dropped_at' - 'drop_event_id') IS DISTINCT FROM
          (to_jsonb(OLD) - 'dropped_at' - 'drop_event_id') THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='Invalid coupling interval change';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION guard_trailer_coupling_close() FROM PUBLIC;
CREATE TRIGGER trailer_coupling_close_only BEFORE UPDATE ON trailer_couplings
    FOR EACH ROW EXECUTE FUNCTION guard_trailer_coupling_close();

CREATE TABLE processed_client_operations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    client_operation_id uuid NOT NULL,
    user_id uuid NOT NULL REFERENCES users(id),
    device_id uuid NULL,
    operation_type varchar NOT NULL,
    request_hash varchar NULL,
    status varchar NOT NULL CHECK (status IN ('SUCCEEDED','REJECTED')),
    processed_at timestamptz NOT NULL,
    result_entity_type varchar NULL,
    result_entity_id uuid NULL,
    response_data jsonb NULL,
    UNIQUE (tenant_id,client_operation_id)
);
-- device_id is reserved and NULL in #38; device registration belongs to #56.
ALTER TABLE trailer_movement_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE trailer_couplings ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_client_operations ENABLE ROW LEVEL SECURITY;
CREATE POLICY trailer_movements_tenant ON trailer_movement_events TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY trailer_couplings_tenant ON trailer_couplings TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY processed_operations_tenant ON processed_client_operations TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON trailer_movement_events,processed_client_operations TO "${runtimeRole}";
GRANT SELECT,INSERT ON trailer_couplings TO "${runtimeRole}";
GRANT UPDATE(dropped_at,drop_event_id) ON trailer_couplings TO "${runtimeRole}";
