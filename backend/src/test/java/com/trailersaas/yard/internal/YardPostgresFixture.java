package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.awaitility.Awaitility.await;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class YardPostgresFixture extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean FirebaseTokenVerifier verifier;
  @Value("${local.server.port}") int port;
  TrailerFixtures f;
  UUID tenant,other,user,membership,site,a,b,trailer,customer;
  final ObjectMapper json=new ObjectMapper();
  @BeforeEach void seed() throws Exception {
    f=new TrailerFixtures(flyway);tenant=f.db.tenant();other=f.db.tenant();user=f.db.user();
    f.db.execute("update tenants set status='ACTIVE' where id=?",tenant);
    membership=f.db.membership(tenant,user,"ACTIVE","YARD_SPOTTER");
    a=f.location(tenant);site=(UUID)f.db.value("select site_id from operational_locations where id=?",a);
    b=location(site,"DOCK");customer=f.customer(tenant);
    trailer=f.trailer(tenant,"Yard-"+UUID.randomUUID(),customer);
    f.db.execute("update trailers set location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=? where id=?",a,a,java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),trailer);
    when(verifier.verify("yard")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
  }
  UUID location(UUID parent,String type) throws Exception {
    UUID id=UUID.randomUUID();f.db.execute("insert into operational_locations(id,tenant_id,site_id,type,name,active,created_at,updated_at) values(?,?,?,?,?,true,now(),now())",id,tenant,parent,type,type+"-"+id);return id;
  }
  HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+path)).header("Authorization","Bearer yard").GET().build(),HttpResponse.BodyHandlers.ofString());
  }
  void problem(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(JsonPath.<String>read(r.body(),"$.code")).isEqualTo(code);}
  Map<String,Object> moveBody() {return new LinkedHashMap<>(Map.of("trailerId",trailer.toString(),"fromLocationId",a.toString(),"toLocationId",b.toString(),"observedAt",Instant.now().minusSeconds(5).toString(),"expectedTrailerVersion",0));}
  HttpResponse<String> post(String key,Map<String,Object> body) throws Exception {
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant+"/yard-moves"))
        .header("Authorization","Bearer yard").header("Content-Type","application/json");
    if(key!=null)request.header("Idempotency-Key",key);
    return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  void unchanged() throws Exception {
    assertThat(f.db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(a);
    assertThat(f.db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    assertThat(f.db.value("select count(*) from processed_client_operations where tenant_id=?",tenant)).isEqualTo(0L);
  }
}
