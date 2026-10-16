package com.trailersaas.audit.internal;

import com.trailersaas.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AuditUpgradePostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @DynamicPropertySource static void baseline(DynamicPropertyRegistry r){r.add("spring.flyway.target",()->"29");}
    @Test void actualV29UpgradePreservesAllHistoricalRowsAndChecksums()throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("29");
        var f=new TrailerFixtures(flyway);var tenant=f.db.tenant();var user=f.db.user();f.db.membership(tenant,user,"DISABLED","TENANT_ADMIN");
        var trailer=f.trailer(tenant,"Audit-upgrade",f.customer(tenant));
        var before=f.db.value("select to_jsonb(t)::text from trailers t where id=?",trailer);
        var members=f.db.value("select jsonb_agg(to_jsonb(m) order by id)::text from tenant_memberships m where tenant_id=?",tenant);
        var checksums=f.db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success");
        var upgraded=Flyway.configure().configuration(flyway.getConfiguration()).target("30").load();upgraded.migrate();upgraded.validate();
        assertThat(f.db.value("select to_jsonb(t)::text from trailers t where id=?",trailer)).isEqualTo(before);
        assertThat(f.db.value("select jsonb_agg(to_jsonb(m) order by id)::text from tenant_memberships m where tenant_id=?",tenant)).isEqualTo(members);
        assertThat(f.db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success and version::int<=29")).isEqualTo(checksums);
        assertThat(f.db.value("select count(*) from audit_events")).isEqualTo(0L);
    }
    @Test void allTwentyNineHistoricalMigrationSourcesRemainUnchanged()throws Exception {
        var hashes=new Properties();try(var stream=getClass().getResourceAsStream("/audit/historical-migrations.sha256")){hashes.load(stream);}assertThat(hashes).hasSize(29);
        for(var name:hashes.stringPropertyNames())try(var stream=getClass().getResourceAsStream("/db/migration/"+name)){
            var text=new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))).as(name).isEqualTo(hashes.getProperty(name));
        }
    }
}
