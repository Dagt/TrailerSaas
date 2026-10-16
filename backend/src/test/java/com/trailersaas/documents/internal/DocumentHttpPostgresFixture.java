package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.documents.PendingUploadInspection;
import com.trailersaas.identity.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DocumentHttpPostgresFixture.Storage.class)
abstract class DocumentHttpPostgresFixture extends PostgresIntegrationTest {
  @TestConfiguration(proxyBeanMethods=false) static class Storage {
    @Bean(destroyMethod="close") SignedS3TestServer s3Peer() throws Exception { return new SignedS3TestServer(); }
    @Bean(destroyMethod="close") @Primary ObjectStoragePort testStorage(SignedS3TestServer peer) { return new R2ObjectStorage(peer.settings(),true); }
  }
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired SignedS3TestServer peer;
  @Autowired PendingUploadInspection inspection;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  EquipmentFixtures db;
  UUID tenant,other,user,driver,membership,admin,operations,customerUser,customerA,customerB,trip,stop;
  byte[] bytes={1,2,3};
  @BeforeEach void seed() throws Exception {
    peer.failHead=false;peer.failCopy=false;peer.afterCopy=()->{};
    db=new EquipmentFixtures(flyway);tenant=db.tenant();other=db.tenant();user=db.user();
    db.execute("UPDATE tenants SET status='ACTIVE' WHERE id=?",tenant);
    membership=db.membership(tenant,user,"ACTIVE","DRIVER");driver=db.driver(tenant,user);
    admin=db.user();db.membership(tenant,admin,"ACTIVE","TENANT_ADMIN");
    operations=db.user();db.membership(tenant,operations,"ACTIVE","TRANSPORT_OPERATIONS");
    customerUser=db.user();var cm=db.membership(tenant,customerUser,"ACTIVE",null);
    var fixtures=new TrailerFixtures(flyway);customerA=fixtures.customer(tenant);customerB=fixtures.customer(tenant);
    fixtures.role(tenant,cm,"CUSTOMER_DISPATCHER",customerA);
    trip=UUID.randomUUID();stop=UUID.randomUUID();
    db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,now(),now())",trip,tenant,driver);
    db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'DELIVERY','PENDING','{\"name\":\"Frozen\"}',now(),now())",stop,tenant,trip);
    mapOrder(customerA);
    for(var entry:Map.of("driver",user,"admin",admin,"ops",operations,"customer",customerUser).entrySet())
      when(verifier.verify(entry.getKey())).thenReturn(new VerifiedFirebaseIdentity("fixture-"+entry.getValue(),null,null));
  }
  UUID mapOrder(UUID customer) throws Exception {
    UUID order=UUID.randomUUID();
    db.execute("INSERT INTO orders(id,tenant_id,customer_id,order_number,status,created_by_user_id,created_at,updated_at) VALUES(?,?,?,?,'OPEN',?,now(),now())",order,tenant,customer,"Doc-"+order,user);
    db.execute("INSERT INTO trip_stop_orders(id,tenant_id,trip_stop_id,order_id) VALUES(?,?,?,?)",UUID.randomUUID(),tenant,stop,order);return order;
  }
  String base() {return "/api/v1/tenants/"+tenant+"/documents";}
  HttpResponse<String> http(String token,String method,String path,String body,UUID key) throws Exception {
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
    if(token!=null) request.header("Authorization","Bearer "+token);
    if(key!=null) request.header("Idempotency-Key",key.toString());
    return HttpClient.newHttpClient().send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> initialize(String body,String token) throws Exception {return http(token,"POST",base()+"/uploads",body,null);}
  String initBody(String type) {return "{\"documentType\":\""+type+"\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3,\"checksumSha256\":null}";}
  UUID pending() throws Exception {
    var response=initialize(initBody("BOL"),"driver");assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
    var url=JsonPath.<String>read(response.body(),"$.uploadUrl");
    var put=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());
    assertThat(put.statusCode()).isEqualTo(200);
    return UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
  }
  HttpResponse<String> complete(UUID id,UUID key,String checksum) throws Exception {return http("driver","POST",base()+"/"+id+"/upload-completion","{\"checksumSha256\":"+(checksum==null?"null":"\""+checksum+"\"")+"}",key);}
  UUID uploaded() throws Exception {var id=pending();assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);return id;}
  HttpResponse<String> link(UUID id,UUID target) throws Exception {return http("driver","POST",base()+"/"+id+"/links","{\"entityType\":\"TRIP_STOP\",\"entityId\":\""+target+"\"}",null);}
  void assertProblem(HttpResponse<String> response,int status,String code) {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
    assertThat(JsonPath.<String>read(response.body(),"$.code")).isEqualTo(code);
    assertThat(JsonPath.<String>read(response.body(),"$.traceId")).isNotBlank();
    assertThat(response.body()).doesNotContain(peer.secret,peer.access,"object_key","constraint","SQLException","X-Amz-Signature");
  }
}
