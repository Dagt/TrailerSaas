package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YardCheckPostgresTests extends YardCheckFixture {
  @Test void startsRealSnapshotWithoutPhysicalMutation() throws Exception {
    var r=start();check(r);assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isEqualTo(1);
    assertThat(JsonPath.<String>read(r.body(),"$.items[0].expectedLocationId")).isEqualTo(a.toString());
    assertThat(JsonPath.<Object>read(r.body(),"$.items[0].result")).isNull();
    assertThat(JsonPath.<String>read(r.body(),"$.items[0].recordedSnapshot.operationalNumber")).startsWith("Yard-");
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
  }
  @Test void startReplayReturnsOriginalReceiptAndNewKeyAllowsIndependentCheck() throws Exception {
    var key=UUID.randomUUID().toString();var r=command("/yard-checks",key,startBody(null));var id=check(r);
    f.db.execute("update trailers set current_location_id=?,version=version+1 where id=?",b,trailer);
    assertThat(command("/yard-checks",key,startBody(null)).body()).isEqualTo(r.body());
    assertThat(check(start())).isNotEqualTo(id);
  }
  @Test void originalSnapshotDoesNotFollowCurrentMovementOrMasterName() throws Exception {
    var id=check(start());f.db.execute("update trailers set current_location_id=?,version=version+1 where id=?",b,trailer);
    f.db.execute("update operational_locations set name='Changed' where id=?",a);
    var r=get("/yard-checks/"+id);
    assertThat(JsonPath.<String>read(r.body(),"$.items[0].expectedLocationId")).isEqualTo(a.toString());
    assertThat(JsonPath.<String>read(r.body(),"$.items[0].recordedSnapshot.locationName")).isNotEqualTo("Changed");
    assertThat(JsonPath.<Integer>read(r.body(),"$.items[0].recordedSnapshot.trailerVersion")).isZero();
  }
  @Test void emptySnapshotIsComplete() throws Exception {
    var r=command("/yard-checks",UUID.randomUUID().toString(),startBody(b));check(r);
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isZero();
    assertThat(JsonPath.<Boolean>read(r.body(),"$.snapshotComplete")).isTrue();
  }
  @Test void optionalLocationMayBeOmittedButUnknownStartFieldsReject() throws Exception {
    check(command("/yard-checks",UUID.randomUUID().toString(),Map.of("siteId",site.toString())));
    problem(command("/yard-checks",UUID.randomUUID().toString(),Map.of("siteId",site.toString(),"actor",user.toString())),400,"VALIDATION_FAILED");
  }
  @Test void locationSpecificCheckExcludesOtherDock() throws Exception {
    var id=f.trailer(tenant,"Dock-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",b,id);
    var r=command("/yard-checks",UUID.randomUUID().toString(),startBody(a));check(r);
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isEqualTo(1);
  }
  @ParameterizedTest @ValueSource(strings={"BASE","SHOP","CUSTOMER_AREA","OTHER"}) void yardSnapshotDoesNotInventNonYardInventory(String type) throws Exception {
    f.db.execute("update operational_locations set type=? where id=?",type,a);
    var r=start();check(r);assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isZero();
    problem(command("/yard-checks",UUID.randomUUID().toString(),startBody(a)),409,"RESOURCE_CONFLICT");
  }
  @ParameterizedTest @ValueSource(strings={"IN_TRANSIT","LOCATION_UNKNOWN"}) void stationaryInventoryExcludesNoLocationStates(String state) throws Exception {
    f.db.execute("update trailers set location_state=?,current_location_id=null where id=?",state,trailer);
    var r=start();check(r);assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isZero();
  }
  @Test void oosIsInventoryNotAvailableEquipment() throws Exception {
    f.db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?",trailer);
    var r=start();check(r);assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isEqualTo(1);
  }
  @Test void inactiveTrailerExcluded() throws Exception {
    f.db.execute("update trailers set active=false where id=?",trailer);var r=start();check(r);
    assertThat(JsonPath.<Integer>read(r.body(),"$.check.expectedCount")).isZero();
  }
  @Test void foreignLocationHiddenAndNoLedger() throws Exception {
    problem(command("/yard-checks",UUID.randomUUID().toString(),startBody(f.location(other))),404,"RESOURCE_NOT_FOUND");unchanged();
  }
  @Test void wrongSiteLocationHidden() throws Exception {
    problem(command("/yard-checks",UUID.randomUUID().toString(),startBody(f.location(tenant))),404,"RESOURCE_NOT_FOUND");unchanged();
  }
  @Test void unknownCheckHidden() throws Exception {problem(get("/yard-checks/"+UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");}
  @Test void revokedRoleBlocksHistoricalReplay() throws Exception {
    var key=UUID.randomUUID().toString();check(command("/yard-checks",key,startBody(null)));
    f.db.execute("delete from role_assignments where membership_id=?",membership);
    assertThat(command("/yard-checks",key,startBody(null)).statusCode()).isEqualTo(403);
  }
  @ParameterizedTest @ValueSource(strings={"limit=0","limit=201","foo=1","cursor=bad","expectedOnly=1","expectedVersion=-1"}) void closedBoundedDetailQueries(String query) throws Exception {
    var id=check(start());problem(get("/yard-checks/"+id+"?"+query),400,"VALIDATION_FAILED");
  }
  @Test void paginationRequiresVersionAndRetrievesEveryItemExactlyOnce() throws Exception {
    for(int i=0;i<205;i++){var id=f.trailer(tenant,"Bulk-"+i+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",a,id);}
    var r=start();var id=check(r);assertThat(JsonPath.<Boolean>read(r.body(),"$.snapshotComplete")).isFalse();
    var ids=new HashSet<String>(JsonPath.<List<String>>read(r.body(),"$.items[*].id"));
    var cursor=JsonPath.<String>read(r.body(),"$.nextCursor");
    problem(get("/yard-checks/"+id+"?cursor="+cursor),400,"VALIDATION_FAILED");
    var second=get("/yard-checks/"+id+"?cursor="+cursor+"&expectedVersion=0");
    ids.addAll(JsonPath.<List<String>>read(second.body(),"$.items[*].id"));
    assertThat(ids).hasSize(206);assertThat(JsonPath.<Object>read(second.body(),"$.nextCursor")).isNull();
    assertThat(JsonPath.<Boolean>read(second.body(),"$.snapshotComplete")).isFalse();
  }
  @Test void oneStatementSnapshotSeesCommittedInventoryWhileAnAtomicMovementIsInFlight() throws Exception {
    var extra=f.trailer(tenant,"Concurrent-"+UUID.randomUUID(),customer);f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=? where id=?",b,extra);
    try(var c=flyway.getConfiguration().getDataSource().getConnection()){
      c.setAutoCommit(false);
      try(var q=c.prepareStatement("update trailers set current_location_id=case when id=? then ?::uuid else ?::uuid end,version=version+1 where id in (?,?)")){
        q.setObject(1,trailer);q.setObject(2,b);q.setObject(3,a);q.setObject(4,trailer);q.setObject(5,extra);assertThat(q.executeUpdate()).isEqualTo(2);
      }
      var result=start();check(result);
      var originals=JsonPath.<List<Map<String,Object>>>read(result.body(),"$.items");
      assertThat(originals).hasSize(2);
      assertThat(originals.stream().filter(i->i.get("trailerId").equals(trailer.toString())).findFirst().orElseThrow().get("expectedLocationId")).isEqualTo(a.toString());
      assertThat(originals.stream().filter(i->i.get("trailerId").equals(extra.toString())).findFirst().orElseThrow().get("expectedLocationId")).isEqualTo(b.toString());
      c.commit();
      assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(b);
      assertThat(f.db.value("select current_location_id from trailers where id=?",extra)).isEqualTo(a);
    }
  }
}
