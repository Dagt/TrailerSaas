CREATE TABLE drivers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NULL REFERENCES users (id),
    name varchar NOT NULL,
    license_number varchar NULL,
    medical_certificate_expires date NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX drivers_user_unique ON drivers (tenant_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX drivers_list_idx ON drivers (tenant_id, name, id);

CREATE TABLE tractors (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    operational_number varchar NOT NULL,
    operational_number_normalized varchar COLLATE "C" GENERATED ALWAYS AS (
        translate(
            regexp_replace(btrim(operational_number, ' ') COLLATE "C", ' +', ' ', 'g'),
            'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
        )
    ) STORED NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, operational_number_normalized),
    CHECK (operational_number_normalized <> ''),
    -- C0/C1 controls are rejected, never normalized. PostgreSQL text also rejects NUL.
    CHECK (operational_number COLLATE "C" !~ U&'[\0001-\001F\007F-\009F]')
);

ALTER TABLE drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE tractors ENABLE ROW LEVEL SECURITY;
CREATE POLICY driver_tenant ON drivers TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY tractor_tenant ON tractors TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
GRANT SELECT, INSERT, UPDATE ON drivers, tractors TO "${runtimeRole}";
