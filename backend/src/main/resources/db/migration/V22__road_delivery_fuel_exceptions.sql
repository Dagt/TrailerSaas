CREATE TABLE fuel_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    tractor_id uuid NOT NULL,
    trip_id uuid NULL,
    odometer numeric NOT NULL CHECK (odometer>=0 AND odometer<=1000000000000 AND scale(odometer)<=3),
    receipt_document_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,receipt_document_id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    FOREIGN KEY (tenant_id,tractor_id) REFERENCES tractors(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,receipt_document_id) REFERENCES documents(tenant_id,id),
    CHECK (occurred_at<=created_at)
);
CREATE INDEX fuel_events_driver_history ON fuel_events(tenant_id,driver_id,occurred_at DESC,id DESC);

-- Exact parent identity, not merely two independently valid Tenant resources.
ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_tenant_trip_id_unique UNIQUE (tenant_id,trip_id,id);
CREATE TABLE operational_exceptions (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    trip_stop_id uuid NULL,
    driver_id uuid NOT NULL,
    actor_user_id uuid NOT NULL REFERENCES users(id),
    category varchar NOT NULL CHECK (category IN ('DELAY_TRAFFIC','EQUIPMENT_DAMAGE','DESTINATION_CUSTOMER_ISSUE','LOAD_REJECTED','OTHER')),
    comment text NOT NULL CHECK (length(comment)<=4000 AND comment ~ '[^[:space:]]'),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    client_operation_id uuid NOT NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,client_operation_id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_id,trip_stop_id) REFERENCES trip_stops(tenant_id,trip_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    CHECK (occurred_at<=recorded_at)
);
CREATE INDEX operational_exceptions_trip_history ON operational_exceptions(tenant_id,trip_id,recorded_at DESC,id DESC);

CREATE TABLE operational_exception_documents (
    tenant_id uuid NOT NULL,
    exception_id uuid NOT NULL,
    document_id uuid NOT NULL,
    PRIMARY KEY (tenant_id,exception_id,document_id),
    FOREIGN KEY (tenant_id,exception_id) REFERENCES operational_exceptions(tenant_id,id),
    FOREIGN KEY (tenant_id,document_id) REFERENCES documents(tenant_id,id)
);

CREATE TABLE order_fulfillment_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    order_id uuid NOT NULL,
    trip_stop_id uuid NOT NULL,
    previous_state varchar NOT NULL CHECK (previous_state IN ('PLANNED','IN_PROGRESS','PARTIALLY_DELIVERED','DELIVERED')),
    new_state varchar NOT NULL CHECK (new_state IN ('IN_PROGRESS','PARTIALLY_DELIVERED','DELIVERED')),
    actor_user_id uuid NOT NULL REFERENCES users(id),
    client_operation_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE (tenant_id,order_id,trip_stop_id),
    FOREIGN KEY (tenant_id,order_id) REFERENCES orders(tenant_id,id),
    FOREIGN KEY (tenant_id,trip_stop_id) REFERENCES trip_stops(tenant_id,id),
    CHECK (occurred_at<=recorded_at)
);

ALTER TABLE fuel_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE operational_exceptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE operational_exception_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_fulfillment_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY fuel_events_tenant ON fuel_events TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY operational_exceptions_tenant ON operational_exceptions TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY operational_exception_documents_tenant ON operational_exception_documents TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY order_fulfillment_events_tenant ON order_fulfillment_events TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON fuel_events,operational_exceptions,operational_exception_documents,order_fulfillment_events TO "${runtimeRole}";
