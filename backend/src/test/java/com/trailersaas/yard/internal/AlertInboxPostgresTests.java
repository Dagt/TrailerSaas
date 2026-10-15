package com.trailersaas.yard.internal;

import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AlertInboxPostgresTests extends YardCheckFixture {
 UUID alert;
 @BeforeEach void missingAlert()throws Exception {
  var c=check(start());assertThat(command("/yard-checks/"+c+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0)).statusCode()).isEqualTo(200);
  var item=JsonPath.<String>read(get("/yard-checks/"+c).body(),"$.items[0].id");
  var r=command("/yard-checks/"+c+"/items/"+item+"/mark-location-unknown",UUID.randomUUID().toString(),Map.of("expectedTrailerVersion",0,"reason","Verified missing"));
  assertThat(r.statusCode()).as(r.body()).isEqualTo(200);alert=UUID.fromString(JsonPath.read(r.body(),"$.alertId"));f.role(tenant,membership,"TENANT_ADMIN",null);
 }
 @Test void listAndDetailExposeOnlyAuthorizedProjectionAndPreserveAlertWithoutPush()throws Exception{
  var r=get("/alerts?status=OPEN&alertType=TRAILER_MISSING&severity=WARNING&relatedEntityType=TRAILER&limit=1");assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
  assertThat(JsonPath.<String>read(r.body(),"$.items[0].id")).isEqualTo(alert.toString());assertThat(JsonPath.<Object>read(r.body(),"$.nextCursor")).isNull();
  var detail=get("/alerts/"+alert);assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);assertThat(JsonPath.<String>read(detail.body(),"$.relatedResource.id")).isEqualTo(trailer.toString());
  assertThat(detail.body()).doesNotContain("fcmToken","firebase_uid","clientOperationId","actorUserId");assertThat(f.db.value("select count(*) from alert_recipients where tenant_id=?",tenant)).isEqualTo(0L);
 }
 @ParameterizedTest @ValueSource(strings={"status=BOGUS","severity=CRITICAL","limit=0","limit=101","cursor=%25%25%25","cursor=Zg","unknown=value","from=bad","from=2030-01-01T00:00:00Z&to=2020-01-01T00:00:00Z"})
 void invalidFiltersAreRejected(String query)throws Exception{problem(get("/alerts?"+query),400,"VALIDATION_FAILED");}
 @ParameterizedTest @ValueSource(strings={"YARD_SPOTTER","DRIVER","CUSTOMER_DISPATCHER"})
 void unapprovedRolesCannotBrowseOrInferAlerts(String role)throws Exception{
  f.db.execute("delete from role_assignments where membership_id=?",membership);
  UUID scoped=role.equals("CUSTOMER_DISPATCHER")?customer:null;f.role(tenant,membership,role,scoped);
  assertThat(get("/alerts").statusCode()).isEqualTo(403);assertThat(get("/alerts/"+alert).statusCode()).isEqualTo(403);
 }
 @Test void operationsHasViewButNoImplicitWriteAuthority()throws Exception{
  f.db.execute("delete from role_assignments where membership_id=?",membership);f.role(tenant,membership,"TRANSPORT_OPERATIONS",null);
  assertThat(get("/alerts").statusCode()).isEqualTo(200);
  // No write grant has yet been approved; review is not inferred from ALERT_VIEW.
  assertThat(command("/alerts/"+alert+"/acknowledgement",null,Map.of("expectedVersion",0)).statusCode()).isEqualTo(403);
 }
 @Test void revokedMembershipAndMissingResourceDoNotLeak()throws Exception{
  problem(get("/alerts/"+UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");f.db.execute("update tenant_memberships set status='DISABLED' where id=?",membership);
  assertThat(get("/alerts").statusCode()).isEqualTo(403);assertThat(get("/alerts/"+alert).statusCode()).isEqualTo(403);
 }
 @Test void sqlLifecyclePreservesFactsRequiresRecoveredProjectionAndIsTerminal()throws Exception{
  var facts=f.db.value("select message_data::text from alerts where id=?",alert);var created=f.db.value("select created_at from alerts where id=?",alert);
  f.db.execute("update alerts set status='ACKNOWLEDGED',acknowledged_by=?,version=1 where id=?",user,alert);
  var acknowledged=f.db.value("select acknowledged_at from alerts where id=?",alert);
  assertThatThrownBy(()->f.db.execute("update alerts set status='RESOLVED',resolved_by=?,resolution_reason='Claimed recovery',version=2 where id=?",user,alert)).isInstanceOf(java.sql.SQLException.class);
  assertThat(f.db.value("select location_state from trailers where id=?",trailer)).isEqualTo("LOCATION_UNKNOWN");
  var corrected=command("/trailers/"+trailer+"/location-corrections",null,Map.of("expectedVersion",1,"newLocationId",a.toString(),"reason","Physically recovered and confirmed"));assertThat(corrected.statusCode()).as(corrected.body()).isEqualTo(200);
  f.db.execute("update alerts set status='RESOLVED',resolved_by=?,resolution_reason='Recovered',version=2 where id=?",user,alert);
  assertThat(f.db.value("select status from alerts where id=?",alert)).isEqualTo("RESOLVED");assertThat(f.db.value("select acknowledged_at from alerts where id=?",alert)).isEqualTo(acknowledged);
  assertThat(f.db.value("select message_data::text from alerts where id=?",alert)).isEqualTo(facts);assertThat(f.db.value("select created_at from alerts where id=?",alert)).isEqualTo(created);
  assertThatThrownBy(()->f.db.execute("update alerts set status='OPEN',version=3 where id=?",alert)).isInstanceOf(java.sql.SQLException.class);
  assertThatThrownBy(()->f.db.execute("delete from alerts where id=?",alert)).isInstanceOf(java.sql.SQLException.class);
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void concurrentVersionedLifecycleWritesHaveOnlyOneWinnerUnderRuntimeRls(boolean resolve)throws Exception{
  if(resolve){var r=command("/trailers/"+trailer+"/location-corrections",null,Map.of("expectedVersion",1,"newLocationId",a.toString(),"reason","Verified recovery before resolution"));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);}
  String sql=resolve?"update alerts set status='RESOLVED',resolved_by=?,resolution_reason='Verified recovered',version=1 where tenant_id=? and id=? and version=0":"update alerts set status='ACKNOWLEDGED',acknowledged_by=?,version=1 where tenant_id=? and id=? and version=0";
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var one=pool.submit(()->transactions.execute(tenant,()->jdbc.update(sql,user,tenant,alert)));
   var two=pool.submit(()->transactions.execute(tenant,()->jdbc.update(sql,user,tenant,alert)));
   assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(0,1);
  }
  assertThat(f.db.value("select version from alerts where id=?",alert)).isEqualTo(1L);
  assertThat(f.db.value("select status from alerts where id=?",alert)).isEqualTo(resolve?"RESOLVED":"ACKNOWLEDGED");
 }
 @Test void paginationNeverMixesExpectedFiltersOrDuplicatesOccurrences()throws Exception{
  for(int n=0;n<2;n++){var t=f.trailer(tenant,"Finding-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",a,t);}
  var c=check(start());assertThat(command("/yard-checks/"+c+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0)).statusCode()).isEqualTo(200);
  var items=JsonPath.<List<Map<String,Object>>>read(get("/yard-checks/"+c).body(),"$.items");
  for(var i:items){var r=command("/yard-checks/"+c+"/items/"+i.get("id")+"/mark-location-unknown",UUID.randomUUID().toString(),Map.of("expectedTrailerVersion",0,"reason","Explicit second occurrence"));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);}
  var first=get("/alerts?limit=2");assertThat(first.statusCode()).isEqualTo(200);String cursor=JsonPath.read(first.body(),"$.nextCursor");assertThat(cursor).isNotBlank();
  var second=get("/alerts?limit=2&cursor="+cursor);assertThat(second.statusCode()).isEqualTo(200);
  var ids=new HashSet<>(JsonPath.<List<String>>read(first.body(),"$.items[*].id"));ids.addAll(JsonPath.read(second.body(),"$.items[*].id"));assertThat(ids).hasSize(3);
  assertThat(JsonPath.<Object>read(second.body(),"$.nextCursor")).isNull();
 }
}
