package com.trailersaas.orders.internal;

import com.trailersaas.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OrderRequestUpgradePostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired com.trailersaas.trailers.TrailerMovementProjection movements;
    @Autowired com.trailersaas.identity.TenantTransactionExecutor transactions;
    @DynamicPropertySource static void baseline(DynamicPropertyRegistry r){r.add("spring.flyway.target",()->"27");}
    @Test void realV27OrderRequestAndPhysicalHistorySurviveV28() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("27");
        var f=new OrderFixtures(flyway);var tenant=f.db.tenant();var customer=f.customer(tenant);var user=f.db.user();var order=f.order(tenant,customer,user);
        f.db.execute("update tenants set status='ACTIVE' where id=?",tenant);f.db.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        f.stop(tenant,order,1,null,null,"{\"name\":\"Frozen historical destination\"}");
        f.db.execute("insert into order_cargo_lines values(?,?,?,'Historical cargo',12.5,null,100,'LB',now())",UUID.randomUUID(),tenant,order);
        var before=f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order);
        var cargo=f.db.value("select jsonb_agg(to_jsonb(c))::text from order_cargo_lines c where order_id=?",order);
        var site=f.site(tenant,customer);var from=f.location(tenant,site,customer);var to=f.location(tenant,site,customer);
        f.db.execute("update operational_locations set type='YARD' where id=?",from);
        var trailer=new TrailerFixtures(flyway).trailer(tenant,"Historical-"+UUID.randomUUID(),customer);
        var provenance=new com.trailersaas.trailers.TrailerMovementProjection.Provenance(com.trailersaas.trailers.TrailerMovementProjection.ActorType.HUMAN_USER,com.trailersaas.trailers.TrailerMovementProjection.SourceType.USER_ACTION,user);
        transactions.execute(tenant,()->movements.correctLocation(tenant,new com.trailersaas.trailers.TrailerMovementProjection.Correction(new com.trailersaas.trailers.TrailerMovementProjection.Target(trailer,0),new com.trailersaas.trailers.TrailerMovementProjection.Context(java.time.Instant.now(),provenance,null,null,null,null,null),from,"Recorded physical observation",true)));
        transactions.execute(tenant,()->movements.yardMove(tenant,new com.trailersaas.trailers.TrailerMovementProjection.YardMove(new com.trailersaas.trailers.TrailerMovementProjection.Target(trailer,1),new com.trailersaas.trailers.TrailerMovementProjection.Context(java.time.Instant.now(),provenance,null,null,null,null,null),from,to)));
        var physical=f.db.value("select to_jsonb(t)::text from trailers t where id=?",trailer);
        var history=f.db.value("select jsonb_agg(to_jsonb(e) order by id)::text from trailer_movement_events e where trailer_id=?",trailer);
        var checksums=f.db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success");
        var upgrade=Flyway.configure().configuration(flyway.getConfiguration()).target("28").load();upgrade.migrate();upgrade.validate();
        assertThat(f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order)).isEqualTo(before);
        assertThat(f.db.value("select jsonb_agg(to_jsonb(c))::text from order_cargo_lines c where order_id=?",order)).isEqualTo(cargo);
        assertThat(f.db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success and version::int<=27")).isEqualTo(checksums);
        assertThat(f.db.value("select count(*) from order_request_revisions")).isEqualTo(0L);
        assertThat(f.db.value("select to_jsonb(t)::text from trailers t where id=?",trailer)).isEqualTo(physical);
        assertThat(f.db.value("select jsonb_agg(to_jsonb(e) order by id)::text from trailer_movement_events e where trailer_id=?",trailer)).isEqualTo(history);
    }
}
