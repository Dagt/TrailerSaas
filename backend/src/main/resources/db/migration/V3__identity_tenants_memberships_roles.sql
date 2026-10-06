CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    name varchar NOT NULL,
    status varchar NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- Tenant status remains candidate vocabulary; no closed CHECK until accepted.
CREATE TABLE tenant_memberships (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NOT NULL REFERENCES users (id),
    status varchar NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, user_id),
    UNIQUE (tenant_id, id)
);

-- Authenticated User bootstrap directory used by /me, not a tenant-wide browse.
CREATE INDEX tenant_memberships_user_idx ON tenant_memberships (user_id);

CREATE TABLE role_assignments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role_code varchar NOT NULL CHECK (role_code IN (
        'TENANT_ADMIN', 'TRANSPORT_OPERATIONS', 'CUSTOMER_DISPATCHER', 'YARD_SPOTTER', 'DRIVER'
    )),
    scope_type varchar NOT NULL CHECK (scope_type IN ('TENANT', 'CUSTOMER')),
    customer_id uuid NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id, membership_id) REFERENCES tenant_memberships (tenant_id, id),
    CONSTRAINT role_assignments_scope_customer_check CHECK (
        (scope_type = 'TENANT' AND customer_id IS NULL)
        OR (scope_type = 'CUSTOMER' AND customer_id IS NOT NULL)
    ),
    CONSTRAINT role_assignments_role_scope_check CHECK (
        (role_code = 'CUSTOMER_DISPATCHER' AND scope_type = 'CUSTOMER')
        OR (role_code IN ('TENANT_ADMIN', 'TRANSPORT_OPERATIONS', 'YARD_SPOTTER', 'DRIVER')
            AND scope_type = 'TENANT')
    )
);

CREATE UNIQUE INDEX role_assignments_tenant_role_unique
    ON role_assignments (tenant_id, membership_id, role_code) WHERE scope_type = 'TENANT';
CREATE UNIQUE INDEX role_assignments_customer_role_unique
    ON role_assignments (tenant_id, membership_id, role_code, customer_id) WHERE scope_type = 'CUSTOMER';

-- /me joins all scopes; the partial uniqueness indexes cannot serve that
-- unfiltered membership join on their own.
CREATE INDEX role_assignments_membership_idx ON role_assignments (tenant_id, membership_id);

-- #27 adds FK (tenant_id, customer_id) once customers exists. No production
-- assignment commands are exposed before that FK. No destructive cascades.
-- Reuse the validated, quoted identifier from DB_RUNTIME_USERNAME (#22).
GRANT SELECT ON TABLE tenants, tenant_memberships, role_assignments TO "${runtimeRole}";
