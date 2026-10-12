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
}
