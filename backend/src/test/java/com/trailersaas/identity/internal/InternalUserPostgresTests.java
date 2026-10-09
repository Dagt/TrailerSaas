package com.trailersaas.identity.internal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.VerifiedFirebaseIdentity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InternalUserPostgresTests extends PostgresIntegrationTest {
    @Autowired InternalUserResolver resolver;
    @Autowired DataSource database;
    @Autowired Flyway flyway;

    @Test
    void schemaAndRuntimeGrantsAreMinimal() throws Exception {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            var columns = new ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='users' ORDER BY ordinal_position")) {
                while (rows.next()) { columns.add(rows.getString(1)); }
            }
            assertThat(columns).containsExactly("id", "firebase_uid", "email", "display_name", "created_at", "updated_at");
            try (var rows = statement.executeQuery("""
                    SELECT pg_get_userbyid(c.relowner), current_user,
                           has_table_privilege(current_user,'users','SELECT'),
                           has_table_privilege(current_user,'users','INSERT'),
                           has_table_privilege(current_user,'users','UPDATE'),
                           has_table_privilege(current_user,'users','DELETE'),
                           has_schema_privilege(current_user,'public','CREATE'),
                           r.rolsuper, r.rolbypassrls, c.relrowsecurity
                    FROM pg_class c JOIN pg_roles r ON r.rolname=current_user
                    WHERE c.oid='users'::regclass
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNotEqualTo(rows.getString(2));
                assertThat(rows.getString(2)).isEqualTo(RUNTIME_USERNAME);
                assertThat(rows.getBoolean(3)).isTrue();
                assertThat(rows.getBoolean(4)).isTrue();
                for (int column = 5; column <= 10; column++) { assertThat(rows.getBoolean(column)).isFalse(); }
            }
            assertThatThrownBy(() -> statement.execute("CREATE TABLE forbidden_runtime_table (id uuid)"))
                    .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
        }
    }

    @Test
    void uidIsUniqueAndEmailIsNotAnIdentityKey() throws Exception {
        String uid = "identity-" + UUID.randomUUID();
        var first = resolver.resolve(new VerifiedFirebaseIdentity(uid, "same@example.test", "First"));
        var again = resolver.resolve(new VerifiedFirebaseIdentity(uid, "changed@example.test", "Changed"));
        assertThat(again.userId()).isEqualTo(first.userId());
        assertThat(again.email()).isEqualTo(first.email()); // /me retains first snapshot.
        assertThat(again.displayName()).isEqualTo(first.displayName());
        assertThat(again.authenticatedEmail()).isEqualTo("changed@example.test");
        var other = resolver.resolve(new VerifiedFirebaseIdentity("identity-" + UUID.randomUUID(), "same@example.test", "Other"));
        assertThat(other.userId()).isNotEqualTo(first.userId());
        assertThat(other.email()).isEqualTo(first.email());
        try (var connection = database.getConnection();
                var insert = connection.prepareStatement("INSERT INTO users VALUES (?, ?, NULL, NULL, now(), now())")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, uid);
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
        }
    }

    @Test
    void simultaneousFirstLoginsAllResolveExactlyOneUser() throws Exception {
        String uid = "concurrent-" + UUID.randomUUID();
        var identity = new VerifiedFirebaseIdentity(uid, null, null);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<UUID>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Concurrent test timed out"); }
                    return resolver.resolve(identity).userId();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var ids = new ArrayList<UUID>();
            for (var future : futures) { ids.add(future.get(20, TimeUnit.SECONDS)); }
            assertThat(ids.stream().distinct()).hasSize(1);
        }
        try (var connection = database.getConnection();
                var query = connection.prepareStatement("SELECT count(*), bool_and(created_at=updated_at) FROM users WHERE firebase_uid=?")) {
            query.setString(1, uid);
            try (var rows = query.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getBoolean(2)).isTrue();
            }
        }
    }
}
