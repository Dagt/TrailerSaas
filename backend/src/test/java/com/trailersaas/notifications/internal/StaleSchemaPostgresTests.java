package com.trailersaas.notifications.internal;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class StaleSchemaPostgresTests extends PostgresIntegrationTest {
 @Autowired Flyway flyway; @Autowired JdbcTemplate jdbc; @Autowired TenantTransactionExecutor transactions;
 @Test void freshV27IsScopedAndDoesNotAddAnotherQueue()throws Exception{
  flyway.validate();assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("27");
  var db=new EquipmentFixtures(flyway);var a=db.tenant();var b=db.tenant();
  transactions.execute(a,()->{jdbc.update("insert into stale_trailer_scans(tenant_id) values(?)",a);return null;});
  assertThat(transactions.execute(a,()->jdbc.queryForObject("select count(*) from stale_trailer_scans",Long.class))).isEqualTo(1L);
  assertThat(transactions.execute(b,()->jdbc.queryForObject("select count(*) from stale_trailer_scans",Long.class))).isZero();
  assertThat(jdbc.queryForObject("select count(*) from stale_trailer_scans",Long.class)).isZero();
  assertThat(jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",Boolean.class)).isFalse();
  assertThatThrownBy(()->transactions.execute(a,()->jdbc.update("insert into stale_trailer_scans(tenant_id) values(?)",b))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->transactions.execute(a,()->jdbc.update("delete from stale_trailer_scans"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(db.value("select count(*) from pg_indexes where indexname='alerts_unresolved_stale'")).isEqualTo(1L);
 }
 @Test void fabricatedSystemOccurrenceCannotBorrowMobileOrForeignJobIdentity()throws Exception{
  var f=new TrailerFixtures(flyway);var tenant=f.db.tenant();var trailer=f.trailer(tenant,"Stale-test-"+UUID.randomUUID(),f.customer(tenant));
  assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update("""
   insert into alerts(id,tenant_id,alert_type,severity,status,related_entity_type,related_entity_id,message_data,created_at,
    origin_actor_type,detector_job_id,detected_last_seen_at,detected_threshold_hours)
   values(?,?,'STALE_TRAILER','WARNING','OPEN','TRAILER',?,'{}',clock_timestamp(),'SYSTEM',?,clock_timestamp()-interval '73 hours',72)
   """,UUID.randomUUID(),tenant,trailer,UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(f.db.value("select count(*) from alerts where tenant_id=?",tenant)).isEqualTo(0L);
 }
 @Test void allTwentySixHistoricalMigrationsAreByteStableAndLockFunctionIsClosed()throws Exception{
  var hashes=new Properties();try(var stream=getClass().getResourceAsStream("/notifications/stale-historical-migrations.sha256")){hashes.load(stream);}assertThat(hashes).hasSize(26);
  for(var name:hashes.stringPropertyNames())try(var stream=getClass().getResourceAsStream("/db/migration/"+name)){
   var text=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n");assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).as(name).isEqualTo(hashes.getProperty(name));
  }
  assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'tenants','UPDATE')",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("select exists(select 1 from pg_proc, lateral aclexplode(proacl) acl where proname='lock_stale_tenant' and acl.grantee=0)",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("select lock_stale_tenant(?,?)",Boolean.class,UUID.randomUUID(),UUID.randomUUID())).isFalse();
 }
}
