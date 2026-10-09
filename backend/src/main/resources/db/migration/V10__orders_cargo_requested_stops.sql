CREATE TABLE orders (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    customer_id uuid NOT NULL,
    order_number varchar NOT NULL,
    po_number varchar NULL,
    shipment_number varchar NULL,
    bol_reference varchar NULL,
    status varchar NOT NULL CHECK (status IN ('OPEN', 'PLANNED', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'DELIVERED', 'COMPLETED', 'CANCELLING', 'CANCELLED')),
    created_by_user_id uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    delivered_at timestamptz NULL,
    completed_at timestamptz NULL,
    cancelled_at timestamptz NULL,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CHECK ((status IN ('DELIVERED', 'COMPLETED')) = (delivered_at IS NOT NULL)),
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

CREATE TABLE order_cargo_lines (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    order_id uuid NOT NULL,
    description varchar NOT NULL,
    quantity numeric NULL,
    pallet_count integer NULL,
    weight numeric NULL,
    weight_unit varchar NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id, order_id) REFERENCES orders (tenant_id, id)
);

-- V5 already supplies operational_locations UNIQUE (tenant_id, site_id, id).
CREATE TABLE order_stops (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    order_id uuid NOT NULL,
    sequence_number integer NOT NULL CHECK (sequence_number >= 1),
    site_id uuid NULL,
    location_id uuid NULL,
    stop_type varchar NOT NULL,
    scheduled_at timestamptz NULL,
    instructions text NULL,
    site_snapshot jsonb NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, order_id) REFERENCES orders (tenant_id, id),
    FOREIGN KEY (tenant_id, site_id) REFERENCES physical_sites (tenant_id, id),
    FOREIGN KEY (tenant_id, site_id, location_id) REFERENCES operational_locations (tenant_id, site_id, id),
    UNIQUE (tenant_id, order_id, sequence_number),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, order_id, id),
    CHECK (location_id IS NULL OR site_id IS NOT NULL),
    CHECK (site_id IS NOT NULL OR site_snapshot IS NOT NULL),
    CHECK (site_id IS NOT NULL OR location_id IS NULL),
    CHECK (site_snapshot IS NULL OR (jsonb_typeof(site_snapshot) = 'object' AND site_snapshot <> '{}'::jsonb))
);

CREATE INDEX orders_customer_number_idx ON orders (tenant_id, customer_id, order_number);
CREATE INDEX orders_status_created_idx ON orders (tenant_id, status, created_at DESC, id DESC);
CREATE INDEX orders_customer_status_idx ON orders (tenant_id, customer_id, status);
CREATE INDEX orders_page_idx ON orders (tenant_id, created_at DESC, id DESC);
CREATE INDEX order_cargo_parent_idx ON order_cargo_lines (tenant_id, order_id, created_at, id);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_cargo_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_stops ENABLE ROW LEVEL SECURITY;
CREATE POLICY orders_tenant ON orders TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY order_cargo_tenant ON order_cargo_lines TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY order_stops_tenant ON order_stops TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);

-- #31 only creates and reads. No UPDATE/DELETE or future command privileges.
GRANT SELECT, INSERT ON orders, order_cargo_lines, order_stops TO "${runtimeRole}";
