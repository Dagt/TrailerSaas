CREATE TABLE tenant_settings (
    tenant_id uuid PRIMARY KEY REFERENCES tenants (id),
    stale_trailer_threshold_hours integer NOT NULL,
    default_timezone varchar NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CHECK (stale_trailer_threshold_hours > 0)
);

-- Discovery's three-day initial value is configuration, not a universal domain rule.
INSERT INTO tenant_settings (tenant_id, stale_trailer_threshold_hours, default_timezone, created_at, updated_at, version)
SELECT id, 72, NULL, now(), now(), 0 FROM tenants;

ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_settings_tenant ON tenant_settings TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
GRANT SELECT, UPDATE ON tenant_settings TO "${runtimeRole}";
