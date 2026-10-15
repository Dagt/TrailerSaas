package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.*;

/** A real V26 human-resolved Missing occurrence must survive the SYSTEM extension. */
class StaleUpgradePostgresTests extends YardCheckFixture {
 @DynamicPropertySource static void beforeStale(DynamicPropertyRegistry r){r.add("spring.flyway.target",()->"26");}
 @Test void v26ToV27RetainsResolvedMissingYardMovementAndLedgerHistory()throws Exception{
  assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("26");
  var check=check(start());assertThat(command("/yard-checks/"+check+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0)).statusCode()).isEqualTo(200);
  var item=JsonPath.<String>read(get("/yard-checks/"+check).body(),"$.items[0].id");
  var response=command("/yard-checks/"+check+"/items/"+item+"/mark-location-unknown",UUID.randomUUID().toString(),Map.of("expectedTrailerVersion",0,"reason","Real pre-upgrade missing finding"));
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);var alert=UUID.fromString(JsonPath.read(response.body(),"$.alertId"));
  f.role(tenant,membership,"TENANT_ADMIN",null);
  var correction=command("/trailers/"+trailer+"/location-corrections",null,Map.of("expectedVersion",1,"newLocationId",a.toString(),"reason","Recorded physical recovery"));assertThat(correction.statusCode()).as(correction.body()).isEqualTo(200);
  f.db.execute("update alerts set status='ACKNOWLEDGED',acknowledged_by=?,version=1 where id=?",user,alert);
  f.db.execute("update alerts set status='RESOLVED',resolved_by=?,resolution_reason='Recovered',version=2 where id=?",user,alert);
  var before=f.db.value("select to_jsonb(a)::text from alerts a where id=?",alert);
  var ledger=f.db.value("select json_agg(row_to_json(o) order by o.id)::text from processed_client_operations o where tenant_id=?",tenant);
  var moves=f.db.value("select json_agg(row_to_json(e) order by e.id)::text from trailer_movement_events e where tenant_id=?",tenant);
  var upgrade=Flyway.configure().configuration(flyway.getConfiguration()).target("27").load();upgrade.migrate();upgrade.validate();
  assertThat(f.db.value("select (to_jsonb(a)-array['origin_actor_type','detector_job_id','detected_last_seen_at','detected_threshold_hours','resolution_actor_type','resolution_job_id','resolution_last_seen_at','resolution_threshold_hours'])::text from alerts a where id=?",alert)).isEqualTo(before);
  assertThat(f.db.value("select json_agg(row_to_json(o) order by o.id)::text from processed_client_operations o where tenant_id=?",tenant)).isEqualTo(ledger);
  assertThat(f.db.value("select json_agg(row_to_json(e) order by e.id)::text from trailer_movement_events e where tenant_id=?",tenant)).isEqualTo(moves);
  assertThat(f.db.value("select origin_actor_type||'/'||resolution_actor_type from alerts where id=?",alert)).isEqualTo("HUMAN_USER/HUMAN_USER");
  assertThat(get("/alerts/"+alert).statusCode()).isEqualTo(200);
 }
}
