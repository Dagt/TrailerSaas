package com.trailersaas.identity.internal;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceApiPostgresTests extends PostgresIntegrationTest {
 @Autowired Flyway flyway; @MockitoBean FirebaseTokenVerifier verifier;
 @Value("${local.server.port}")int port;
 EquipmentFixtures db;UUID user,other;
 final HttpClient http=HttpClient.newHttpClient();
 final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
 @BeforeEach void seed()throws Exception{db=new EquipmentFixtures(flyway);user=db.user();other=db.user();actor(user);}
 void actor(UUID id){when(verifier.verify("devices")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+id,null,null));}
 HttpResponse<String> call(String method,String path,String body)throws Exception{
  return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/"+path)).header("Authorization","Bearer devices").header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void globalRegistrationNeedsNoTenantAndReceiptContainsNoToken()throws Exception{
  var id=UUID.randomUUID();var body=json.writeValueAsString(Map.of("deviceId",id.toString(),"platform","IOS","fcmToken","private-device-token","deviceName","Optional private label"));
  var first=call("POST","devices",body);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(first.body()).contains(user.toString(),id.toString()).doesNotContain("private-device-token","Optional private label","tenantId");
  assertThat(call("POST","devices",body).statusCode()).isEqualTo(200);assertThat(db.value("select count(*) from user_devices where user_id=?",user)).isEqualTo(1L);
  assertThat(call("POST","tenants/"+UUID.randomUUID()+"/devices",body).statusCode()).isEqualTo(404);
 }
 @Test void nullTokenAndLogoutRetainHistoricalRow()throws Exception{
  var r=call("POST","devices","{\"platform\":\"ANDROID\",\"fcmToken\":null}");assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
  var id=UUID.fromString(json.readTree(r.body()).path("id").asText());assertThat(json.readTree(r.body()).path("pushRegistered").asBoolean()).isFalse();
  assertThat(call("DELETE","devices/"+id,null).statusCode()).isEqualTo(204);assertThat(call("DELETE","devices/"+id,null).statusCode()).isEqualTo(204);
  assertThat(db.value("select count(*) from user_devices where id=? and not active",id)).isEqualTo(1L);
 }
 @Test void transferNeverRebindsUserAndForeignDeleteDenies()throws Exception{
  var a=call("POST","devices","{\"platform\":\"ANDROID\",\"fcmToken\":\"shared-installation-token\"}");var old=UUID.fromString(json.readTree(a.body()).path("id").asText());actor(other);
  var b=call("POST","devices","{\"platform\":\"ANDROID\",\"fcmToken\":\"shared-installation-token\"}");var current=UUID.fromString(json.readTree(b.body()).path("id").asText());assertThat(current).isNotEqualTo(old);
  assertThat(call("DELETE","devices/"+old,null).statusCode()).isEqualTo(404);actor(user);assertThat(call("DELETE","devices/"+current,null).statusCode()).isEqualTo(404);
  assertThat(db.value("select user_id from user_devices where id=?",old)).isEqualTo(user);assertThat(db.value("select not active and fcm_token is null from user_devices where id=?",old)).isEqualTo(true);
 }
 @ParameterizedTest @ValueSource(strings={"{}","{\"platform\":\"WEB\"}","{\"platform\":\"ANDROID\",\"userId\":\"spoofed\"}","{\"platform\":\"IOS\",\"fcmToken\":42}","{\"platform\":\"IOS\",\"fcmToken\":\"bad token\"}","{\"platform\":\"IOS\",\"deviceName\":\" \"}","{\"platform\":\"IOS\",\"deviceId\":\"not-a-uuid\"}"})
 void strictRequestRejectsInvalidAndUnknownFields(String body)throws Exception{assertThat(call("POST","devices",body).statusCode()).isEqualTo(400);assertThat(db.value("select count(*) from user_devices where user_id=?",user)).isEqualTo(0L);}
 @Test void foreignRegistrationCannotBeReused()throws Exception{
  var id=UUID.fromString(json.readTree(call("POST","devices","{\"platform\":\"IOS\"}").body()).path("id").asText());actor(other);
  var r=call("POST","devices",json.writeValueAsString(Map.of("platform","IOS","deviceId",id.toString(),"fcmToken","new-token")));assertThat(r.statusCode()).as(r.body()).isEqualTo(409);assertThat(r.body()).doesNotContain(user.toString());
 }
}
