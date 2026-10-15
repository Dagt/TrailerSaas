package com.trailersaas.notifications.internal;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.util.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class NotificationSchemaPostgresTests extends PostgresIntegrationTest {
 @Autowired Flyway flyway;
 @Autowired JdbcTemplate jdbc;
 @Autowired TenantContext context;
 @Autowired TenantTransactionExecutor tenants;
 @Autowired PlatformTransactionManager manager;
 EquipmentFixtures db; UUID user,other,tenant;
 @BeforeEach void seed() throws Exception {db=new EquipmentFixtures(flyway);user=db.user();other=db.user();tenant=db.tenant();}
 <T>T as(UUID actor,Supplier<T> work){return new TransactionTemplate(manager).execute(s->{context.establishBootstrapUser(new AuthenticatedUserPrincipal(actor,"fixture-"+actor,null,null));return work.get();});}
 UUID register(UUID actor,UUID id,String token){return as(actor,()->jdbc.queryForObject("select register_user_device(?,'ANDROID',?)",UUID.class,id,token));}
 @Test void migrationFreshAndHistoricalHashesAndLeastPrivilege() throws Exception {
  flyway.validate();assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("28");
  var hashes=new Properties();try(var s=getClass().getResourceAsStream("/notifications/historical-migrations.sha256")){hashes.load(s);}
  assertThat(hashes).hasSize(25);
  for(var name:hashes.stringPropertyNames())try(var s=getClass().getResourceAsStream("/db/migration/"+name)){
   var text=new String(s.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n");
   assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).as(name).isEqualTo(hashes.getProperty(name));
  }
  assertThat(jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'user_devices','UPDATE')",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='user_devices' and column_name='tenant_id'",Long.class)).isZero();
  assertThat(db.value("select count(*) from pg_indexes where tablename in ('user_devices','alert_recipients','alert_deliveries')")).isEqualTo(12L);
 }
 @Test void registrationRotatesKeepsOwnerAndLogoutRetainsHistory() throws Exception {
  var id=register(user,null,"initial-token"); assertThat(register(user,id,"rotated-token")).isEqualTo(id);
  assertThat(db.value("select token_version from user_devices where id=?",id)).isEqualTo(1L);
  assertThat(as(user,()->jdbc.queryForObject("select deactivate_user_device(?)",Boolean.class,id))).isTrue();
  assertThat(db.value("select count(*) from user_devices where id=? and not active and fcm_token is null",id)).isEqualTo(1L);
  assertThatThrownBy(()->db.execute("update user_devices set user_id=? where id=?",other,id)).isInstanceOf(java.sql.SQLException.class);
  assertThatThrownBy(()->db.execute("delete from user_devices where id=?",id)).isInstanceOf(java.sql.SQLException.class);
 }
 @Test void tokenTransferCreatesNewRegistrationNeverRebindsOldOwner() throws Exception {
  var first=register(user,null,"reused-token");var second=register(other,null,"reused-token");assertThat(first).isNotEqualTo(second);
  assertThat(db.value("select user_id from user_devices where id=?",first)).isEqualTo(user);
  assertThat(db.value("select not active and fcm_token is null from user_devices where id=?",first)).isEqualTo(true);
  assertThat(db.value("select user_id from user_devices where fcm_token='reused-token'")).isEqualTo(other);
  assertThat(register(other,null,"reused-token")).isEqualTo(second);
 }
 @Test void nullTokenIsValidAndUserContextDoesNotLeakAcrossPooledTransactions(){
  var id=register(user,null,null);
  assertThat(as(user,()->jdbc.queryForList("select id from user_devices",UUID.class))).containsExactly(id);
  assertThat(as(other,()->jdbc.queryForList("select id from user_devices",UUID.class))).isEmpty();
  assertThat(jdbc.queryForList("select id from user_devices",UUID.class)).isEmpty();
  assertThat(tenants.execute(tenant,()->jdbc.queryForList("select id from user_devices",UUID.class))).isEmpty();
  assertThatThrownBy(()->tenants.execute(tenant,()->jdbc.queryForObject("select register_user_device(null,'IOS',null)",UUID.class))).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void foreignRegistrationCannotBeUpdatedOrDeactivated(){
  var id=register(user,null,"private-token");
  assertThatThrownBy(()->register(other,id,"other-token")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(as(other,()->jdbc.queryForObject("select deactivate_user_device(?)",Boolean.class,id))).isFalse();
 }
 @ParameterizedTest @ValueSource(strings={"", "bad token", "line\nbreak"}) void malformedTokenFailsClosed(String token){
  assertThatThrownBy(()->register(user,null,token)).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void concurrentTokenRegistrationHasOneActiveOwner() throws Exception {
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var a=pool.submit(()->register(user,UUID.randomUUID(),"racing-token"));var b=pool.submit(()->register(other,UUID.randomUUID(),"racing-token"));
   a.get();b.get();assertThat(db.value("select count(*) from user_devices where fcm_token='racing-token' and active")).isEqualTo(1L);
  }
 }
 @Test void reactivatingHistoricalRegistrationCannotExceedActiveDeviceBound(){
  var historical=register(user,null,null);
  as(user,()->jdbc.queryForObject("select deactivate_user_device(?)",Boolean.class,historical));
  UUID active=null;for(int i=0;i<32;i++)active=register(user,null,null);
  assertThatThrownBy(()->register(user,historical,"over-bound")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(register(user,active,"existing-active-rotation")).isEqualTo(active);
 }
 @Test void ordinaryTenantAndNoContextCannotUseDeliveryTokenLookup(){
  register(user,null,"hidden-token");
  assertThat(tenants.execute(tenant,()->jdbc.queryForList("select * from alert_delivery_devices(?,?)",UUID.randomUUID(),UUID.randomUUID()))).isEmpty();
  assertThat(jdbc.queryForList("select * from alert_delivery_devices(?,?)",UUID.randomUUID(),UUID.randomUUID())).isEmpty();
 }
}
