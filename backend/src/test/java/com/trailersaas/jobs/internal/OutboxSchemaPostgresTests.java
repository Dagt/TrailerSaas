package com.trailersaas.jobs.internal;

import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.TenantTransactionExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OutboxSchemaPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantTransactionExecutor transactions;
    EquipmentFixtures fixtures;
    UUID tenant, other;

    @BeforeEach void seed() throws Exception {
        fixtures = new EquipmentFixtures(flyway);
        tenant = fixtures.tenant(); other = fixtures.tenant();
    }

    @Test void migrationAndHistoricalChecksumsAreIntact() throws Exception {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("27");
        var hashes = new Properties();
        try (var stream = getClass().getResourceAsStream("/jobs/historical-migrations.sha256")) { hashes.load(stream); }
        assertThat(hashes).hasSize(24);
        for (var name : hashes.stringPropertyNames()) {
            try (var stream = getClass().getResourceAsStream("/db/migration/" + name)) {
                var canonical = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))))
                        .as(name).isEqualTo(hashes.getProperty(name));
            }
        }
        assertThat(fixtures.value("SELECT count(*) FROM pg_indexes WHERE tablename='outbox_jobs' AND indexname LIKE 'outbox_%'"))
                .isEqualTo(6L); // primary key and amendment Q's five indexes
        assertThat(fixtures.value("SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid='outbox_jobs'::regclass")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'outbox_jobs','DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'outbox_jobs','SELECT,INSERT,UPDATE')", Boolean.class)).isTrue();
    }

    UUID insert(UUID scope) throws Exception {
        var id = UUID.randomUUID();
        fixtures.execute("INSERT INTO outbox_jobs(id,tenant_id,job_type,payload,status,available_at,max_attempts) VALUES(?,?,'TEST_JOB','{\"schemaVersion\":1,\"data\":{}}','PENDING',clock_timestamp(),5)",id,scope);
        return id;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "'status', 'BOGUS'", "'max_attempts', '0'", "'max_attempts', '21'",
        "'attempt_count', '-1'", "'aggregate_type', 'TRAILER'",
        "'aggregate_id', '00000000-0000-4000-8000-000000000001'",
        "'locked_by', '00000000-0000-4000-8000-000000000001'",
        "'locked_until', '2030-01-01T00:00:00Z'", "'processed_at', '2026-01-01T00:00:00Z'",
        "'payload', '[]'::jsonb", "'payload', '{}'::jsonb", "'payload', '{\"schemaVersion\":0,\"data\":{}}'::jsonb",
        "'payload', '{\"schemaVersion\":1,\"data\":null}'::jsonb",
        "'payload', '{\"schemaVersion\":1,\"data\":{},\"secret\":1}'::jsonb",
        "'tenant_id', '00000000-0000-4000-8000-000000000001'"
    })
    void invalidRowsAreRejected(String override) {
        // Populate a typed PostgreSQL record so each candidate reaches the actual constraints.
        assertThatThrownBy(() -> fixtures.execute("""
            INSERT INTO outbox_jobs SELECT (jsonb_populate_record(NULL::outbox_jobs,
              jsonb_build_object('id',?::uuid,'tenant_id',?::uuid,'job_type','TEST_JOB',
                'payload',jsonb_build_object('schemaVersion',1,'data','{}'::jsonb),
                'status','PENDING','available_at',clock_timestamp(),'attempt_count',0,'max_attempts',5,
                'created_at',clock_timestamp(),'updated_at',clock_timestamp()) || jsonb_build_object(%s))).*
            """.formatted(override), UUID.randomUUID(), tenant)).isInstanceOf(java.sql.SQLException.class);
    }

    @Test void runtimeTenantIsolationAndGlobalDenialSurviveConnectionReuse() throws Exception {
        var a=insert(tenant); var b=insert(other); var global=insert(null);
        assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs", UUID.class)).isEmpty();
        transactions.execute(tenant, () -> {
            assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs",UUID.class)).contains(a).doesNotContain(b,global);
            assertThat(jdbc.update("UPDATE outbox_jobs SET status='SUCCEEDED' WHERE id=?",b)).isZero();
            return null;
        });
        assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs", UUID.class)).isEmpty();
        transactions.execute(other, () -> {
            assertThat(jdbc.queryForList("SELECT id FROM outbox_jobs",UUID.class)).contains(b).doesNotContain(a,global);
            return null;
        });
        assertThatThrownBy(() -> transactions.execute(tenant, () -> jdbc.update("INSERT INTO outbox_jobs(id,tenant_id,job_type,payload,status,available_at,max_attempts) VALUES(?,?,'TEST_JOB','{\"schemaVersion\":1,\"data\":{}}','PENDING',now(),5)", UUID.randomUUID(),other)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> transactions.execute(tenant, () -> jdbc.update("INSERT INTO outbox_jobs(id,job_type,payload,status,available_at,max_attempts) VALUES(?,'TEST_JOB','{\"schemaVersion\":1,\"data\":{}}','PENDING',now(),5)", UUID.randomUUID())))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
