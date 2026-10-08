CREATE TABLE trailers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    operational_number varchar NOT NULL,
    operational_number_normalized varchar COLLATE "C" GENERATED ALWAYS AS (
        translate(
            regexp_replace(btrim(operational_number, ' ') COLLATE "C", ' +', ' ', 'g'),
            'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
        )
    ) STORED NOT NULL,
    designated_customer_id uuid NULL,
    trailer_type varchar NULL,
    capability_notes text NULL,
    location_state varchar NOT NULL CHECK (location_state IN ('AT_LOCATION', 'IN_TRANSIT', 'LOCATION_UNKNOWN')),
    current_location_id uuid NULL,
    last_known_location_id uuid NULL,
    last_seen_at timestamptz NULL,
    last_driver_id uuid NULL,
    load_state varchar NOT NULL CHECK (load_state IN ('EMPTY', 'LOADED', 'UNKNOWN')),
    serviceability_state varchar NOT NULL CHECK (serviceability_state IN ('IN_SERVICE', 'OUT_OF_SERVICE')),
    usage_purpose varchar NULL CHECK (usage_purpose IN ('DELIVERY', 'SHUTTLE', 'WIP_STORAGE', 'TOOLING_STORAGE', 'OTHER')),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, operational_number_normalized),
    FOREIGN KEY (tenant_id, designated_customer_id) REFERENCES customers (tenant_id, id),
    FOREIGN KEY (tenant_id, current_location_id) REFERENCES operational_locations (tenant_id, id),
    FOREIGN KEY (tenant_id, last_known_location_id) REFERENCES operational_locations (tenant_id, id),
    FOREIGN KEY (tenant_id, last_driver_id) REFERENCES drivers (tenant_id, id),
    CHECK (location_state <> 'AT_LOCATION' OR current_location_id IS NOT NULL),
    CHECK (location_state <> 'LOCATION_UNKNOWN' OR current_location_id IS NULL),
    CHECK (operational_number_normalized <> ''),
    CHECK (operational_number COLLATE "C" !~ U&'[\0001-\001F\007F-\009F]')
);

CREATE TABLE trailer_qr_identities (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trailer_id uuid NOT NULL,
    public_token varchar NOT NULL UNIQUE,
    active boolean NOT NULL,
    issued_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, trailer_id) REFERENCES trailers (tenant_id, id),
    CHECK ((active AND revoked_at IS NULL) OR (NOT active AND revoked_at IS NOT NULL)),
    CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);
CREATE UNIQUE INDEX trailer_qr_active_unique ON trailer_qr_identities (tenant_id, trailer_id) WHERE active;
CREATE INDEX trailer_qr_history_idx ON trailer_qr_identities (tenant_id, trailer_id, issued_at DESC, id);

ALTER TABLE trailers ENABLE ROW LEVEL SECURITY;
ALTER TABLE trailer_qr_identities ENABLE ROW LEVEL SECURITY;
CREATE POLICY trailer_tenant ON trailers TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY trailer_qr_tenant ON trailer_qr_identities TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
-- Resolution can identify one active QR, never mutate it or read the Trailer.
CREATE POLICY trailer_qr_lookup ON trailer_qr_identities FOR SELECT TO "${runtimeRole}"
    USING (public_token = NULLIF(current_setting('app.current_trailer_qr_token', true), '')
        AND active AND revoked_at IS NULL
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
GRANT SELECT, INSERT, UPDATE ON trailers, trailer_qr_identities TO "${runtimeRole}";
