CREATE TABLE load_manifests (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trailer_id uuid NULL,
    status varchar NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    closed_at timestamptz NULL,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, trailer_id) REFERENCES trailers (tenant_id, id),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE TABLE manifest_orders (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    manifest_id uuid NOT NULL,
    order_id uuid NOT NULL,
    allocated_quantity numeric NULL CHECK (allocated_quantity >= 0),
    allocated_pallet_count integer NULL CHECK (allocated_pallet_count >= 0),
    allocated_weight numeric NULL CHECK (allocated_weight >= 0),
    created_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id, manifest_id) REFERENCES load_manifests (tenant_id, id),
    FOREIGN KEY (tenant_id, order_id) REFERENCES orders (tenant_id, id),
    UNIQUE (tenant_id, manifest_id, order_id)
);

ALTER TABLE load_manifests ENABLE ROW LEVEL SECURITY;
ALTER TABLE manifest_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY load_manifests_tenant ON load_manifests TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY manifest_orders_tenant ON manifest_orders TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);

GRANT SELECT, INSERT, UPDATE ON load_manifests TO "${runtimeRole}";
GRANT SELECT, INSERT, DELETE ON manifest_orders TO "${runtimeRole}";
