CREATE TABLE membership_invitations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    email varchar NOT NULL,
    email_normalized varchar NOT NULL CHECK (length(email_normalized) > 0),
    display_name varchar NULL,
    token_hash varchar UNIQUE NOT NULL,
    status varchar NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    expires_at timestamptz NOT NULL,
    accepted_by_user_id uuid NULL REFERENCES users (id),
    created_by_user_id uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL,
    accepted_at timestamptz NULL,
    UNIQUE (tenant_id, id)
);

-- Expiry is materialized transactionally before re-inviting. No clock predicate.
CREATE UNIQUE INDEX membership_invitations_pending_email_unique
    ON membership_invitations (tenant_id, email_normalized) WHERE status = 'PENDING';
CREATE INDEX membership_invitations_list_idx ON membership_invitations (tenant_id, created_at DESC, id);

CREATE TABLE invitation_role_assignments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    invitation_id uuid NOT NULL,
    role_code varchar NOT NULL CHECK (role_code IN (
        'TENANT_ADMIN', 'TRANSPORT_OPERATIONS', 'CUSTOMER_DISPATCHER', 'YARD_SPOTTER', 'DRIVER'
    )),
    scope_type varchar NOT NULL CHECK (scope_type IN ('TENANT', 'CUSTOMER')),
    customer_id uuid NULL,
    FOREIGN KEY (tenant_id, invitation_id) REFERENCES membership_invitations (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CHECK ((scope_type = 'TENANT' AND customer_id IS NULL)
        OR (scope_type = 'CUSTOMER' AND customer_id IS NOT NULL)),
    CHECK ((role_code = 'CUSTOMER_DISPATCHER' AND scope_type = 'CUSTOMER')
        OR (role_code IN ('TENANT_ADMIN', 'TRANSPORT_OPERATIONS', 'YARD_SPOTTER', 'DRIVER')
            AND scope_type = 'TENANT'))
);
CREATE UNIQUE INDEX invitation_roles_tenant_unique
    ON invitation_role_assignments (tenant_id, invitation_id, role_code) WHERE scope_type = 'TENANT';
CREATE UNIQUE INDEX invitation_roles_customer_unique
    ON invitation_role_assignments (tenant_id, invitation_id, role_code, customer_id) WHERE scope_type = 'CUSTOMER';
CREATE INDEX invitation_roles_parent_idx ON invitation_role_assignments (tenant_id, invitation_id);

GRANT SELECT, INSERT, UPDATE ON membership_invitations TO "${runtimeRole}";
GRANT SELECT, INSERT ON invitation_role_assignments TO "${runtimeRole}";
GRANT INSERT ON tenant_memberships, role_assignments TO "${runtimeRole}";

ALTER TABLE membership_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitation_role_assignments ENABLE ROW LEVEL SECURITY;

CREATE POLICY invitation_tenant ON membership_invitations TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);

-- Hash-only onboarding can SELECT/FOR UPDATE its one invitation, not INSERT.
-- Bootstrap /me remains excluded. After validation the service pins its Tenant.
CREATE POLICY invitation_acceptance_read ON membership_invitations FOR SELECT TO "${runtimeRole}"
    USING (token_hash = NULLIF(current_setting('app.current_invitation_hash', true), '')
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
        AND (NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
            OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid));
CREATE POLICY invitation_acceptance_update ON membership_invitations FOR UPDATE TO "${runtimeRole}"
    USING (token_hash = NULLIF(current_setting('app.current_invitation_hash', true), '')
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
        AND (NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
            OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (token_hash = NULLIF(current_setting('app.current_invitation_hash', true), '')
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL
        AND (NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
            OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid));

CREATE POLICY invitation_role_tenant ON invitation_role_assignments TO "${runtimeRole}"
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        AND NULLIF(current_setting('app.current_user_id', true), '') IS NULL);
CREATE POLICY invitation_role_acceptance ON invitation_role_assignments FOR SELECT TO "${runtimeRole}"
    USING (NULLIF(current_setting('app.current_user_id', true), '') IS NULL
        AND EXISTS (SELECT 1 FROM membership_invitations i
            WHERE i.tenant_id = invitation_role_assignments.tenant_id
                AND i.id = invitation_role_assignments.invitation_id
                AND i.token_hash = NULLIF(current_setting('app.current_invitation_hash', true), '')
                AND (NULLIF(current_setting('app.current_tenant_id', true), '') IS NULL
                    OR i.tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)));
