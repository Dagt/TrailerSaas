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
@Import(DocumentPostgresTests.Storage.class)
class DocumentPostgresTests extends PostgresIntegrationTest {
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
  @Test void directUploadVerifiedPublicationLinkAndAuthorizedDownload() throws Exception {
    var id=pending();var key=UUID.randomUUID();var response=complete(id,key,SignedS3TestServer.sha(bytes));
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    assertThat(response.body()).contains("UPLOADED").doesNotContain("tenantId","objectKey","uploadedByUserId","sourceType");
    assertThat(JsonPath.<String>read(response.body(),"$.uploadedAt")).isNotBlank();
    assertThat(link(id,stop).statusCode()).isEqualTo(200);
    for(String reader:List.of("driver","admin","ops","customer")) {
      var metadata=http(reader,"GET",base()+"/"+id,null,null);assertThat(metadata.statusCode()).withFailMessage(metadata.body()).isEqualTo(200);
      var download=http(reader,"POST",base()+"/"+id+"/download-url","{}",null);assertThat(download.statusCode()).withFailMessage(download.body()).isEqualTo(200);
      var signed=JsonPath.<String>read(download.body(),"$.downloadUrl");
      var downloaded=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(signed)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
      assertThat(downloaded.statusCode()).isEqualTo(200);assertThat(downloaded.body()).isEqualTo(bytes);
    }
    assertThat(db.value("SELECT source_type FROM documents WHERE id=?",id)).isEqualTo("USER_ACTION");
    assertThat(db.value("SELECT uploaded_by_user_id FROM documents WHERE id=?",id)).isEqualTo(user);
    assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE tenant_id=? AND client_operation_id=?",tenant,key)).isEqualTo(1L);
  }
  @Test void missingObjectDoesNotCommitLedgerOrUploadedState() throws Exception {
    var response=initialize(initBody("BOL"),"driver");var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));var key=UUID.randomUUID();
    assertProblem(complete(id,key,null),409,"UPLOAD_NOT_COMPLETE");
    assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("PENDING");
    assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE tenant_id=? AND client_operation_id=?",tenant,key)).isEqualTo(0L);
  }
  @Test void checksumMismatchIsNotAClientMetadataProof() throws Exception {
    var id=pending();assertProblem(complete(id,UUID.randomUUID(),"0".repeat(64)),409,"UPLOAD_CONTENT_MISMATCH");
    assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("PENDING");
    assertThat(complete(id,UUID.randomUUID(),SignedS3TestServer.sha(bytes)).statusCode()).isEqualTo(200);
  }
  @Test void actualMimeAndSizeAreVerified() throws Exception {
    var id=pending();String stage=DocumentService.temporary(tenant,id);
    peer.objects.put(stage,new SignedS3TestServer.Blob(bytes,"image/png"));assertProblem(complete(id,UUID.randomUUID(),null),409,"UPLOAD_CONTENT_MISMATCH");
    peer.objects.put(stage,new SignedS3TestServer.Blob(new byte[2],"image/jpeg"));assertProblem(complete(id,UUID.randomUUID(),null),409,"UPLOAD_CONTENT_MISMATCH");
    assertThat(db.value("SELECT uploaded_at IS NULL FROM documents WHERE id=?",id)).isEqualTo(true);
  }
  @Test void lostCompletionResponseReplaysSameBytesIdentityAndTime() throws Exception {
    var id=pending();var key=UUID.randomUUID();var first=complete(id,key,null);int copied=peer.copies.get();
    var second=complete(id,key,null);assertThat(second.statusCode()).withFailMessage(second.body()).isEqualTo(200);
    assertThat(second.body()).isEqualTo(first.body());assertThat(peer.copies.get()).isEqualTo(copied);
    String publication=(String)db.value("SELECT object_key FROM documents WHERE id=?",id);
    peer.objects.put(DocumentService.temporary(tenant,id),new SignedS3TestServer.Blob(new byte[]{9,9,9},"image/jpeg"));
    assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
    assertThat(db.value("SELECT object_key FROM documents WHERE id=?",id)).isEqualTo(publication);assertThat(peer.objects.get(publication).bytes()).isEqualTo(bytes);
  }
  @Test void incompatibleKeyAndOtherUserCannotReplay() throws Exception {
    var id=pending();var key=UUID.randomUUID();assertThat(complete(id,key,null).statusCode()).isEqualTo(200);
    assertProblem(complete(id,key,SignedS3TestServer.sha(bytes)),409,"IDEMPOTENCY_KEY_REUSED");
    var second=pending();assertProblem(complete(second,key,null),409,"IDEMPOTENCY_KEY_REUSED");
    var otherUser=db.user();db.membership(tenant,otherUser,"ACTIVE","DRIVER");db.driver(tenant,otherUser);
    when(verifier.verify("other-driver")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+otherUser,null,null));
    assertProblem(http("other-driver","POST",base()+"/"+id+"/upload-completion","{}",key),404,"RESOURCE_NOT_FOUND");
  }
  @Test void concurrentFinalizationsPublishExactlyOnce() throws Exception {
    var id=pending();var key=UUID.randomUUID();int before=peer.copies.get();
    var copied=new CountDownLatch(1);var release=new CountDownLatch(1);
    peer.afterCopy=()-> {copied.countDown();try {if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Completion latch timed out");} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);} };
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var one=executor.submit(()->complete(id,key,null));assertThat(copied.await(10,TimeUnit.SECONDS)).isTrue();
      var two=executor.submit(()->complete(id,key,null));awaitDocumentLock();release.countDown();
      var first=one.get(15,TimeUnit.SECONDS);var second=two.get(15,TimeUnit.SECONDS);
      assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);assertThat(second.statusCode()).withFailMessage(second.body()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    } finally { release.countDown();peer.afterCopy=()->{}; }
    assertThat(peer.copies.get()).isEqualTo(before+1);
  }
  void awaitDocumentLock() throws Exception {
    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
    while(System.nanoTime()<until) {
      if((Long)db.value("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%documents%' AND query ILIKE '%FOR UPDATE%'")>0) return;
      Thread.onSpinWait();
    }
    throw new AssertionError("No PostgreSQL document lock wait observed");
  }
  @Test void storageFailureAfterCopyAndDatabaseFailureAreRecoverableWithoutFalsePublication() throws Exception {
    var id=pending();var key=UUID.randomUUID();peer.afterCopy=()->peer.failHead=true;
    assertProblem(complete(id,key,null),503,"OBJECT_STORAGE_UNAVAILABLE");peer.afterCopy=()->{};peer.failHead=false;
    assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("PENDING");
    db.execute("CREATE FUNCTION fail_document_publication() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only forced persistence failure'; END $$");
    db.execute("CREATE TRIGGER document_test_failure BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION fail_document_publication()");
    try {
      var failure=complete(id,key,null);assertThat(failure.statusCode()).isGreaterThanOrEqualTo(500);
      assertThat(failure.body()).doesNotContain("test-only","SQL",peer.secret,"X-Amz-Signature");
      assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("PENDING");
      assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
    } finally {db.execute("DROP TRIGGER document_test_failure ON documents");db.execute("DROP FUNCTION fail_document_publication()");}
    assertThat(complete(id,key,null).statusCode()).isEqualTo(200);
  }
  @Test void mixedCustomerStopDoesNotLeakDocumentOrReferences() throws Exception {
    var id=uploaded();link(id,stop);assertThat(http("customer","GET",base()+"/"+id,null,null).statusCode()).isEqualTo(200);
    mapOrder(customerB);
    assertProblem(http("customer","GET",base()+"/"+id,null,null),404,"RESOURCE_NOT_FOUND");
    assertProblem(http("customer","POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    assertThat(http("admin","GET",base()+"/"+id,null,null).statusCode()).isEqualTo(200);
    assertThat(http("admin","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
  }
  @Test void unlinkedDocumentCannotBeDownloadedEvenByUploaderOrAdmin() throws Exception {
    var id=uploaded();for(String token:List.of("driver","admin")) assertProblem(http(token,"POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void targetAndDocumentAreTenantSafeAndLinkDuplicateIsStable() throws Exception {
    var id=uploaded();var first=link(id,stop);var second=link(id,stop);assertThat(first.statusCode()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    assertThat(db.value("SELECT count(*) FROM document_links WHERE document_id=?",id)).isEqualTo(1L);
    assertProblem(link(id,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    var foreignTrip=UUID.randomUUID();var foreignStop=UUID.randomUUID();
    db.execute("INSERT INTO trips(id,tenant_id,status,created_at,updated_at) VALUES(?,?,'PLANNED',now(),now())",foreignTrip,other);
    db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'OTHER','PENDING','{\"name\":\"Foreign\"}',now(),now())",foreignStop,other,foreignTrip);
    assertProblem(link(id,foreignStop),404,"RESOURCE_NOT_FOUND");
    assertProblem(http("driver","GET","/api/v1/tenants/"+other+"/documents/"+id,null,null),403,"TENANT_ACCESS_DENIED");
    db.membership(other,user,"ACTIVE","DRIVER");db.driver(other,user);
    assertProblem(http("driver","GET","/api/v1/tenants/"+other+"/documents/"+id,null,null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void pendingLinkAndUnsupportedTargetsAreRejected() throws Exception {
    var id=pending();assertProblem(link(id,stop),409,"UPLOAD_NOT_COMPLETE");
    assertProblem(http("driver","POST",base()+"/"+id+"/links","{\"entityType\":\"TRAILER\",\"entityId\":\""+stop+"\"}",null),400,"VALIDATION_FAILED");
  }
  @Test void revokedMembershipRoleDriverAndAssignmentAreFresh() throws Exception {
    var id=pending();var key=UUID.randomUUID();
    db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);assertProblem(complete(id,key,null),403,"TENANT_ACCESS_DENIED");
    db.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership);db.execute("UPDATE drivers SET active=false WHERE id=?",driver);assertProblem(complete(id,key,null),403,"CAPABILITY_DENIED");
    db.execute("UPDATE drivers SET active=true WHERE id=?",driver);assertThat(complete(id,key,null).statusCode()).isEqualTo(200);link(id,stop);
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);
    assertProblem(link(id,stop),404,"RESOURCE_NOT_FOUND");assertProblem(http("driver","POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);assertProblem(complete(id,key,null),403,"CAPABILITY_DENIED");
  }
  @ParameterizedTest @ValueSource(strings={"admin","ops","customer"})
  void readCapabilitiesDoNotGrantUploadOrLink(String token) throws Exception {
    assertProblem(initialize(initBody("BOL"),token),403,"CAPABILITY_DENIED");
    var id=uploaded();assertProblem(http(token,"POST",base()+"/"+id+"/links","{\"entityType\":\"TRIP_STOP\",\"entityId\":\""+stop+"\"}",null),403,"CAPABILITY_DENIED");
  }
  @ParameterizedTest @ValueSource(strings={"PRETRIP_FRONT","PRETRIP_REAR","PRETRIP_DRIVER_SIDE","PRETRIP_PASSENGER_SIDE","DAMAGE_PHOTO","FUEL_RECEIPT"})
  void futureUploadPermissionsStayBlocked(String type) throws Exception {assertProblem(initialize(initBody(type),"driver"),403,"CAPABILITY_DENIED");}
  @ParameterizedTest @ValueSource(strings={"{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3,\"objectKey\":\"bad\"}","{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3.5}","{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":\"3\"}","{\"documentType\":\"BOL\",\"contentType\":\"text/html\",\"sizeBytes\":3}","{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":0}","{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":104857601}","{\"documentType\":\"BOL\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3,\"checksumSha256\":\"bad\"}","{\"documentType\":\"OTHER\",\"contentType\":\"image/jpeg\",\"sizeBytes\":3}","null","{}"})
  void initializationIsStrict(String body) throws Exception {assertProblem(initialize(body,"driver"),400,"VALIDATION_FAILED");}
  @Test void authenticationHeadersQueriesAndProvenanceAreStrict() throws Exception {
    assertThat(initialize(initBody("BOL"),null).statusCode()).isEqualTo(401);
    var id=pending();assertProblem(http("driver","POST",base()+"/"+id+"/upload-completion","{}",null),400,"VALIDATION_FAILED");
    for(String body:List.of("{\"sourceType\":\"SYSTEM_PROCESS\"}","{\"actorUserId\":\""+admin+"\"}","{\"checksumSha256\":\"bad\"}")) assertProblem(http("driver","POST",base()+"/"+id+"/upload-completion",body,UUID.randomUUID()),400,"VALIDATION_FAILED");
    assertProblem(http("admin","GET",base()+"/"+id+"?tenantId=bad",null,null),400,"VALIDATION_FAILED");
  }
  @Test void pendingInspectionIsBoundedRevalidatedAndNeverDeletes() throws Exception {
    var old=pending();var finalized=pending();var fresh=pending();
    db.execute("UPDATE documents SET created_at=now()-interval '2 days' WHERE id IN (?,?)",old,finalized);
    assertThat(complete(finalized,UUID.randomUUID(),null).statusCode()).isEqualTo(200);link(finalized,stop);
    Instant cutoff=Instant.now().minus(Duration.ofDays(1));
    assertThat(inspection.candidates(tenant,cutoff,20)).containsExactly(old);
    assertThat(inspection.inspect(tenant,old,cutoff)).isPresent();assertThat(inspection.inspect(tenant,finalized,cutoff)).isEmpty();
    assertThat(inspection.inspect(other,old,cutoff)).isEmpty();assertThat(inspection.inspect(tenant,fresh,cutoff)).isEmpty();
    assertThat(complete(old,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
    assertThat(inspection.inspect(tenant,old,cutoff)).isEmpty();assertThat(inspection.candidates(tenant,cutoff,20)).isEmpty();
    assertThat(db.value("SELECT count(*) FROM documents WHERE id IN (?,?,?)",old,finalized,fresh)).isEqualTo(3L);
  }
  @Test void runtimePrivilegesAndReusedConnectionRemainTenantPinned() throws Exception {
    var id=pending();
    try(var c=runtime.getConnection()) {
      assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);c.setAutoCommit(false);
      try(var s=c.createStatement()) {
        for(String table:List.of("documents","document_links")) {
          try(var r=s.executeQuery("SELECT has_table_privilege(current_user,'"+table+"','SELECT'),has_table_privilege(current_user,'"+table+"','INSERT'),has_table_privilege(current_user,'"+table+"','DELETE'),has_table_privilege(current_user,'"+table+"','UPDATE')")) {r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();assertThat(r.getBoolean(3)).isFalse();assertThat(r.getBoolean(4)).isFalse();}
          try(var r=s.executeQuery("SELECT count(*) FROM "+table)) {r.next();assertThat(r.getLong(1)).isZero();}
        }
        try(var r=s.executeQuery("SELECT rolsuper,rolbypassrls,has_schema_privilege(current_user,'public','CREATE') FROM pg_roles WHERE rolname=current_user")) {r.next();assertThat(r.getBoolean(1)).isFalse();assertThat(r.getBoolean(2)).isFalse();assertThat(r.getBoolean(3)).isFalse();}
        s.execute("SELECT set_config('app.current_tenant_id','"+tenant+"',true)");
        try(var r=s.executeQuery("SELECT id FROM documents WHERE id='"+id+"'")) {assertThat(r.next()).isTrue();}
        s.execute("SELECT set_config('app.current_user_id','"+user+"',true)");
        try(var r=s.executeQuery("SELECT count(*) FROM documents")) {r.next();assertThat(r.getLong(1)).isZero();}
        c.rollback();s.execute("SELECT set_config('app.current_tenant_id','"+other+"',true)");
        try(var r=s.executeQuery("SELECT count(*) FROM documents")) {r.next();assertThat(r.getLong(1)).isZero();}
        assertThatThrownBy(()->s.executeUpdate("INSERT INTO document_links VALUES('"+UUID.randomUUID()+"','"+tenant+"','"+id+"','TRIP_STOP','"+stop+"',now())")).isInstanceOf(SQLException.class);c.rollback();
      }
    }
  }
  @ParameterizedTest @ValueSource(strings={"document_type='BAD'","upload_status='BAD'","source_type='WEB'","size_bytes=-1","checksum_sha256='unverified'","upload_status='UPLOADED'"})
  void databaseCatalogsAndMilestonesAreClosed(String change) throws Exception {
    var id=pending();assertThatThrownBy(()->db.execute("UPDATE documents SET "+change+" WHERE id=?",id)).isInstanceOf(SQLException.class);
  }
  @Test void databaseForeignKeysAndUniquenessCannotCrossTenant() throws Exception {
    var id=pending();
    assertThatThrownBy(()->db.execute("INSERT INTO document_links VALUES(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),other,id,stop)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute("UPDATE documents SET uploaded_by_user_id=? WHERE id=?",UUID.randomUUID(),id)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute("INSERT INTO documents SELECT ?,tenant_id,object_key,document_type,content_type,size_bytes,checksum_sha256,upload_status,uploaded_by_user_id,source_type,created_at,uploaded_at FROM documents WHERE id=?",UUID.randomUUID(),id)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute("UPDATE documents SET uploaded_by_user_id=NULL WHERE id=?",id)).isInstanceOf(SQLException.class);
  }

  @ParameterizedTest @ValueSource(strings={"BOL","POD","DELIVERY_PHOTO"})
  void implementedUploadTypesAndInitializedChecksumAreVerified(String type) throws Exception {
    String sha=SignedS3TestServer.sha(bytes);
    var response=initialize(initBody(type).replace("null","\""+sha.toUpperCase(Locale.ROOT)+"\""),"driver");
    assertThat(response.statusCode()).isEqualTo(201);var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    peer.objects.put(DocumentService.temporary(tenant,id),new SignedS3TestServer.Blob(bytes,"image/jpeg"));
    assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
    assertThat(db.value("SELECT checksum_sha256 FROM documents WHERE id=?",id)).isEqualTo(sha);
  }
  @Test void initializationFailuresDoNotLeaveMetadataOrFakeUploadSuccess() throws Exception {
    db.execute("CREATE FUNCTION fail_document_initialization() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only insertion failure'; END $$");
    db.execute("CREATE TRIGGER document_init_test_failure BEFORE INSERT ON documents FOR EACH ROW EXECUTE FUNCTION fail_document_initialization()");
    try {
      var result=initialize(initBody("BOL"),"driver");assertThat(result.statusCode()).isGreaterThanOrEqualTo(500);
      assertThat(result.body()).doesNotContain("uploadUrl","X-Amz-Signature","test-only",peer.secret);
      assertThat(db.value("SELECT count(*) FROM documents WHERE tenant_id=?",tenant)).isEqualTo(0L);
    } finally {db.execute("DROP TRIGGER document_init_test_failure ON documents");db.execute("DROP FUNCTION fail_document_initialization()");}
  }
  @Test void linkFailureRollsBackWithoutChangingFinalizedDocument() throws Exception {
    var id=uploaded();String original=(String)db.value("SELECT object_key FROM documents WHERE id=?",id);
    db.execute("CREATE FUNCTION fail_document_link() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only link failure'; END $$");
    db.execute("CREATE TRIGGER document_link_test_failure BEFORE INSERT ON document_links FOR EACH ROW EXECUTE FUNCTION fail_document_link()");
    try {assertThat(link(id,stop).statusCode()).isGreaterThanOrEqualTo(500);}
    finally {db.execute("DROP TRIGGER document_link_test_failure ON document_links");db.execute("DROP FUNCTION fail_document_link()");}
    assertThat(db.value("SELECT count(*) FROM document_links WHERE document_id=?",id)).isEqualTo(0L);
    assertThat(db.value("SELECT object_key FROM documents WHERE id=?",id)).isEqualTo(original);
    assertThat(link(id,stop).statusCode()).isEqualTo(200);
  }
  @Test void oneVisibleLinkCannotGrantAccessToDocumentSpanningForeignCustomerWork() throws Exception {
    var id=uploaded();link(id,stop);UUID first=stop;
    stop=UUID.randomUUID();db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'DELIVERY','PENDING','{\"name\":\"Second\"}',now(),now())",stop,tenant,trip);
    mapOrder(customerB);assertThat(link(id,stop).statusCode()).isEqualTo(200);
    var hidden=http("customer","GET",base()+"/"+id,null,null);assertProblem(hidden,404,"RESOURCE_NOT_FOUND");
    assertThat(hidden.body()).doesNotContain(first.toString(),stop.toString(),customerB.toString());
    assertProblem(http("customer","POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    assertThat(JsonPath.<List<?>>read(http("admin","GET",base()+"/"+id,null,null).body(),"$.links")).hasSize(2);
  }
  @Test void freshCustomerScopeRevocationDeniesPreviouslyVisibleDownload() throws Exception {
    var id=uploaded();link(id,stop);assertThat(http("customer","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
    db.execute("UPDATE role_assignments SET customer_id=? WHERE membership_id IN (SELECT id FROM tenant_memberships WHERE user_id=? AND tenant_id=?)",customerB,customerUser,tenant);
    assertProblem(http("customer","GET",base()+"/"+id,null,null),404,"RESOURCE_NOT_FOUND");
    assertProblem(http("customer","POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void finalizedRecordCannotBeRewrittenInSql() throws Exception {
    var id=uploaded();String key=(String)db.value("SELECT object_key FROM documents WHERE id=?",id);
    for(String change:List.of("object_key='other'","upload_status='PENDING',uploaded_at=NULL","checksum_sha256='"+"0".repeat(64)+"'","document_type='POD'"))
      assertThatThrownBy(()->db.execute("UPDATE documents SET "+change+" WHERE id=?",id)).isInstanceOf(SQLException.class);
    assertThat(db.value("SELECT object_key FROM documents WHERE id=?",id)).isEqualTo(key);
    assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("UPLOADED");
  }
  @Test void danglingPendingLinksAreExcludedByInspectionEvenIfSeededByMigrator() throws Exception {
    var id=pending();db.execute("UPDATE documents SET created_at=now()-interval '2 days' WHERE id=?",id);
    db.execute("INSERT INTO document_links VALUES(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,id,stop);
    var cutoff=Instant.now().minus(Duration.ofDays(1));assertThat(inspection.candidates(tenant,cutoff,20)).isEmpty();assertThat(inspection.inspect(tenant,id,cutoff)).isEmpty();
  }
  @Test void putGrantsDoNotPermitPublicationKeyOrDownloadBeforeLink() throws Exception {
    var response=initialize(initBody("BOL"),"driver");var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));String url=JsonPath.read(response.body(),"$.uploadUrl");
    var client=HttpClient.newHttpClient();
    assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
    assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);link(id,stop);
    String publication=(String)db.value("SELECT object_key FROM documents WHERE id=?",id);
    assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{8,8,8})).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
    String forbidden=url.replace(DocumentService.temporary(tenant,id),publication);
    assertThat(client.send(HttpRequest.newBuilder(URI.create(forbidden)).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{8,8,8})).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
    assertThat(peer.objects.get(publication).bytes()).isEqualTo(bytes);
  }
  @Test void duplicateFieldsNumericEnumsAndTrailingPayloadsAreRejected() throws Exception {
    for(String body:List.of(initBody("BOL").replace("\"BOL\"","0"),initBody("BOL")+" {}",initBody("BOL").replace("\"sizeBytes\":3","\"sizeBytes\":3,\"sizeBytes\":4")))
      assertProblem(initialize(body,"driver"),400,"VALIDATION_FAILED");
  }
  @Test void exactDatabaseSecurityCatalogAndNoCascades() throws Exception {
    try(var c=flyway.getConfiguration().getDataSource().getConnection();var s=c.createStatement()) {
      try(var r=s.executeQuery("SELECT c.relname,c.relrowsecurity,c.relowner=(SELECT oid FROM pg_roles WHERE rolname='"+RUNTIME_USERNAME+"') FROM pg_class c WHERE c.relname IN ('documents','document_links')")) {
        int count=0;while(r.next()) {count++;assertThat(r.getBoolean(2)).isTrue();assertThat(r.getBoolean(3)).isFalse();}assertThat(count).isEqualTo(2);
      }
      try(var r=s.executeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid IN ('documents'::regclass,'document_links'::regclass) AND contype='f' AND confdeltype='c'")) {r.next();assertThat(r.getLong(1)).isZero();}
      try(var r=s.executeQuery("SELECT count(*) FROM information_schema.role_table_grants WHERE table_name IN ('documents','document_links') AND grantee='PUBLIC'")) {r.next();assertThat(r.getLong(1)).isZero();}
      for(String column:List.of("object_key","checksum_sha256","upload_status","uploaded_at")) {
        try(var r=s.executeQuery("SELECT has_column_privilege('"+RUNTIME_USERNAME+"','documents','"+column+"','UPDATE')")) {r.next();assertThat(r.getBoolean(1)).isTrue();}
      }
      for(String column:List.of("tenant_id","id","uploaded_by_user_id","document_type","content_type","size_bytes","created_at","source_type")) {
        try(var r=s.executeQuery("SELECT has_column_privilege('"+RUNTIME_USERNAME+"','documents','"+column+"','UPDATE')")) {r.next();assertThat(r.getBoolean(1)).isFalse();}
      }
    }
  }
  @Test void completionCorsAcceptsUuidHeaderOnlyFromExactApprovedOrigins() throws Exception {
    var client=HttpClient.newHttpClient();
    for(String origin:List.of("http://localhost:5173","https://unapproved.example.test")) {
      var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+base()+"/"+UUID.randomUUID()+"/upload-completion"))
          .header("Origin",origin).header("Access-Control-Request-Method","POST")
          .header("Access-Control-Request-Headers","Authorization,Content-Type,Idempotency-Key").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
      if(origin.startsWith("http://localhost")) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow()).containsIgnoringCase("Idempotency-Key");
      } else {assertThat(response.statusCode()).isEqualTo(403);assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();}
    }
  }
  @Test void combinedPersistedReadGrantsRemainAdditiveWithoutGrantingUpload() throws Exception {
    var id=uploaded();link(id,stop);
    new TrailerFixtures(flyway).role(tenant,membership,"CUSTOMER_DISPATCHER",customerA);
    assertThat(http("driver","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
    db.execute("UPDATE drivers SET active=false WHERE id=?",driver);
    // Customer A read authority survives loss of Driver eligibility; upload still requires active Driver.
    assertThat(http("driver","GET",base()+"/"+id,null,null).statusCode()).isEqualTo(200);
    assertThat(http("driver","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
    assertProblem(initialize(initBody("BOL"),"driver"),403,"CAPABILITY_DENIED");
    mapOrder(customerB);assertProblem(http("driver","POST",base()+"/"+id+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE drivers SET active=true WHERE id=?",driver);
    assertThat(http("driver","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
  }

  @Test void everyEndpointRequiresAuthenticationAndFreshMembership() throws Exception {
    var id=uploaded();link(id,stop);
    var paths=List.of("/uploads","/"+id+"/upload-completion","/"+id+"/links","/"+id,"/"+id+"/download-url");
    var methods=List.of("POST","POST","POST","GET","POST");
    var bodies=Arrays.asList(initBody("BOL"),"{}","{\"entityType\":\"TRIP_STOP\",\"entityId\":\""+stop+"\"}",null,"{}");
    for(int i=0;i<paths.size();i++) assertThat(http(null,methods.get(i),base()+paths.get(i),bodies.get(i),UUID.randomUUID()).statusCode()).isEqualTo(401);
    db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
    for(int i=0;i<paths.size();i++) assertProblem(http("driver",methods.get(i),base()+paths.get(i),bodies.get(i),UUID.randomUUID()),403,"TENANT_ACCESS_DENIED");
    db.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership);
    db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    for(int i=0;i<paths.size();i++) assertProblem(http("driver",methods.get(i),base()+paths.get(i),bodies.get(i),UUID.randomUUID()),403,"CAPABILITY_DENIED");
  }

  @Test void providerFailuresNeverCreateFakeUploadOrDownloadSuccess() throws Exception {
    peer.failHead=true;
    assertProblem(initialize(initBody("BOL"),"driver"),503,"OBJECT_STORAGE_UNAVAILABLE");
    assertThat(db.value("SELECT count(*) FROM documents WHERE tenant_id=?",tenant)).isEqualTo(0L);
    peer.failHead=false;var id=uploaded();link(id,stop);peer.failHead=true;
    assertProblem(http("driver","POST",base()+"/"+id+"/download-url","{}",null),503,"OBJECT_STORAGE_UNAVAILABLE");
    assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("UPLOADED");
    peer.failHead=false;
    assertThat(http("driver","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
  }

  @Test void orphanInspectionWaitsForPublicationAndCannotTreatCommittedDocumentAsPending() throws Exception {
    var id=pending();db.execute("UPDATE documents SET created_at=now()-interval '2 days' WHERE id=?",id);
    var cutoff=Instant.now().minus(Duration.ofDays(1));assertThat(inspection.candidates(tenant,cutoff,20)).containsExactly(id);
    var copied=new CountDownLatch(1);var release=new CountDownLatch(1);
    peer.afterCopy=()-> {copied.countDown();try {if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Publication latch timed out");} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);} };
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var completion=executor.submit(()->complete(id,UUID.randomUUID(),null));assertThat(copied.await(10,TimeUnit.SECONDS)).isTrue();
      var inspectionResult=executor.submit(()->inspection.inspect(tenant,id,cutoff));awaitDocumentLock();release.countDown();
      assertThat(completion.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      assertThat(inspectionResult.get(15,TimeUnit.SECONDS)).isEmpty();
      assertThat(inspection.candidates(tenant,cutoff,20)).isEmpty();
      assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",id)).isEqualTo("UPLOADED");
    } finally {release.countDown();peer.afterCopy=()->{};}
  }
}
