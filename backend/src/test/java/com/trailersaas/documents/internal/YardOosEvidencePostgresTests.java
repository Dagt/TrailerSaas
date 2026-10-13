package com.trailersaas.documents.internal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.TrailerFixtures;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.trailersaas.identity.VerifiedFirebaseIdentity;

class YardOosEvidencePostgresTests extends DocumentHttpPostgresFixture {
  UUID spotter,spotterMembership,site,location,dock,trailer;
  final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
  @BeforeEach void yardContext() throws Exception {
    db.execute("update tenants set status='ACTIVE' where id=?",tenant);
    spotter=db.user();spotterMembership=db.membership(tenant,spotter,"ACTIVE","YARD_SPOTTER");
    var fixtures=new TrailerFixtures(flyway);fixtures.role(tenant,spotterMembership,"TENANT_ADMIN",null);
    location=fixtures.location(tenant);site=(UUID)db.value("select site_id from operational_locations where id=?",location);
    dock=UUID.randomUUID();db.execute("insert into operational_locations(id,tenant_id,site_id,type,name,active,created_at,updated_at) values(?,?,?,'DOCK','Damage dock',true,now(),now())",dock,tenant,site);
    trailer=fixtures.trailer(tenant,"Oos-"+UUID.randomUUID(),customerA);
    db.execute("update trailers set location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=now()-interval '1 hour' where id=?",location,location,trailer);
    when(verifier.verify("spotter")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+spotter,null,null));
  }
  String uploadPath(){return "/api/v1/tenants/"+tenant+"/trailers/"+trailer+"/damage-uploads";}
  UUID photo(String token,boolean finalized) throws Exception {
    var sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var response=http(token,"POST",uploadPath(),"{\"documentType\":\"DAMAGE_PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3,\"checksumSha256\":\""+sha+"\"}",null);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    var put=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(JsonPath.read(response.body(),"$.uploadUrl"))).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());assertThat(put.statusCode()).isEqualTo(200);
    var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    if(finalized){var complete=http(token,"POST",base()+"/"+id+"/upload-completion","{\"checksumSha256\":\""+sha+"\"}",UUID.randomUUID());assertThat(complete.statusCode()).as(complete.body()).isEqualTo(200);}
    return id;
  }
  Map<String,Object> body(List<UUID> documents) {
    return new LinkedHashMap<>(Map.of("trailerId",trailer.toString(),"siteId",site.toString(),"expectedTrailerVersion",0,"observedAt",Instant.now().minusSeconds(5).toString(),"comment","Damage physically observed","documentIds",documents.stream().map(UUID::toString).toList()));
  }
  HttpResponse<String> mark(String token,UUID key,Map<String,Object> body) throws Exception {
    return http(token,"POST","/api/v1/tenants/"+tenant+"/yard/out-of-service",json.writeValueAsString(body),key);
  }
  void unchanged() throws Exception {
    assertThat(db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("IN_SERVICE");assertThat(db.value("select version from trailers where id=?",trailer)).isEqualTo(0L);
    assertThat(db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(0L);
    assertThat(db.value("select count(*) from trailer_oos_evidence where tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void actualSignedUploadAndFinalizeAllowDriverlessYardOosWithAtomicEvidence() throws Exception {
    assertThat(db.value("select count(*) from drivers where tenant_id=? and user_id=?",tenant,spotter)).isEqualTo(0L);
    var id=photo("spotter",true);var observed=body(List.of(id));var key=UUID.randomUUID();var seen=db.value("select last_seen_at from trailers where id=?",trailer);
    var response=mark("spotter",key,observed);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(JsonPath.<String>read(response.body(),"$.movementEvent.eventType")).isEqualTo("MARKED_OOS");
    assertThat(db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("OUT_OF_SERVICE");
    assertThat(db.value("select current_location_id from trailers where id=?",trailer)).isEqualTo(location);assertThat(db.value("select last_seen_at from trailers where id=?",trailer)).isEqualTo(seen);
    assertThat(db.value("select load_state from trailers where id=?",trailer)).isEqualTo("UNKNOWN");assertThat(db.value("select designated_customer_id from trailers where id=?",trailer)).isEqualTo(customerA);
    assertThat(db.value("select count(*) from trailer_oos_evidence v join trailer_movement_events e on e.tenant_id=v.tenant_id and e.id=v.event_id where v.tenant_id=? and v.trailer_id=? and v.document_id=? and e.actor_type='HUMAN_USER' and e.source_type='USER_ACTION' and e.actor_user_id=? and e.client_operation_id=? and e.driver_id is null and e.trip_id is null and e.tractor_id is null",tenant,trailer,id,spotter,key)).isEqualTo(1L);
    assertThat(mark("spotter",key,observed).body()).isEqualTo(response.body());
    assertThat(db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"missing","pending","otherUploader","foreignDocument","wrongType","ownWrongType","wrongTrailer","duplicate","blank","unknownField","stale","inactiveSite","revoked","oos"})
  void invalidEvidenceOrContextCannotMutate(String mode) throws Exception {
    var id=photo(mode.equals("otherUploader")?"admin":"spotter",!mode.equals("pending"));var request=body(List.of(id));int status=409;String code="RESOURCE_CONFLICT";
    switch(mode) {
      case "missing" -> {request.put("documentIds",List.of());code="EVIDENCE_REQUIRED";}
      case "pending" -> code="UPLOAD_NOT_COMPLETE";
      case "otherUploader" -> {status=404;code="RESOURCE_NOT_FOUND";}
      case "foreignDocument" -> {var foreign=UUID.randomUUID();db.execute("insert into documents(id,tenant_id,object_key,document_type,content_type,size_bytes,checksum_sha256,upload_status,uploaded_by_user_id,source_type,created_at,uploaded_at) values(?,?,?,'DAMAGE_PHOTO','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",foreign,other,"private/"+foreign,"0".repeat(64),spotter);request.put("documentIds",List.of(foreign.toString()));status=404;code="RESOURCE_NOT_FOUND";}
      case "wrongType" -> {request.put("documentIds",List.of(uploaded().toString()));status=404;code="RESOURCE_NOT_FOUND";}
      case "ownWrongType" -> {var wrong=UUID.randomUUID();db.execute("insert into documents(id,tenant_id,object_key,document_type,content_type,size_bytes,checksum_sha256,upload_status,uploaded_by_user_id,source_type,created_at,uploaded_at) values(?,?,?,'BOL','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",wrong,tenant,"private/"+wrong,"0".repeat(64),spotter);db.execute("insert into document_links values(?,?,?,'TRAILER',?,now())",UUID.randomUUID(),tenant,wrong,trailer);request.put("documentIds",List.of(wrong.toString()));status=400;code="VALIDATION_FAILED";}
      case "wrongTrailer" -> {var another=new TrailerFixtures(flyway).trailer(tenant,"Other-"+UUID.randomUUID(),null);request.put("trailerId",another.toString());status=404;code="RESOURCE_NOT_FOUND";}
      case "duplicate" -> {request.put("documentIds",List.of(id.toString(),id.toString()));status=400;code="VALIDATION_FAILED";}
      case "blank" -> {request.put("comment"," ");status=400;code="VALIDATION_FAILED";}
      case "unknownField" -> {request.put("actor_type","SYSTEM");status=400;code="VALIDATION_FAILED";}
      case "stale" -> {request.put("expectedTrailerVersion",4);code="STALE_VERSION";}
      case "inactiveSite" -> db.execute("update physical_sites set active=false where id=?",site);
      case "revoked" -> {db.execute("update tenant_memberships set status='DISABLED' where id=?",spotterMembership);status=403;code="TENANT_ACCESS_DENIED";}
      case "oos" -> {db.execute("update trailers set serviceability_state='OUT_OF_SERVICE' where id=?",trailer);code="INVALID_STATE_TRANSITION";}
    }
    var response=mark("spotter",UUID.randomUUID(),request);assertProblem(response,status,code);
    if(mode.equals("oos"))db.execute("update trailers set serviceability_state='IN_SERVICE' where id=?",trailer);unchanged();
    assertThat(db.value("select count(*) from documents where id=?",id)).isEqualTo(1L);
  }
  @Test void photoInitializationAndCompletionRequireConcreteOosAuthority() throws Exception {
    db.execute("delete from role_assignments where membership_id=? and role_code='TENANT_ADMIN'",spotterMembership);
    assertProblem(http("spotter","POST",uploadPath(),"{\"documentType\":\"DAMAGE_PHOTO\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3}",null),403,"CAPABILITY_DENIED");
    unchanged();
  }
  @Test void lostAckStillRejectsRevokedCapabilityAndChangedPayload() throws Exception {
    var request=body(List.of(photo("spotter",true)));var key=UUID.randomUUID();assertThat(mark("spotter",key,request).statusCode()).isEqualTo(200);
    request.put("comment","Changed cause");assertProblem(mark("spotter",key,request),409,"IDEMPOTENCY_KEY_REUSED");request.put("comment","Damage physically observed");
    db.execute("delete from role_assignments where membership_id=? and role_code='TENANT_ADMIN'",spotterMembership);
    assertProblem(mark("spotter",key,request),403,"CAPABILITY_DENIED");assertThat(db.value("select version from trailers where id=?",trailer)).isEqualTo(1L);
  }
  @Test void concurrentOosHasExactlyOneEventAndAssociation() throws Exception {
    var request=body(List.of(photo("spotter",true)));var key=UUID.randomUUID();
    try(var pool=Executors.newFixedThreadPool(2)) {
      var one=pool.submit(()->mark("spotter",key,request));var two=pool.submit(()->mark("spotter",key,request));var first=one.get(30,TimeUnit.SECONDS);var second=two.get(30,TimeUnit.SECONDS);
      assertThat(first.statusCode()).as(first.body()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    }
    assertThat(db.value("select count(*) from trailer_movement_events where trailer_id=?",trailer)).isEqualTo(1L);assertThat(db.value("select count(*) from trailer_oos_evidence where trailer_id=?",trailer)).isEqualTo(1L);
  }
  @Test void evidenceFailureAfterPhysicalFlushRollsBackEverythingExceptEarlierUpload() throws Exception {
    var request=body(List.of(photo("spotter",true)));var key=UUID.randomUUID();var function="oos_failure_"+UUID.randomUUID().toString().replace("-","");
    db.execute("create function "+function+"() returns trigger language plpgsql as $$ begin raise exception 'Controlled evidence failure'; end $$");
    db.execute("create trigger "+function+" before insert on trailer_oos_evidence for each row execute function "+function+"()");
    try{assertThat(mark("spotter",key,request).statusCode()).isEqualTo(500);}finally{db.execute("drop trigger "+function+" on trailer_oos_evidence");db.execute("drop function "+function+"()");}
    unchanged();assertThat(db.value("select count(*) from processed_client_operations where tenant_id=? and client_operation_id=?",tenant,key)).isEqualTo(0L);
    assertThat(mark("spotter",key,request).statusCode()).isEqualTo(200);
  }
  @Test void acceptedOosPreventsYardMoveAndDoesNotGrantReturnToService() throws Exception {
    assertThat(mark("spotter",UUID.randomUUID(),body(List.of(photo("spotter",true)))).statusCode()).isEqualTo(200);
    var move=new LinkedHashMap<String,Object>(Map.of("trailerId",trailer.toString(),"fromLocationId",location.toString(),"toLocationId",dock.toString(),"observedAt",Instant.now().minusSeconds(1).toString(),"expectedTrailerVersion",1));
    assertProblem(http("spotter","POST","/api/v1/tenants/"+tenant+"/yard-moves",json.writeValueAsString(move),UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");
    db.execute("delete from role_assignments where membership_id=? and role_code='TENANT_ADMIN'",spotterMembership);
    assertProblem(http("spotter","POST","/api/v1/tenants/"+tenant+"/trailers/"+trailer+"/return-to-service","{\"expectedVersion\":1,\"reason\":\"Repair\"}",null),403,"CAPABILITY_DENIED");
    assertThat(db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("OUT_OF_SERVICE");
  }
  @Test void previouslyAcceptedPhotographCannotCreateAnotherOosAfterAdministrativeReturn() throws Exception {
    var id=photo("spotter",true);var request=body(List.of(id));assertThat(mark("spotter",UUID.randomUUID(),request).statusCode()).isEqualTo(200);
    var restored=http("admin","POST","/api/v1/tenants/"+tenant+"/trailers/"+trailer+"/return-to-service","{\"expectedVersion\":1,\"reason\":\"Authorized repair\"}",null);assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
    request.put("expectedTrailerVersion",2);assertProblem(mark("spotter",UUID.randomUUID(),request),409,"RESOURCE_CONFLICT");
    assertThat(db.value("select version from trailers where id=?",trailer)).isEqualTo(2L);assertThat(db.value("select serviceability_state from trailers where id=?",trailer)).isEqualTo("IN_SERVICE");
    assertThat(db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='MARKED_OOS'",trailer)).isEqualTo(1L);
  }
  @Test void markingCoupledTrailerDoesNotDropOrCompleteTrip() throws Exception {
    var tractor=db.tractor(tenant,"Damage-"+UUID.randomUUID());var hook=UUID.randomUUID();
    db.execute("insert into trailer_movement_events(id,tenant_id,trailer_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at) values(?,?,?,'HOOKED','HUMAN_USER','USER_ACTION',?,now(),now())",hook,tenant,trailer,user);
    db.execute("insert into trailer_couplings(id,tenant_id,trailer_id,tractor_id,driver_id,trip_id,hooked_at,hook_event_id,created_at) values(?,?,?,?,?,?,now(),?,now())",UUID.randomUUID(),tenant,trailer,tractor,driver,trip,hook);
    var request=body(List.of(photo("spotter",true)));assertThat(mark("spotter",UUID.randomUUID(),request).statusCode()).isEqualTo(200);
    assertThat(db.value("select count(*) from trailer_couplings where trailer_id=? and dropped_at is null",trailer)).isEqualTo(1L);
    assertThat(db.value("select status from trips where id=?",trip)).isEqualTo("ASSIGNED");
    assertThat(db.value("select count(*) from trailer_movement_events where trailer_id=? and event_type='DROPPED'",trailer)).isEqualTo(0L);
  }
  @Test void finalizedBytesAndPublicationSurviveLostCompletionAckButRevocationStillDeniesReplay() throws Exception {
    var id=photo("spotter",false);var key=UUID.randomUUID();var request="{}";
    var first=http("spotter","POST",base()+"/"+id+"/upload-completion",request,key);assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
    var again=http("spotter","POST",base()+"/"+id+"/upload-completion",request,key);assertThat(again.body()).isEqualTo(first.body());
    assertThat(db.value("select count(*) from document_links where tenant_id=? and document_id=? and entity_type='TRAILER' and entity_id=?",tenant,id,trailer)).isEqualTo(1L);
    assertThat(http("spotter","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
    db.execute("delete from role_assignments where membership_id=? and role_code='TENANT_ADMIN'",spotterMembership);
    assertProblem(http("spotter","POST",base()+"/"+id+"/upload-completion",request,key),403,"CAPABILITY_DENIED");unchanged();
  }

}
