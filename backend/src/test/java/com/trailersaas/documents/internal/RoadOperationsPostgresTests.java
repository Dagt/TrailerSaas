package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DocumentHttpPostgresFixture.Storage.class)
class RoadOperationsPostgresTests extends DocumentHttpPostgresFixture {
  UUID tractor,shift,order;
  Instant at;
  @BeforeEach void context() throws Exception {
    tractor=db.tractor(tenant,"Road-"+UUID.randomUUID());at=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    db.execute("UPDATE trips SET current_tractor_id=?,status='AT_STOP',started_at=? WHERE id=?",tractor,java.sql.Timestamp.from(at),trip);
    db.execute("UPDATE trip_stops SET status='IN_SERVICE',arrived_at=?,service_started_at=? WHERE id=?",java.sql.Timestamp.from(at.plusSeconds(1)),java.sql.Timestamp.from(at.plusSeconds(2)),stop);
    order=(UUID)db.value("SELECT order_id FROM trip_stop_orders WHERE trip_stop_id=?",stop);db.execute("UPDATE orders SET status='IN_PROGRESS' WHERE id=?",order);
    var on=http("driver","POST",home()+"/shifts/on-duty",time(0),UUID.randomUUID());assertThat(on.statusCode()).isEqualTo(200);shift=UUID.fromString(JsonPath.read(on.body(),"$.id"));
  }
  String root(){return "/api/v1/tenants/"+tenant;}
  String home(){return root()+"/driver/me";}
  String time(int second){return "{\"occurredAt\":\""+at.plusSeconds(second)+"\"}";}
  UUID image(String type) throws Exception {
    var init=initialize(initBody(type),"driver");assertThat(init.statusCode()).isEqualTo(201);
    UUID id=UUID.fromString(JsonPath.read(init.body(),"$.documentId"));
    var put=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(JsonPath.read(init.body(),"$.uploadUrl"))).header("Content-Type","image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());assertThat(put.statusCode()).isEqualTo(200);
    assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);return id;
  }
  UUID bol() throws Exception {var id=image("BOL");assertThat(link(id,stop).statusCode()).isEqualTo(200);return id;}
  String delivery(UUID bol){return "{\"evidenceMode\":\"SIGNED_BOL\",\"bolDocumentId\":\""+bol+"\",\"occurredAt\":\""+at.plusSeconds(3)+"\"}";}
  HttpResponse<String> deliver(String body,UUID key) throws Exception {return http("driver","POST",root()+"/stops/"+stop+"/delivery-completions",body,key);}
  @Test void signedBolCompletesRealStopOrderAndTripWithAtomicEvidenceAndReplay() throws Exception {
    UUID id=bol(),key=UUID.randomUUID();var body=delivery(id);var first=deliver(body,key);
    assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);assertThat(deliver(body,key).body()).isEqualTo(first.body());
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("COMPLETED");assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("COMPLETED");assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("DELIVERED");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=? AND event_type='COMPLETED'",tenant)).isEqualTo(1L);assertThat(db.value("SELECT count(*) FROM order_fulfillment_events WHERE tenant_id=?",tenant)).isEqualTo(1L);assertThat(db.value("SELECT off_duty_at IS NULL FROM shifts WHERE id=?",shift)).isEqualTo(true);
    assertThat(JsonPath.<String>read(first.body(),"$.stopStatus")).isEqualTo("COMPLETED");assertThat(first.body()).doesNotContain("tenantId","actorUserId","objectKey");
  }
  @Test void noReceiverRequiresBothDistinctFilesAndUnicodeNonblankComment() throws Exception {
    var bol=bol();var photo=image("DELIVERY_PHOTO");assertThat(link(photo,stop).statusCode()).isEqualTo(200);
    String body="{\"evidenceMode\":\"NO_RECEIVER_AVAILABLE\",\"bolDocumentId\":\""+bol+"\",\"deliveryPhotoDocumentId\":\""+photo+"\",\"comment\":\"No receiver\",\"occurredAt\":\""+at.plusSeconds(3)+"\"}";
    assertProblem(deliver(body.replace("No receiver","\u00a0\u2003"),UUID.randomUUID()),409,"EVIDENCE_REQUIRED");assertProblem(deliver(body.replace(photo.toString(),bol.toString()),UUID.randomUUID()),400,"VALIDATION_FAILED");
    assertProblem(deliver(body.replace("No receiver","\u0085"),UUID.randomUUID()),409,"EVIDENCE_REQUIRED");
    var r=deliver(body,UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);assertThat(db.value("SELECT evidence_mode FROM delivery_evidence WHERE trip_stop_id=?",stop)).isEqualTo("NO_RECEIVER_AVAILABLE");
  }
  @ParameterizedTest @ValueSource(strings={"PENDING","ARRIVED","EN_ROUTE","COMPLETED","CANCELLED"})
  void invalidStopStatesCannotProduceEvidence(String status) throws Exception {var body=delivery(bol());db.execute("UPDATE trip_stops SET status=? WHERE id=?",status,stop);assertProblem(deliver(body,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);}
  @ParameterizedTest @ValueSource(strings={"CANCELLING","CANCELLED","IN_TRANSIT","ASSIGNED"})
  void incompatibleTripCannotCompleteDelivery(String status) throws Exception {var body=delivery(bol());db.execute("UPDATE trips SET status=? WHERE id=?",status,trip);assertProblem(deliver(body,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");}
  @Test void pendingWrongTypeWrongStopAndForeignDocumentsReject() throws Exception {
    var pending=pending();assertThat(link(pending,stop).statusCode()).isEqualTo(409);assertProblem(deliver(delivery(pending),UUID.randomUUID()),409,"UPLOAD_NOT_COMPLETE");
    UUID pod=image("POD");assertThat(link(pod,stop).statusCode()).isEqualTo(200);assertProblem(deliver(delivery(pod),UUID.randomUUID()),400,"VALIDATION_FAILED");
    UUID unlinked=image("BOL");assertProblem(deliver(delivery(unlinked),UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");assertProblem(deliver(delivery(UUID.randomUUID()),UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
  }
  @Test void freshAssignmentAndMembershipBeforeLedgerReplay() throws Exception {
    String body=delivery(bol());UUID key=UUID.randomUUID();assertThat(deliver(body,key).statusCode()).isEqualTo(200);
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);assertProblem(deliver(body,key),404,"RESOURCE_NOT_FOUND");db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);
    db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);assertProblem(deliver(body,key),403,"TENANT_ACCESS_DENIED");
  }
  @Test void idempotencyBodyMismatchAndConcurrentLostAckDoNotDuplicate() throws Exception {
    var body=delivery(bol());UUID key=UUID.randomUUID();var latch=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(()->{latch.await();return deliver(body,key);});var b=pool.submit(()->{latch.await();return deliver(body,key);});latch.countDown();
      var first=a.get(30,TimeUnit.SECONDS);var second=b.get(30,TimeUnit.SECONDS);assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);assertThat(second.body()).isEqualTo(first.body());
    }
    assertProblem(deliver(body.replace(at.plusSeconds(3).toString(),at.plusSeconds(4).toString()),key),409,"IDEMPOTENCY_KEY_REUSED");assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void differentKeysForSameDeliveryHaveOneWinnerAndNoDuplicateFacts() throws Exception {
    String body=delivery(bol());var latch=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(()->{latch.await();return deliver(body,UUID.randomUUID());});
      var b=pool.submit(()->{latch.await();return deliver(body,UUID.randomUUID());});latch.countDown();
      assertThat(List.of(a.get(30,TimeUnit.SECONDS).statusCode(),b.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
    }
    for(String table:List.of("delivery_evidence","trip_stop_events","order_fulfillment_events","processed_client_operations"))
      assertThat(db.value("SELECT count(*) FROM "+table+" WHERE tenant_id=?"+(table.equals("processed_client_operations")?" AND operation_type='DELIVERY_COMPLETION'":""),tenant)).isEqualTo(1L);
  }
  @Test void invisibleDeliveryNeverWaitsForAnotherAssignmentsOrderLock() throws Exception {
    String body=delivery(bol());UUID key=UUID.randomUUID();db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);
    try(var executor=Executors.newVirtualThreadPerTaskExecutor();var connection=flyway.getConfiguration().getDataSource().getConnection();var q=connection.createStatement()) {
      connection.setAutoCommit(false);q.executeQuery("SELECT id FROM orders WHERE id='"+order+"' FOR UPDATE").close();
      var result=executor.submit(()->deliver(body,key));assertProblem(result.get(10,TimeUnit.SECONDS),404,"RESOURCE_NOT_FOUND");
    }
    assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"assignment","capability","cancellation","membership","linked-resource"})
  void deliveryRevalidatesChangesCommittedWhileWaitingForOrderLock(String change) throws Exception {
    UUID document=bol(),key=UUID.randomUUID();String body=delivery(document);
    try(var executor=Executors.newVirtualThreadPerTaskExecutor();var connection=flyway.getConfiguration().getDataSource().getConnection();var q=connection.createStatement()) {
      connection.setAutoCommit(false);q.executeQuery("SELECT id FROM orders WHERE id='"+order+"' FOR UPDATE").close();
      int blocker;try(var pid=q.executeQuery("SELECT pg_backend_pid()")){pid.next();blocker=pid.getInt(1);}
      var result=executor.submit(()->deliver(body,key));
      org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(()->
        assertThat((Long)db.value("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%orders%' AND ?=ANY(pg_blocking_pids(pid))",blocker)).isGreaterThan(0L));
      switch(change) {
        case "assignment" -> db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);
        case "capability" -> db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        case "cancellation" -> q.executeUpdate("UPDATE orders SET status='CANCELLING' WHERE id='"+order+"'");
        case "membership" -> db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
        case "linked-resource" -> {
          UUID hidden=UUID.randomUUID(),hiddenStop=UUID.randomUUID();
          db.execute("INSERT INTO trips(id,tenant_id,status,created_at,updated_at) VALUES(?,?,'ASSIGNED',now(),now())",hidden,tenant);
          db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'DELIVERY','PENDING','{\"name\":\"Hidden\"}',now(),now())",hiddenStop,tenant,hidden);
          db.execute("INSERT INTO document_links(id,tenant_id,document_id,entity_type,entity_id,created_at) VALUES(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,document,hiddenStop);
        }
        default -> throw new AssertionError(change);
      }
      connection.commit();
      assertProblem(result.get(30,TimeUnit.SECONDS),Set.of("assignment","linked-resource").contains(change)?404:Set.of("capability","membership").contains(change)?403:409,
        switch(change){case "assignment","linked-resource" -> "RESOURCE_NOT_FOUND";case "capability" -> "CAPABILITY_DENIED";case "membership" -> "TENANT_ACCESS_DENIED";default -> "INVALID_STATE_TRANSITION";});
    }
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    for(String table:List.of("delivery_evidence","trip_stop_events","order_fulfillment_events"))assertThat(db.value("SELECT count(*) FROM "+table+" WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"trip_stops","trip_stop_events","order_fulfillment_events","delivery_evidence"})
  void injectedFailureRollsBackStopEventOrdersTripEvidenceAndLedger(String table) throws Exception {
    var body=delivery(bol());UUID key=UUID.randomUUID();
    db.execute("CREATE FUNCTION road_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only transaction failure'; END $$");db.execute("CREATE TRIGGER road_fail "+(table.equals("trip_stops")?"AFTER UPDATE":"BEFORE INSERT")+" ON "+table+" FOR EACH ROW EXECUTE FUNCTION road_fail()");
    try {
      assertThat(deliver(body,key).statusCode()).isGreaterThanOrEqualTo(500);
      assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("IN_PROGRESS");assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("AT_STOP");
      for(String item:List.of("trip_stop_events","delivery_evidence","order_fulfillment_events"))assertThat(db.value("SELECT count(*) FROM "+item+" WHERE tenant_id=?",tenant)).isEqualTo(0L);
      assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
    } finally {db.execute("DROP TRIGGER road_fail ON "+table);db.execute("DROP FUNCTION road_fail()");}
    assertThat(deliver(body,key).statusCode()).isEqualTo(200);
  }
  @Test void baseReturnAndOtherDeliveryObligationsPreventPrematureClosure() throws Exception {
    UUID second=UUID.randomUUID();db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'BASE_RETURN','PENDING','{\"name\":\"Base\"}',now(),now())",second,tenant,trip);
    var requested=UUID.randomUUID();db.execute("INSERT INTO order_stops(id,tenant_id,order_id,sequence_number,stop_type,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'DELIVERY','{\"name\":\"Other destination\"}',now(),now())",requested,tenant,order);
    assertThat(deliver(delivery(bol()),UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("AT_STOP");assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("PARTIALLY_DELIVERED");
  }
  @Test void twoRealDeliveriesSatisfyTwoRequestedVisitsAndCompleteOrderOnlyAfterBoth() throws Exception {
    UUID second=UUID.randomUUID();db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'DELIVERY','PENDING','{\"name\":\"Frozen\"}',now(),now())",second,tenant,trip);
    db.execute("INSERT INTO trip_stop_orders VALUES(?,?,?,?,NULL)",UUID.randomUUID(),tenant,second,order);
    for(int sequence:List.of(1,2))db.execute("INSERT INTO order_stops(id,tenant_id,order_id,sequence_number,stop_type,site_snapshot,created_at,updated_at) VALUES(?,?,?,?,'DELIVERY','{\"name\":\"Frozen\"}',now(),now())",UUID.randomUUID(),tenant,order,sequence);
    assertThat(deliver(delivery(bol()),UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("PARTIALLY_DELIVERED");
    var departure=http("driver","POST",root()+"/stops/"+stop+"/departures","{\"occurredAt\":\""+at.plusSeconds(4)+"\",\"nextStopId\":\""+second+"\"}",UUID.randomUUID());assertThat(departure.statusCode()).withFailMessage(departure.body()).isEqualTo(200);
    for(var action:List.of("arrivals","service-starts")){var r=http("driver","POST",root()+"/stops/"+second+"/"+action,"{\"occurredAt\":\""+at.plusSeconds(action.equals("arrivals")?5:6)+"\""+(action.equals("arrivals")?",\"source\":\"MANUAL\"":"")+"}",UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);}
    UUID image=image("BOL");assertThat(link(image,second).statusCode()).isEqualTo(200);
    var r=http("driver","POST",root()+"/stops/"+second+"/delivery-completions",delivery(image).replace(at.plusSeconds(3).toString(),at.plusSeconds(7).toString()),UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("DELIVERED");assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("COMPLETED");assertThat(db.value("SELECT count(*) FROM order_fulfillment_events WHERE order_id=?",order)).isEqualTo(2L);
  }
  @Test void oneVisitCannotFulfillTwoRequestedVisitsAtSameDestination() throws Exception {
    for(int sequence:List.of(1,2))db.execute("INSERT INTO order_stops(id,tenant_id,order_id,sequence_number,stop_type,site_snapshot,created_at,updated_at) VALUES(?,?,?,?,'DELIVERY','{\"name\":\"Frozen\"}',now(),now())",UUID.randomUUID(),tenant,order,sequence);
    assertThat(deliver(delivery(bol()),UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("PARTIALLY_DELIVERED");
  }
  @Test void sharedStopFulfillsOnlyItsTwoMappedOrdersWithoutAdministrativeClose() throws Exception {
    UUID second=mapOrder(customerB);db.execute("UPDATE orders SET status='IN_PROGRESS' WHERE id=?",second);
    assertThat(deliver(delivery(bol()),UUID.randomUUID()).statusCode()).isEqualTo(200);assertThat(db.value("SELECT status FROM orders WHERE id=?",second)).isEqualTo("DELIVERED");assertThat(db.value("SELECT count(*) FROM order_fulfillment_events WHERE tenant_id=?",tenant)).isEqualTo(2L);assertThat(db.value("SELECT count(*) FROM orders WHERE tenant_id=? AND completed_at IS NOT NULL",tenant)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"CANCELLING","CANCELLED"}) void cancellingOrdersNeverSilentlyFulfill(String status) throws Exception {
    var body=delivery(bol());db.execute("UPDATE orders SET status=?,cancelled_at=CASE WHEN ?='CANCELLED' THEN now() ELSE NULL END WHERE id=?",status,status,order);
    assertProblem(deliver(body,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void commitFailureAfterEvidenceInsertStillRevertsWholeTransaction() throws Exception {
    var body=delivery(bol());UUID key=UUID.randomUUID();
    db.execute("CREATE FUNCTION road_commit_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only deferred commit failure'; END $$");db.execute("CREATE CONSTRAINT TRIGGER road_commit_fail AFTER INSERT ON delivery_evidence DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION road_commit_fail()");
    try {assertThat(deliver(body,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("IN_PROGRESS");assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);}
    finally {db.execute("DROP TRIGGER road_commit_fail ON delivery_evidence");db.execute("DROP FUNCTION road_commit_fail()");}
    assertThat(deliver(body,key).statusCode()).isEqualTo(200);
  }
  String fuelBody(UUID receipt){return "{\"tripId\":\""+trip+"\",\"tractorId\":\""+tractor+"\",\"odometer\":123.5,\"receiptDocumentId\":\""+receipt+"\",\"occurredAt\":\""+at.plusSeconds(3)+"\"}";}
  UUID receipt() throws Exception {UUID id=image("FUEL_RECEIPT");var r=http("driver","POST",base()+"/"+id+"/links","{\"entityType\":\"TRIP\",\"entityId\":\""+trip+"\"}",null);assertThat(r.statusCode()).isEqualTo(200);return id;}
  HttpResponse<String> fuel(String body,UUID key) throws Exception{return http("driver","POST",home()+"/fuel-events",body,key);}
  @Test void fuelReceiptAndEventPersistOnceWithoutChangingOperationalStates() throws Exception {
    String body=fuelBody(receipt());UUID key=UUID.randomUUID();var r=fuel(body,key);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);assertThat(fuel(body,key).body()).isEqualTo(r.body());assertThat(db.value("SELECT count(*) FROM fuel_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("AT_STOP");assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("IN_PROGRESS");assertThat(r.body()).doesNotContain("cost","price","gallons","tenantId","driverId");
    assertProblem(fuel(body,UUID.randomUUID()),409,"RESOURCE_CONFLICT");assertProblem(fuel(body.replace("123.5","124"),key),409,"IDEMPOTENCY_KEY_REUSED");
  }
  @Test void fuelWithoutShiftWrongTractorForeignReceiptOrPermissionFailsSafely() throws Exception {
    var body=fuelBody(receipt());assertProblem(fuel(body.replace(tractor.toString(),UUID.randomUUID().toString()),UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");assertProblem(fuel(body.replace(JsonPath.<String>read(body,"$.receiptDocumentId"),UUID.randomUUID().toString()),UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE shifts SET off_duty_at=now() WHERE id=?",shift);assertProblem(fuel(body,UUID.randomUUID()),409,"RESOURCE_CONFLICT");db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);assertProblem(fuel(body,UUID.randomUUID()),403,"CAPABILITY_DENIED");assertThat(db.value("SELECT count(*) FROM fuel_events WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void fuelInsertFailureRetainsReceiptAndAllowsSameKeyRecovery() throws Exception {
    UUID receipt=receipt(),key=UUID.randomUUID();String body=fuelBody(receipt);
    db.execute("CREATE FUNCTION road_fuel_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only'; END $$");db.execute("CREATE TRIGGER road_fuel_fail BEFORE INSERT ON fuel_events FOR EACH ROW EXECUTE FUNCTION road_fuel_fail()");
    try {assertThat(fuel(body,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT count(*) FROM fuel_events WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT upload_status FROM documents WHERE id=?",receipt)).isEqualTo("UPLOADED");}
    finally {db.execute("DROP TRIGGER road_fuel_fail ON fuel_events");db.execute("DROP FUNCTION road_fuel_fail()");}
    assertThat(fuel(body,key).statusCode()).isEqualTo(200);
  }
  String problemBody(String category,List<UUID> images){return "{\"stopId\":\""+stop+"\",\"category\":\""+category+"\",\"comment\":\"Reported problem\",\"documentIds\":"+images.stream().map(i->"\""+i+"\"").collect(java.util.stream.Collectors.joining(",","[","]"))+",\"occurredAt\":\""+at.plusSeconds(3)+"\"}";}
  HttpResponse<String> problem(String body,UUID key) throws Exception{return http("driver","POST",root()+"/trips/"+trip+"/exceptions",body,key);}
  @ParameterizedTest @ValueSource(strings={"DELAY_TRAFFIC","DESTINATION_CUSTOMER_ISSUE","LOAD_REJECTED","OTHER"})
  void nondamageCategoryRecordsFactWithOptionalStopWithoutStateEditor(String category) throws Exception {
    String body=problemBody(category,List.of()).replace("\"stopId\":\""+stop+"\"","\"stopId\":null");UUID key=UUID.randomUUID();var r=problem(body,key);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);assertThat(problem(body,key).body()).isEqualTo(r.body());assertThat(db.value("SELECT count(*) FROM operational_exceptions WHERE tenant_id=?",tenant)).isEqualTo(1L);assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("AT_STOP");assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
  }
  @Test void equipmentDamageRequiresOwnFinalizedPhotosAndNeverMarksOos() throws Exception {
    assertProblem(problem(problemBody("EQUIPMENT_DAMAGE",List.of()),UUID.randomUUID()),409,"EVIDENCE_REQUIRED");UUID id=image("DAMAGE_PHOTO");assertThat(link(id,stop).statusCode()).isEqualTo(200);String body=problemBody("EQUIPMENT_DAMAGE",List.of(id));var r=problem(body,UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);assertThat(db.value("SELECT count(*) FROM operational_exception_documents WHERE tenant_id=?",tenant)).isEqualTo(1L);assertThat(db.value("SELECT count(*) FROM trailer_movement_events WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertProblem(problem(body.replace("Reported problem","\u00a0\u2003"),UUID.randomUUID()),400,"VALIDATION_FAILED");assertProblem(problem(body.replace("\"category\":", "\"status\":\"COMPLETED\",\"category\":"),UUID.randomUUID()),400,"VALIDATION_FAILED");
  }
  @Test void allThreeEndpointsUseStrictRequestsAndSafeAuthorization() throws Exception {
    for(String path:List.of(root()+"/stops/"+stop+"/delivery-completions",home()+"/fuel-events",root()+"/trips/"+trip+"/exceptions")) {
      assertProblem(http(null,"POST",path,"{}",UUID.randomUUID()),401,"AUTHENTICATION_REQUIRED");assertProblem(http("driver","POST",path,"{\"tenantId\":\""+other+"\"}",UUID.randomUUID()),400,"VALIDATION_FAILED");assertProblem(http("driver","POST",path+"?unexpected=true","{}",UUID.randomUUID()),400,"VALIDATION_FAILED");
    }
  }
  @Test void realShiftPreTripTwoStopRoadPodOrderPostTripOffDutyLifecycle() throws Exception {
    db.execute("UPDATE trips SET status='ASSIGNED',started_at=NULL WHERE id=?",trip);db.execute("UPDATE trip_stops SET status='PENDING',arrived_at=NULL,service_started_at=NULL WHERE id=?",stop);db.execute("UPDATE orders SET status='PLANNED' WHERE id=?",order);
    UUID back=UUID.randomUUID();db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'BASE_RETURN','PENDING','{\"name\":\"Base\"}',now(),now())",back,tenant,trip);
    UUID front=image("PRETRIP_FRONT"),rear=image("PRETRIP_REAR"),left=image("PRETRIP_DRIVER_SIDE"),right=image("PRETRIP_PASSENGER_SIDE");
    String pre="{\"shiftId\":\""+shift+"\",\"tripId\":\""+trip+"\",\"tractorId\":\""+tractor+"\",\"odometer\":100,\"checklist\":{\"items\":[]},\"documentIds\":{\"front\":\""+front+"\",\"rear\":\""+rear+"\",\"driverSide\":\""+left+"\",\"passengerSide\":\""+right+"\"},\"occurredAt\":\""+at.plusSeconds(1)+"\"}";
    var r=http("driver","POST",home()+"/inspections/pre-trip",pre,UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    r=http("driver","POST",root()+"/trips/"+trip+"/departures","{\"occurredAt\":\""+at.plusSeconds(5)+"\",\"nextStopId\":\""+stop+"\"}",UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("IN_PROGRESS");
    for(var action:List.of("arrivals","service-starts")){r=http("driver","POST",root()+"/stops/"+stop+"/"+action,"{\"occurredAt\":\""+at.plusSeconds(action.equals("arrivals")?6:7)+"\""+(action.equals("arrivals")?",\"source\":\"MANUAL\"":"")+"}",UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);}
    UUID key=UUID.randomUUID();String body=delivery(bol()).replace(at.plusSeconds(3).toString(),at.plusSeconds(8).toString());var completed=deliver(body,key);assertThat(completed.statusCode()).withFailMessage(completed.body()).isEqualTo(200);assertThat(db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("DELIVERED");assertThat(db.value("SELECT status FROM trips WHERE id=?",trip)).isEqualTo("AT_STOP");
    r=http("driver","POST",root()+"/stops/"+stop+"/departures","{\"occurredAt\":\""+at.plusSeconds(9)+"\",\"nextStopId\":\""+back+"\"}",UUID.randomUUID());assertThat(r.statusCode()).isEqualTo(200);
    for(var action:List.of("arrivals","service-starts","completions")){r=http("driver","POST",root()+"/stops/"+back+"/"+action,"{\"occurredAt\":\""+at.plusSeconds(action.equals("arrivals")?10:action.equals("service-starts")?11:12)+"\""+(action.equals("arrivals")?",\"source\":\"MANUAL\"":"")+"}",UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);}
    assertThat(deliver(body,key).body()).isEqualTo(completed.body());assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
    r=http("driver","POST",home()+"/inspections/post-trip","{\"shiftId\":\""+shift+"\",\"tripId\":\""+trip+"\",\"tractorId\":\""+tractor+"\",\"odometer\":110,\"occurredAt\":\""+at.plusSeconds(13)+"\"}",UUID.randomUUID());assertThat(r.statusCode()).isEqualTo(200);
    r=http("driver","POST",home()+"/shifts/"+shift+"/off-duty","{\"expectedShiftState\":\"ON_DUTY\",\"occurredAt\":\""+at.plusSeconds(14)+"\"}",UUID.randomUUID());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
  }
  @Test void committedFuelAndDamageReplayAfterTripEndsButStillRevalidateAssignment() throws Exception {
    String fuelBody=fuelBody(receipt());UUID fuelKey=UUID.randomUUID();var fuel=fuel(fuelBody,fuelKey);assertThat(fuel.statusCode()).isEqualTo(200);
    UUID photo=image("DAMAGE_PHOTO");assertThat(link(photo,stop).statusCode()).isEqualTo(200);String problem=problemBody("EQUIPMENT_DAMAGE",List.of(photo));UUID problemKey=UUID.randomUUID();var reported=problem(problem,problemKey);assertThat(reported.statusCode()).isEqualTo(200);
    assertThat(deliver(delivery(bol()),UUID.randomUUID()).statusCode()).isEqualTo(200);
    assertThat(fuel(fuelBody,fuelKey).body()).isEqualTo(fuel.body());assertThat(problem(problem,problemKey).body()).isEqualTo(reported.body());assertThat(fuel(fuelBody.replace("123.5","123.5000"),fuelKey).body()).isEqualTo(fuel.body());
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);assertProblem(fuel(fuelBody,fuelKey),404,"RESOURCE_NOT_FOUND");assertProblem(problem(problem,problemKey),404,"RESOURCE_NOT_FOUND");
  }
  @ParameterizedTest @ValueSource(strings={"SIGNED_BOL","NO_RECEIVER_AVAILABLE"}) void missingRequiredBolProducesNoEffects(String mode) throws Exception {
    assertProblem(deliver("{\"evidenceMode\":\""+mode+"\",\"occurredAt\":\""+at.plusSeconds(3)+"\"}",UUID.randomUUID()),409,"EVIDENCE_REQUIRED");assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"PICKUP","OTHER","LIVE_UNLOAD","DROP_HOOK"}) void nondeliveryStopCannotUsePodRoute(String type) throws Exception {
    String body=delivery(bol());db.execute("UPDATE trip_stops SET stop_type=? WHERE id=?",type,stop);assertProblem(deliver(body,UUID.randomUUID()),409,"INVALID_STATE_TRANSITION");
  }
  @Test void currentDriverAndPermissionsCannotBeReplacedByUploaderIdentity() throws Exception {
    String body=delivery(bol());UUID key=UUID.randomUUID();db.execute("UPDATE drivers SET active=false WHERE id=?",driver);assertProblem(deliver(body,key),403,"CAPABILITY_DENIED");db.execute("UPDATE drivers SET active=true WHERE id=?",driver);db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);assertProblem(deliver(body,key),403,"CAPABILITY_DENIED");assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);
  }
  @Test void fuelWrongDocumentTypeAndPendingReceiptAreNeverAccepted() throws Exception {
    UUID id=image("BOL");assertThat(link(id,stop).statusCode()).isEqualTo(200);assertProblem(fuel(fuelBody(id),UUID.randomUUID()),400,"VALIDATION_FAILED");
    var pending=initialize(initBody("FUEL_RECEIPT"),"driver");UUID receipt=UUID.fromString(JsonPath.read(pending.body(),"$.documentId"));assertProblem(fuel(fuelBody(receipt),UUID.randomUUID()),409,"UPLOAD_NOT_COMPLETE");
  }
  @Test void concurrentFuelCommandsSameReceiptHaveOneDefinitiveWinner() throws Exception {
    String body=fuelBody(receipt());var latch=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {var a=pool.submit(()->{latch.await();return fuel(body,UUID.randomUUID());});var b=pool.submit(()->{latch.await();return fuel(body,UUID.randomUUID());});latch.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS).statusCode(),b.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);}
    assertThat(db.value("SELECT count(*) FROM fuel_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void receiptUniquenessAcrossDifferentTripLocksIsAStableBusinessConflict() throws Exception {
    UUID second=UUID.randomUUID(),receipt=receipt();
    db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'AT_STOP',?,?,now(),now())",second,tenant,driver,tractor);
    assertThat(http("driver","POST",base()+"/"+receipt+"/links","{\"entityType\":\"TRIP\",\"entityId\":\""+second+"\"}",null).statusCode()).isEqualTo(200);
    String body=fuelBody(receipt);var latch=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(()->{latch.await();return fuel(body,UUID.randomUUID());});
      var b=pool.submit(()->{latch.await();return fuel(body.replace(trip.toString(),second.toString()),UUID.randomUUID());});latch.countDown();
      var results=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
      assertThat(results.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,409);
      assertProblem(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"RESOURCE_CONFLICT");
    }
    assertThat(db.value("SELECT count(*) FROM fuel_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE tenant_id=? AND operation_type='FUEL_EVENT'",tenant)).isEqualTo(1L);
  }
  @Test void exceptionsCannotUseAnotherTripStopOrCreatePartialDocumentEdges() throws Exception {
    UUID another=UUID.randomUUID(),foreignStop=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,now(),now())",another,tenant,driver);db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'OTHER','PENDING','{\"name\":\"Other\"}',now(),now())",foreignStop,tenant,another);
    assertProblem(problem(problemBody("OTHER",List.of()).replace(stop.toString(),foreignStop.toString()),UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
    UUID photo=image("DAMAGE_PHOTO");assertThat(link(photo,stop).statusCode()).isEqualTo(200);String body=problemBody("EQUIPMENT_DAMAGE",List.of(photo));UUID key=UUID.randomUUID();db.execute("CREATE FUNCTION road_edge_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only'; END $$");db.execute("CREATE TRIGGER road_edge_fail BEFORE INSERT ON operational_exception_documents FOR EACH ROW EXECUTE FUNCTION road_edge_fail()");
    try {assertThat(problem(body,key).statusCode()).isGreaterThanOrEqualTo(500);assertThat(db.value("SELECT count(*) FROM operational_exceptions WHERE tenant_id=?",tenant)).isEqualTo(0L);assertThat(db.value("SELECT count(*) FROM processed_client_operations WHERE client_operation_id=?",key)).isEqualTo(0L);}finally{db.execute("DROP TRIGGER road_edge_fail ON operational_exception_documents");db.execute("DROP FUNCTION road_edge_fail()");}
    assertThat(problem(body,key).statusCode()).isEqualTo(200);
  }
  @Test void opposingForeignStopRequestsRejectWithoutAcquiringAnotherTripsLock() throws Exception {
    UUID second=UUID.randomUUID(),secondStop=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,now(),now())",second,tenant,driver);db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'OTHER','PENDING','{\"name\":\"Other\"}',now(),now())",secondStop,tenant,second);
    var latch=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(()->{latch.await();return problem(problemBody("OTHER",List.of()).replace(stop.toString(),secondStop.toString()),UUID.randomUUID());});
      var b=pool.submit(()->{latch.await();return http("driver","POST",root()+"/trips/"+second+"/exceptions",problemBody("OTHER",List.of()),UUID.randomUUID());});latch.countDown();
      assertProblem(a.get(30,TimeUnit.SECONDS),404,"RESOURCE_NOT_FOUND");assertProblem(b.get(30,TimeUnit.SECONDS),404,"RESOURCE_NOT_FOUND");
    }
    assertThat(db.value("SELECT count(*) FROM operational_exceptions WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
}
