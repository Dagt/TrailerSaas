CREATE TABLE customers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    name varchar NOT NULL,
    status varchar NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name)
);

CREATE TABLE physical_sites (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    name varchar NOT NULL,
    address_line1 varchar NULL,
    address_line2 varchar NULL,
    city varchar NULL,
    state_region varchar NULL,
    postal_code varchar NULL,
    country_code varchar(2) NULL,
    timezone varchar NOT NULL,
    latitude numeric(9,6) NULL,
    longitude numeric(9,6) NULL,
    geofence_radius_m integer NULL,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);

CREATE TABLE customer_sites (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    site_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    FOREIGN KEY (tenant_id, site_id) REFERENCES physical_sites (tenant_id, id),
    UNIQUE (tenant_id, customer_id, site_id)
);

CREATE TABLE operational_locations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    site_id uuid NOT NULL,
    customer_id uuid NULL,
    type varchar NOT NULL CHECK (type IN ('BASE', 'YARD', 'DOCK', 'CUSTOMER_AREA', 'SHOP', 'OTHER')),
    code varchar NULL,
    name varchar NOT NULL,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, site_id) REFERENCES physical_sites (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, site_id, id)
);

ALTER TABLE role_assignments ADD CONSTRAINT role_assignments_customer_fk
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id);

CREATE INDEX customers_list_idx ON customers (tenant_id, name, id);
CREATE INDEX physical_sites_list_idx ON physical_sites (tenant_id, name, id);
CREATE INDEX customer_sites_site_idx ON customer_sites (tenant_id, site_id, customer_id);
CREATE INDEX operational_locations_list_idx ON operational_locations (tenant_id, site_id, name, id);
CREATE INDEX operational_locations_customer_idx ON operational_locations (tenant_id, customer_id, site_id);
CREATE INDEX operational_locations_active_association_idx
    ON operational_locations (tenant_id, customer_id, site_id) WHERE active;
CREATE INDEX role_assignments_customer_idx ON role_assignments (tenant_id, customer_id);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE physical_sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE operational_locations ENABLE ROW LEVEL SECURITY;

CREATE POLICY customer_tenant ON customers TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY site_tenant ON physical_sites TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY customer_site_tenant ON customer_sites TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY location_tenant ON operational_locations TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);

-- No bootstrap policies, Customer-level RLS, destructive deletes or ownership changes.
GRANT SELECT, INSERT, UPDATE ON TABLE customers, physical_sites, operational_locations TO "${runtimeRole}";
GRANT SELECT, INSERT, DELETE ON TABLE customer_sites TO "${runtimeRole}";
