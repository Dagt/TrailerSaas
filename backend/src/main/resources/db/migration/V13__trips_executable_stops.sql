CREATE TABLE trips (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    status varchar NOT NULL CHECK (status IN ('PLANNED','ASSIGNED','IN_TRANSIT','AT_STOP','CANCELLING','COMPLETED','CANCELLED')),
    current_driver_id uuid NULL,
    current_tractor_id uuid NULL,
    planned_initial_trailer_id uuid NULL,
    started_at timestamptz NULL,
    completed_at timestamptz NULL,
    cancelled_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,current_driver_id) REFERENCES drivers (tenant_id,id),
    FOREIGN KEY (tenant_id,current_tractor_id) REFERENCES tractors (tenant_id,id),
    FOREIGN KEY (tenant_id,planned_initial_trailer_id) REFERENCES trailers (tenant_id,id)
);
CREATE INDEX trips_status_created ON trips (tenant_id,status,created_at DESC);
CREATE INDEX trips_driver_status ON trips (tenant_id,current_driver_id,status);

CREATE TABLE trip_stops (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    sequence_number integer NOT NULL CHECK (sequence_number >= 1),
    stop_type varchar NOT NULL CHECK (stop_type IN ('BASE_DEPARTURE','PICKUP','DELIVERY','LIVE_LOAD','LIVE_UNLOAD','DROP_HOOK','BASE_RETURN','OTHER')),
    site_id uuid NULL,
    location_id uuid NULL,
    scheduled_at timestamptz NULL,
    status varchar NOT NULL CHECK (status IN ('PENDING','EN_ROUTE','ARRIVED','IN_SERVICE','EXCEPTION','COMPLETED','CANCELLED')),
    arrived_at timestamptz NULL,
    service_started_at timestamptz NULL,
    completed_at timestamptz NULL,
    departed_at timestamptz NULL,
    instructions text NULL,
    site_snapshot jsonb NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,trip_id,sequence_number),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips (tenant_id,id),
    FOREIGN KEY (tenant_id,site_id) REFERENCES physical_sites (tenant_id,id),
    FOREIGN KEY (tenant_id,site_id,location_id) REFERENCES operational_locations (tenant_id,site_id,id),
    CHECK (location_id IS NULL OR site_id IS NOT NULL),
    CHECK (site_id IS NOT NULL OR site_snapshot IS NOT NULL),
    CHECK (site_id IS NOT NULL OR location_id IS NULL)
);
CREATE INDEX trip_stops_status_scheduled ON trip_stops (tenant_id,status,scheduled_at);

CREATE TABLE trip_stop_orders (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_stop_id uuid NOT NULL,
    order_id uuid NOT NULL,
    order_stop_id uuid NULL,
    FOREIGN KEY (tenant_id,trip_stop_id) REFERENCES trip_stops (tenant_id,id),
    FOREIGN KEY (tenant_id,order_id) REFERENCES orders (tenant_id,id),
    FOREIGN KEY (tenant_id,order_id,order_stop_id) REFERENCES order_stops (tenant_id,order_id,id),
    UNIQUE NULLS NOT DISTINCT (tenant_id,trip_stop_id,order_id,order_stop_id)
);
CREATE INDEX trip_stop_orders_order ON trip_stop_orders (tenant_id,order_id);

ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_stops ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_stop_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY trips_tenant ON trips TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY trip_stops_tenant ON trip_stops TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY trip_stop_orders_tenant ON trip_stop_orders TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON trips TO "${runtimeRole}";
GRANT SELECT,INSERT ON trip_stops TO "${runtimeRole}";
GRANT SELECT,INSERT,DELETE ON trip_stop_orders TO "${runtimeRole}";
