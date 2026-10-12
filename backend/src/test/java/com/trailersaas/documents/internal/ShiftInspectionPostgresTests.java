package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.drivers.DriverShiftContext;
import com.trailersaas.trips.TripDeparturePrerequisites;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DocumentHttpPostgresFixture.Storage.class)
class ShiftInspectionPostgresTests extends DocumentHttpPostgresFixture {
  UUID tractor,shift;
  Instant start;
  @BeforeEach void shiftContext() throws Exception {
    tractor=db.tractor(tenant,"Shift-"+UUID.randomUUID());db.execute("UPDATE trips SET current_tractor_id=? WHERE id=?",tractor,trip);
    start=Instant.now().minusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var r=http("driver","POST",home()+"/shifts/on-duty","{\"occurredAt\":\""+start+"\"}",UUID.randomUUID());
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);shift=UUID.fromString(JsonPath.read(r.body(),"$.id"));
  }
  String home() {return "/api/v1/tenants/"+tenant+"/driver/me";}
  UUID image(String type,boolean complete) throws Exception {
    var r=initialize(initBody(type),"driver");assertThat(r.statusCode()).isEqualTo(201);UUID id=UUID.fromString(JsonPath.read(r.body(),"$.documentId"));
    if(complete) {peer.objects.put(DocumentService.temporary(tenant,id),new SignedS3TestServer.Blob(bytes,"image/jpeg"));assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);}
    return id;
  }
  UUID[] photos() throws Exception {return new UUID[]{image("PRETRIP_FRONT",true),image("PRETRIP_REAR",true),image("PRETRIP_DRIVER_SIDE",true),image("PRETRIP_PASSENGER_SIDE",true)};}
  String preBody(UUID[] ids,UUID selectedTrip) {
    return "{\"shiftId\":\""+shift+"\",\"tripId\":"+(selectedTrip==null?"null":"\""+selectedTrip+"\"")+",\"tractorId\":\""+tractor+"\",\"odometer\":123,\"checklist\":{\"items\":[]},\"documentIds\":{\"front\":"+id(ids[0])+",\"rear\":"+id(ids[1])+",\"driverSide\":"+id(ids[2])+",\"passengerSide\":"+id(ids[3])+"},\"occurredAt\":\""+start.plusSeconds(1)+"\"}";
  }
  String id(UUID id) {return id==null?"null":"\""+id+"\"";}
  java.net.http.HttpResponse<String> pre(UUID[] photos,UUID trip,UUID key) throws Exception {return http("driver","POST",home()+"/inspections/pre-trip",preBody(photos,trip),key);}
  @ParameterizedTest @ValueSource(booleans={true,false}) void validFourPhotosAttachPermanentlyAndReplayWithoutDuplicate(boolean tripContext) throws Exception {
    var images=photos();var key=UUID.randomUUID();var first=pre(images,tripContext?trip:null,key);assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);
    assertThat(pre(images,tripContext?trip:null,key).body()).isEqualTo(first.body());
    UUID inspection=UUID.fromString(JsonPath.read(first.body(),"$.id"));
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM document_links WHERE tenant_id=? AND entity_type='INSPECTION' AND entity_id=?",tenant,inspection)).isEqualTo(4L);
    for(UUID image:images) {
      assertThat(http("driver","POST",base()+"/"+image+"/download-url","{}",null).statusCode()).isEqualTo(200);
      assertThat(http("customer","GET",base()+"/"+image,null,null).statusCode()).isIn(403,404);
    }
    assertProblem(pre(images,tripContext?trip:null,UUID.randomUUID()),409,"RESOURCE_CONFLICT");
    db.execute("UPDATE drivers SET active=false WHERE id=?",driver);
    assertThat(http("driver","GET",base()+"/"+images[0],null,null).statusCode()).isIn(403,404);
  }
  @ParameterizedTest @ValueSource(ints={0,1,2,3}) void eachMissingPhotoRollsBackInspectionAndLedger(int missing) throws Exception {
    var images=photos();images[missing]=null;var key=UUID.randomUUID();assertProblem(pre(images,trip,key),409,"EVIDENCE_REQUIRED");
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
  }
  @Test void pendingWrongTypeDuplicateForeignAndReassignedPhotosCannotCreateInspection() throws Exception {
    var images=photos();var original=images[0];images[0]=image("PRETRIP_FRONT",false);assertProblem(pre(images,trip,UUID.randomUUID()),409,"UPLOAD_NOT_COMPLETE");
    images[0]=image("BOL",true);assertProblem(pre(images,trip,UUID.randomUUID()),400,"VALIDATION_FAILED");
    images[0]=images[1];assertProblem(pre(images,trip,UUID.randomUUID()),400,"VALIDATION_FAILED");
    images[0]=UUID.randomUUID();assertProblem(pre(images,trip,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    images[0]=original;db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);assertProblem(pre(images,trip,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void closedShiftWrongOrInactiveTractorAndStrictChecklistReject() throws Exception {
    var images=photos();db.execute("UPDATE tractors SET active=false WHERE id=?",tractor);assertProblem(pre(images,trip,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");db.execute("UPDATE tractors SET active=true WHERE id=?",tractor);
    var body=preBody(images,trip).replace("\"items\":[]","\"approved\":true");assertProblem(http("driver","POST",home()+"/inspections/pre-trip",body,UUID.randomUUID()),400,"VALIDATION_FAILED");
    db.execute("UPDATE shifts SET off_duty_at=now() WHERE id=?",shift);assertProblem(pre(images,trip,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");
  }
  @Test void atomicAttachmentFailureRollsBackEveryAssociationAndInspection() throws Exception {
    var images=photos();UUID key=UUID.randomUUID();db.execute("CREATE FUNCTION fail_inspection_link() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.entity_type='INSPECTION' THEN RAISE EXCEPTION 'test-only link failure'; END IF; RETURN NEW; END $$");db.execute("CREATE TRIGGER fail_inspection_link BEFORE INSERT ON document_links FOR EACH ROW EXECUTE FUNCTION fail_inspection_link()");
    try {assertThat(pre(images,trip,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM document_links WHERE tenant_id=?",tenant)).isEqualTo(0L);}
    finally {db.execute("DROP TRIGGER fail_inspection_link ON document_links");db.execute("DROP FUNCTION fail_inspection_link()");}
    assertThat(pre(images,trip,key).statusCode()).isEqualTo(200);
  }
  @Test void fullShiftPreTripDepartureExecutionPostTripAndOffDutyUseRealPorts() throws Exception {
    db.execute("UPDATE trip_stops SET stop_type='OTHER' WHERE id=?",stop);
    var path="/api/v1/tenants/"+tenant;
    String departure="{\"occurredAt\":\""+start.plusSeconds(2)+"\",\"nextStopId\":\""+stop+"\"}";
    assertProblem(http("driver","POST",path+"/trips/"+trip+"/departures",departure,UUID.randomUUID()),409,"RESOURCE_CONFLICT");
    var images=photos();assertThat(pre(images,trip,UUID.randomUUID()).statusCode()).isEqualTo(200);
    var detail=http("driver","GET",home()+"/trips/"+trip,null,null);assertThat(JsonPath.<Boolean>read(detail.body(),"$.departurePrerequisitesSatisfied")).isTrue();
    var first=http("driver","POST",path+"/trips/"+trip+"/departures",departure,UUID.randomUUID());assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);
    for(var action:List.of("arrivals","service-starts","completions")) {
      int seconds=action.equals("arrivals")?3:action.equals("service-starts")?4:5;
      String body="{\"occurredAt\":\""+start.plusSeconds(seconds)+"\""+(action.equals("arrivals")?",\"source\":\"MANUAL\"":"")+"}";
      var r=http("driver","POST",path+"/stops/"+stop+"/"+action,body,UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    }
    assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("COMPLETED");assertThat(db.value("SELECT off_duty_at IS NULL FROM shifts WHERE id=?",shift)).isEqualTo(true);
    String off="{\"occurredAt\":\""+start.plusSeconds(8)+"\",\"expectedShiftState\":\"ON_DUTY\"}";
    assertProblem(http("driver","POST",home()+"/shifts/"+shift+"/off-duty",off,UUID.randomUUID()),409,"RESOURCE_CONFLICT");
    String post="{\"shiftId\":\""+shift+"\",\"tripId\":\""+trip+"\",\"tractorId\":\""+tractor+"\",\"odometer\":124,\"damages\":\"Door scratch\",\"failures\":null,\"comments\":null,\"occurredAt\":\""+start.plusSeconds(7)+"\"}";
    UUID postKey=UUID.randomUUID();var end=http("driver","POST",home()+"/inspections/post-trip",post,postKey);assertThat(end.statusCode()).withFailMessage(end.body()).isEqualTo(200);assertThat(http("driver","POST",home()+"/inspections/post-trip",post,postKey).body()).isEqualTo(end.body());
    assertThat(http("driver","POST",home()+"/shifts/"+shift+"/off-duty",off,UUID.randomUUID()).statusCode()).isEqualTo(200);
    assertThat(db.value("SELECT damages FROM inspections WHERE tenant_id=? AND inspection_type='POST_TRIP'",tenant)).isEqualTo("Door scratch");
  }
  boolean canDepart() throws Exception {return JsonPath.<Boolean>read(http("driver","GET",home()+"/trips/"+trip,null,null).body(),"$.departurePrerequisitesSatisfied");}
  @Test void homeDeduplicatesOnlyCurrentlyAssignedNonterminalActiveEquipment() throws Exception {
    UUID duplicate=UUID.randomUUID(),terminal=UUID.randomUUID(),unassigned=UUID.randomUUID();
    db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,?,now(),now())",duplicate,tenant,driver,tractor);
    db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'COMPLETED',?,?,now(),now())",terminal,tenant,driver,db.tractor(tenant,"Old"));
    db.execute("INSERT INTO trips(id,tenant_id,status,current_tractor_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,now(),now())",unassigned,tenant,db.tractor(tenant,"Other"));
    var home=http("driver","GET",home(),null,null);assertThat(JsonPath.<List<?>>read(home.body(),"$.shiftContext.authorizedTractors")).hasSize(1);
    assertThat(JsonPath.<String>read(home.body(),"$.shiftContext.authorizedTractors[0].tractorId")).isEqualTo(tractor.toString());
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE tenant_id=? AND status='ASSIGNED'",tenant);
    assertThat(JsonPath.<List<?>>read(http("driver","GET",home(),null,null).body(),"$.shiftContext.authorizedTractors")).isEmpty();
    assertProblem(pre(photos(),null,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
  }
  @Test void noTripInspectionAuthorizesCurrentTractorButNeverAReassignedTractor() throws Exception {
    var images=photos();assertThat(pre(images,null,UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(canDepart()).isTrue();
    UUID replacement=db.tractor(tenant,"Replacement");db.execute("UPDATE trips SET current_tractor_id=? WHERE id=?",replacement,trip);assertThat(canDepart()).isFalse();
    assertProblem(pre(photos(),null,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE trips SET current_tractor_id=? WHERE id=?",tractor,trip);assertThat(canDepart()).isTrue();
  }
  @Test void explicitTripInspectionCannotAuthorizeAnotherTripSharingTheTractor() throws Exception {
    assertThat(pre(photos(),trip,UUID.randomUUID()).statusCode()).isEqualTo(200);
    UUID another=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,?,now(),now())",another,tenant,driver,tractor);
    assertThat(JsonPath.<Boolean>read(http("driver","GET",home()+"/trips/"+another,null,null).body(),"$.departurePrerequisitesSatisfied")).isFalse();
  }
  @Test void historicalPreTripNeverAuthorizesAnotherShift() throws Exception {
    assertThat(pre(photos(),null,UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(canDepart()).isTrue();
    db.execute("UPDATE shifts SET off_duty_at=?,updated_at=now() WHERE id=?",java.sql.Timestamp.from(start.plusSeconds(4)),shift);
    var r=http("driver","POST",home()+"/shifts/on-duty","{\"occurredAt\":\""+start.plusSeconds(5)+"\"}",UUID.randomUUID());assertThat(r.statusCode()).isEqualTo(200);assertThat(canDepart()).isFalse();
  }
  @Test void replayAndDocumentReadRevalidateRevokedMembershipAndCurrentAssignment() throws Exception {
    var images=photos();var key=UUID.randomUUID();assertThat(pre(images,trip,key).statusCode()).isEqualTo(200);
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);assertProblem(pre(images,trip,key),404,"RESOURCE_NOT_FOUND");
    assertProblem(http("driver","GET",base()+"/"+images[0],null,null),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
    assertThat(pre(images,trip,key).statusCode()).isEqualTo(403);
    assertThat(http("driver","GET",base()+"/"+images[0],null,null).statusCode()).isEqualTo(403);
  }
  @Test void concurrentPreTripsCannotReusePhotosOrCreatePartialHistory() throws Exception {
    var images=photos();var gate=new CountDownLatch(1);var entered=new CountDownLatch(2);
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<java.net.http.HttpResponse<String>> call=()->{entered.countDown();assertThat(gate.await(10,TimeUnit.SECONDS)).isTrue();return pre(images,trip,UUID.randomUUID());};
      var a=pool.submit(call);var b=pool.submit(call);assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();gate.countDown();
      assertThat(List.of(a.get(20,TimeUnit.SECONDS).statusCode(),b.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    } finally {gate.countDown();}
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM document_links WHERE tenant_id=? AND entity_type='INSPECTION'",tenant)).isEqualTo(4L);
  }
  @Test void anotherDriverPhotoAndForeignShiftCannotPassEvenWhenResourcesExistInTenant() throws Exception {
    var images=photos();UUID another=db.user(),anotherDriver=db.driver(tenant,another);
    db.membership(tenant,another,"ACTIVE","DRIVER");org.mockito.Mockito.when(verifier.verify("otherdriver")).thenReturn(new com.trailersaas.identity.VerifiedFirebaseIdentity("fixture-"+another,null,null));
    var upload=initialize(initBody("PRETRIP_FRONT"),"otherdriver");assertThat(upload.statusCode()).isEqualTo(201);var foreignPhoto=UUID.fromString(JsonPath.<String>read(upload.body(),"$.documentId"));
    peer.objects.put(DocumentService.temporary(tenant,foreignPhoto),new SignedS3TestServer.Blob(bytes,"image/jpeg"));assertThat(http("otherdriver","POST",base()+"/"+foreignPhoto+"/upload-completion","{\"checksumSha256\":null}",UUID.randomUUID()).statusCode()).isEqualTo(200);
    var original=images[0];images[0]=foreignPhoto;assertProblem(pre(images,trip,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    images[0]=original;UUID alienShift=UUID.randomUUID();
    db.execute("INSERT INTO shifts VALUES(?,?,?,now(),NULL,now(),now())",alienShift,tenant,anotherDriver);
    var body=preBody(images,trip).replace(shift.toString(),alienShift.toString());assertProblem(http("driver","POST",home()+"/inspections/pre-trip",body,UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void concurrentSamePreTripIdentityReplaysOneInspectionAndOnePhotoSet() throws Exception {
    var images=photos();var key=UUID.randomUUID();var release=new CountDownLatch(1);
    try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<java.net.http.HttpResponse<String>> call=()->{assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return pre(images,trip,key);};
      var a=pool.submit(call);var b=pool.submit(call);release.countDown();var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
      assertThat(first.statusCode()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    } finally {release.countDown();}
    assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void postTripFailureIsAtomicAndNoTripPathRemainsUsableAfterAssignedWorkFinishes() throws Exception {
    assertThat(pre(photos(),null,UUID.randomUUID()).statusCode()).isEqualTo(200);
    db.execute("UPDATE trips SET status='COMPLETED' WHERE id=?",trip);
    assertThat(JsonPath.<List<?>>read(http("driver","GET",home(),null,null).body(),"$.shiftContext.usedTractors")).hasSize(1);
    String body="{\"shiftId\":\""+shift+"\",\"tractorId\":\""+tractor+"\",\"odometer\":124,\"occurredAt\":\""+start.plusSeconds(3)+"\"}";
    var key=UUID.randomUUID();db.execute("CREATE FUNCTION fail_post() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.inspection_type='POST_TRIP' THEN RAISE EXCEPTION 'test-only'; END IF; RETURN NEW; END $$");db.execute("CREATE TRIGGER fail_post BEFORE INSERT ON inspections FOR EACH ROW EXECUTE FUNCTION fail_post()");
    try {assertThat(http("driver","POST",home()+"/inspections/post-trip",body,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT count(*) FROM inspections WHERE tenant_id=? AND inspection_type='POST_TRIP'",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);}
    finally {db.execute("DROP TRIGGER fail_post ON inspections");db.execute("DROP FUNCTION fail_post()");}
    assertThat(http("driver","POST",home()+"/inspections/post-trip",body,key).statusCode()).isEqualTo(200);
    assertThat(db.value("SELECT off_duty_at IS NULL FROM shifts WHERE id=?",shift)).isEqualTo(true);
  }
}
