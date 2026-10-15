package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Upgrade real V24 reconciliation history, not a fabricated Alert fixture. */
class AlertUpgradePostgresTests extends YardCheckFixture {
 @DynamicPropertySource static void beforeNotifications(DynamicPropertyRegistry r){r.add("spring.flyway.target",()->"25");}
 @Test void v25ToV26PreservesExistingMissingAlertAndMobileLedger()throws Exception{
  assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("25");
  var id=check(start());assertThat(command("/yard-checks/"+id+"/completion",UUID.randomUUID().toString(),Map.of("expectedVersion",0)).statusCode()).isEqualTo(200);
  var item=JsonPath.<String>read(get("/yard-checks/"+id).body(),"$.items[0].id");
  var response=command("/yard-checks/"+id+"/items/"+item+"/mark-location-unknown",UUID.randomUUID().toString(),Map.of("expectedTrailerVersion",0,"reason","Pre-upgrade confirmed finding"));
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);var alert=UUID.fromString(JsonPath.read(response.body(),"$.alertId"));
  var original=f.db.value("select row_to_json(a)::text from alerts a where id=?",alert);
  var ledger=f.db.value("select json_agg(row_to_json(o) order by o.id)::text from processed_client_operations o where tenant_id=?",tenant);
  var upgrade=Flyway.configure().configuration(flyway.getConfiguration()).target("26").load();upgrade.migrate();upgrade.validate();
  assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("26");
  assertThat(f.db.value("select (to_jsonb(a)-'resolution_reason')::text from alerts a where id=?",alert)).isEqualTo(f.db.value("select ?::jsonb::text",original.toString()));
  assertThat(f.db.value("select json_agg(row_to_json(o) order by o.id)::text from processed_client_operations o where tenant_id=?",tenant)).isEqualTo(ledger);
  assertThat(f.db.value("select status||'/'||version from alerts where id=?",alert)).isEqualTo("OPEN/0");
  assertThat(get("/yard-checks/"+id).statusCode()).isEqualTo(200);
 }
}
