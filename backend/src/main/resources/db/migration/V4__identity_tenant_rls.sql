-- Runtime is not the owner and cannot bypass RLS. Migrator ownership remains
-- separate for migrations/administrative fixtures; no runtime grants change.
ALTER TABLE tenant_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

CREATE POLICY membership_tenant ON tenant_memberships TO "${runtimeRole}"
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
    );

CREATE POLICY membership_bootstrap ON tenant_memberships FOR SELECT TO "${runtimeRole}"
    USING (
        user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
    );

CREATE POLICY role_tenant ON role_assignments TO "${runtimeRole}"
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
    );

CREATE POLICY role_bootstrap ON role_assignments FOR SELECT TO "${runtimeRole}"
    USING (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        AND EXISTS (
            SELECT 1 FROM tenant_memberships m
            WHERE m.tenant_id = role_assignments.tenant_id
                AND m.id = role_assignments.membership_id
                AND m.user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        )
    );

-- Tenants is global master data, but runtime SELECT must not enumerate it.
CREATE POLICY tenant_current ON tenants FOR SELECT TO "${runtimeRole}"
    USING (
        id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
    );

CREATE POLICY tenant_bootstrap ON tenants FOR SELECT TO "${runtimeRole}"
    USING (
        NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
        AND EXISTS (
            SELECT 1 FROM tenant_memberships m
            WHERE m.tenant_id = tenants.id
                AND m.user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        )
    );
