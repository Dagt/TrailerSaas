package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The production disabled-storage bean, actual HTTP/authorization and runtime PostgreSQL role. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="R2_ENABLED=false")
class CustomerStorageDisabledPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired R2Configuration.Settings settings;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  @Test void unavailableR2DoesNotBreakAuthorizedIndexButNeverReturnsAFabricatedDownload()throws Exception{
    assertThat(settings.enabled()).isFalse();
    var db=new EquipmentFixtures(flyway);var tenant=db.tenant();var user=db.user();
    db.execute("update tenants set status='ACTIVE' where id=?",tenant);
    var member=db.membership(tenant,user,"ACTIVE",null);var f=new TrailerFixtures(flyway);var customer=f.customer(tenant);f.role(tenant,member,"CUSTOMER_DISPATCHER",customer);
    var trip=UUID.randomUUID();var stop=UUID.randomUUID();var order=UUID.randomUUID();var file=UUID.randomUUID();
    db.execute("insert into trips(id,tenant_id,status,created_at,updated_at) values(?,?,'COMPLETED',now(),now())",trip,tenant);
    db.execute("insert into trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,arrived_at,service_started_at,completed_at,created_at,updated_at) values(?,?,?,1,'DELIVERY','COMPLETED','{\"name\":\"Authorized\"}',now(),now(),now(),now(),now())",stop,tenant,trip);
    db.execute("insert into orders(id,tenant_id,customer_id,order_number,status,created_by_user_id,created_at,updated_at) values(?,?,?,'Delivery','OPEN',?,now(),now())",order,tenant,customer,user);
    db.execute("insert into trip_stop_orders(id,tenant_id,trip_stop_id,order_id) values(?,?,?,?)",UUID.randomUUID(),tenant,stop,order);
    db.execute("insert into documents values(?,?,?,'BOL','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",file,tenant,"private/never-public","0".repeat(64),user);
    db.execute("insert into document_links values(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,file,stop);
    db.execute("insert into delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,completed_by_user_id,completed_at,bol_document_id) values(?,?,?,'SIGNED_BOL',?,now(),?)",UUID.randomUUID(),tenant,stop,user,file);
    when(verifier.verify("customer")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
    var base="/api/v1/tenants/"+tenant;
    var index=http("GET",base+"/customers/"+customer+"/documents");assertThat(index.statusCode()).withFailMessage(index.body()).isEqualTo(200);assertThat(index.body()).contains(file.toString(),"UPLOADED").doesNotContain("private/never-public");
    assertThat(http("GET",base+"/documents/"+file+"?customerId="+customer).statusCode()).isEqualTo(200);
    var download=http("POST",base+"/documents/"+file+"/download-url?customerId="+customer);
    assertThat(download.statusCode()).withFailMessage(download.body()).isEqualTo(503);
    assertThat(download.body()).contains("OBJECT_STORAGE_UNAVAILABLE").doesNotContain("downloadUrl","private/never-public","X-Amz");
  }
  HttpResponse<String> http(String method,String path)throws Exception{
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer customer").header("Content-Type","application/json").method(method,method.equals("POST")?HttpRequest.BodyPublishers.ofString("{}"):HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
  }
}
