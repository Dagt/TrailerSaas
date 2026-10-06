package com.trailersaas.identity.internal;

import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;

/** Test-only migrator seeds. Production runtime has no identity/tenancy write API. */
final class IdentityFixtures {
    private final Flyway flyway;
    IdentityFixtures(Flyway flyway) { this.flyway = flyway; }

    void execute(String sql, Object... values) throws SQLException {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) { statement.setObject(i + 1, values[i]); }
            statement.executeUpdate();
        }
    }

    UUID user() throws SQLException {
        var id = UUID.randomUUID();
        execute("INSERT INTO users VALUES (?, ?, NULL, NULL, now(), now())", id, "fixture-" + id);
        return id;
    }

    UUID tenant(String name) throws SQLException {
        var id = UUID.randomUUID();
        // Deliberately not a frozen Tenant status enum.
        execute("INSERT INTO tenants VALUES (?, ?, 'CANDIDATE_TEST_STATUS', now(), now())", id, name);
        return id;
    }

    UUID membership(UUID tenant, UUID user, String status) throws SQLException {
        var id = UUID.randomUUID();
        execute("INSERT INTO tenant_memberships VALUES (?, ?, ?, ?, now(), now())", id, tenant, user, status);
        return id;
    }

    void role(UUID tenant, UUID membership, String role, String scope, UUID customer) throws SQLException {
        execute("INSERT INTO role_assignments VALUES (?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), tenant, membership, role, scope, customer);
    }
}
