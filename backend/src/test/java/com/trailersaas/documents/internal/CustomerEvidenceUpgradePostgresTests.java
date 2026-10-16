package com.trailersaas.documents.internal;

import com.trailersaas.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class CustomerEvidenceUpgradePostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @DynamicPropertySource static void baseline(DynamicPropertyRegistry r){r.add("spring.flyway.target",()->"28");}
  @Test void v28AmbiguousLinksArePreservedWithoutInventingAcceptedFiles()throws Exception{
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("28");
    var db=new EquipmentFixtures(flyway);var tenant=db.tenant();var user=db.user();var trip=UUID.randomUUID();var stop=UUID.randomUUID();var receipt=UUID.randomUUID();
    db.execute("insert into trips(id,tenant_id,status,created_at,updated_at) values(?,?,'COMPLETED',now(),now())",trip,tenant);
    db.execute("insert into trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,arrived_at,service_started_at,completed_at,created_at,updated_at) values(?,?,?,1,'DELIVERY','COMPLETED','{\"name\":\"Original\"}',now(),now(),now(),now(),now())",stop,tenant,trip);
    db.execute("insert into delivery_evidence values(?,?,?,'SIGNED_BOL',null,?,now())",receipt,tenant,stop,user);
    for(int i=0;i<2;i++){var document=UUID.randomUUID();db.execute("insert into documents values(?,?,?,'BOL','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",document,tenant,"private/"+document,"0".repeat(64),user);db.execute("insert into document_links values(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,document,stop);}
    var before=db.value("select to_jsonb(e)::text from delivery_evidence e where id=?",receipt);
    var links=db.value("select jsonb_agg(to_jsonb(l) order by id)::text from document_links l where tenant_id=?",tenant);
    var checksums=db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success");
    var upgraded=Flyway.configure().configuration(flyway.getConfiguration()).target("29").load();upgraded.migrate();upgraded.validate();
    assertThat(db.value("select (to_jsonb(e)-'bol_document_id'-'delivery_photo_document_id')::text from delivery_evidence e where id=?",receipt)).isEqualTo(before);
    assertThat(db.value("select bol_document_id is null and delivery_photo_document_id is null from delivery_evidence where id=?",receipt)).isEqualTo(true);
    assertThat(db.value("select jsonb_agg(to_jsonb(l) order by id)::text from document_links l where tenant_id=?",tenant)).isEqualTo(links);
    assertThat(db.value("select jsonb_agg(jsonb_build_array(version,checksum) order by installed_rank)::text from flyway_schema_history where success and version::int<=28")).isEqualTo(checksums);
    assertThat(db.value("select count(*) from pg_indexes where schemaname='public' and indexname in ('delivery_evidence_bol_lookup','delivery_evidence_photo_lookup','documents_published_history')")).isEqualTo(3L);
    try(var c=flyway.getConfiguration().getDataSource().getConnection();var q=c.createStatement()) {
      try(var rows=q.executeQuery("select relrowsecurity from pg_class where oid='delivery_evidence'::regclass")){rows.next();assertThat(rows.getBoolean(1)).isTrue();}
    }
  }
}
