package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import com.trailersaas.documents.*;
import com.trailersaas.identity.*;
import com.trailersaas.TrailerFixtures;
import com.jayway.jsonpath.JsonPath;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import static com.trailersaas.documents.OperationalEvidencePolicy.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({DocumentHttpPostgresFixture.Storage.class,EvidencePostgresTests.Harness.class})
class EvidencePostgresTests extends DocumentHttpPostgresFixture {
  @Autowired OperationalEvidencePolicy policy;
  @Autowired PlatformTransactionManager manager;
  @Autowired Harness workflow;
  UUID tractor;
  @BeforeEach void operationalContext() throws Exception {
    tractor=db.tractor(tenant,"Evidence-"+UUID.randomUUID());
    db.execute("UPDATE trips SET current_tractor_id=?,status='AT_STOP',started_at=now() WHERE id=?",tractor,trip);
    db.execute("UPDATE trip_stops SET status='IN_SERVICE',arrived_at=now(),service_started_at=now() WHERE id=?",stop);
    workflow.beforeCompletion=()->{};
  }
  AuthenticatedUserPrincipal actor() { return new AuthenticatedUserPrincipal(user,"fixture-"+user,null,null); }
  void tx(Runnable action) { new TransactionTemplate(manager).executeWithoutResult(s->action.run()); }
  void rejected(Runnable action,EvidenceValidationException.Code code) {
    assertThatThrownBy(()->tx(action)).isInstanceOfSatisfying(EvidenceValidationException.class,e->{assertThat(e.code()).isEqualTo(code);assertThat(e.getMessage()).doesNotContain(tenant.toString(),peer.secret,"tenant/");});
  }
  UUID document(String type,DocumentContracts.Target target,UUID resource) throws Exception {
    var response=initialize(initBody(type),"driver");assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201);
    var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    peer.objects.put(DocumentService.temporary(tenant,id),new SignedS3TestServer.Blob(bytes,"image/jpeg"));
    assertThat(complete(id,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
    if(target==null) return id;
    var linked=http("driver","POST",base()+"/"+id+"/links","{\"entityType\":\""+target+"\",\"entityId\":\""+resource+"\"}",null);
    assertThat(linked.statusCode()).withFailMessage(linked.body()).isEqualTo(200);return id;
  }
  PreTripPhotos photos() throws Exception {
    return new PreTripPhotos(document("PRETRIP_FRONT",DocumentContracts.Target.TRIP,trip),document("PRETRIP_REAR",DocumentContracts.Target.TRIP,trip),
        document("PRETRIP_DRIVER_SIDE",DocumentContracts.Target.TRIP,trip),document("PRETRIP_PASSENGER_SIDE",DocumentContracts.Target.TRIP,trip));
  }
  Delivery signed() throws Exception { return new Delivery(Mode.SIGNED_BOL,document("BOL",DocumentContracts.Target.TRIP_STOP,stop),null,null); }
  Delivery noReceiver() throws Exception { return new Delivery(Mode.NO_RECEIVER_AVAILABLE,document("BOL",DocumentContracts.Target.TRIP_STOP,stop),document("DELIVERY_PHOTO",DocumentContracts.Target.TRIP_STOP,stop),"  No receiver available  "); }
  void pretrip(PreTripPhotos p) { policy.validatePreTrip(actor(),tenant,trip,tractor,p); }
  @Test void fourPreTripImagesValidateRealAssignedContextWithoutCreatingInspectionOrShift() throws Exception {
    var p=photos();tx(()->pretrip(p));assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void attachmentFreePreTripSetSupportsFutureInspectionWithoutInventedTrip() throws Exception {
    var uploads=new PreTripPhotos(document("PRETRIP_FRONT",null,null),document("PRETRIP_REAR",null,null),document("PRETRIP_DRIVER_SIDE",null,null),document("PRETRIP_PASSENGER_SIDE",null,null));
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);
    tx(()->policy.validatePreTripUploads(actor(),tenant,uploads));
    assertThat(db.value("SELECT count(*) FROM document_links WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertProblem(http("driver","POST",base()+"/"+uploads.front()+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);
    assertThat(http("driver","POST",base()+"/"+uploads.front()+"/links","{\"entityType\":\"TRIP\",\"entityId\":\""+trip+"\"}",null).statusCode()).isEqualTo(200);
    rejected(()->policy.validatePreTripUploads(actor(),tenant,uploads),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
  }
  @Test void attachmentFreeFuelReceiptDoesNotApproveOrCreateFutureShiftFuelEvent() throws Exception {
    var receipt=document("FUEL_RECEIPT",null,null);tx(()->policy.validateFuelUpload(actor(),tenant,receipt));
    assertThat(db.value("SELECT count(*) FROM document_links WHERE document_id=?",receipt)).isEqualTo(0L);
    assertThat(http("driver","POST",base()+"/"+receipt+"/links","{\"entityType\":\"TRIP\",\"entityId\":\""+trip+"\"}",null).statusCode()).isEqualTo(200);
    rejected(()->policy.validateFuelUpload(actor(),tenant,receipt),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
  }
  @ParameterizedTest @ValueSource(ints={0,1,2,3})
  void eachMissingPreTripPositionRejects(int missing) throws Exception {
    var p=photos();var ids=new UUID[]{p.front(),p.rear(),p.driverSide(),p.passengerSide()};ids[missing]=null;
    rejected(()->pretrip(new PreTripPhotos(ids[0],ids[1],ids[2],ids[3])),EvidenceValidationException.Code.EVIDENCE_REQUIRED);
  }
  @Test void duplicateAndInterchangedPreTripPositionsReject() throws Exception {
    var p=photos();rejected(()->pretrip(new PreTripPhotos(p.front(),p.front(),p.driverSide(),p.passengerSide())),EvidenceValidationException.Code.VALIDATION_FAILED);
    rejected(()->pretrip(new PreTripPhotos(p.rear(),p.front(),p.driverSide(),p.passengerSide())),EvidenceValidationException.Code.VALIDATION_FAILED);
  }
  @ParameterizedTest @ValueSource(strings={"PENDING","FAILED"})
  void preTripCannotApproveIncompleteDocument(String state) throws Exception {
    var p=photos();var response=initialize(initBody("PRETRIP_FRONT"),"driver");var id=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    db.execute("UPDATE documents SET upload_status=? WHERE id=?",state,id);
    rejected(()->pretrip(new PreTripPhotos(id,p.rear(),p.driverSide(),p.passengerSide())),EvidenceValidationException.Code.UPLOAD_NOT_COMPLETE);
  }
  @Test void preTripRequiresOwnTenantUploaderAndExactTripContext() throws Exception {
    var p=photos();var foreign=UUID.randomUUID();
    db.execute("INSERT INTO documents VALUES(?,?,?,'PRETRIP_FRONT','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",foreign,other,"foreign/"+foreign,"0".repeat(64),user);
    rejected(()->pretrip(new PreTripPhotos(foreign,p.rear(),p.driverSide(),p.passengerSide())),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    var stranger=UUID.randomUUID();db.execute("INSERT INTO documents VALUES(?,?,?,'PRETRIP_FRONT','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",stranger,tenant,"owned-other/"+stranger,"0".repeat(64),admin);
    rejected(()->pretrip(new PreTripPhotos(stranger,p.rear(),p.driverSide(),p.passengerSide())),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    var otherTrip=UUID.randomUUID();db.execute("INSERT INTO trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at) VALUES(?,?,'ASSIGNED',?,?,now(),now())",otherTrip,tenant,driver,tractor);
    rejected(()->policy.validatePreTrip(actor(),tenant,otherTrip,tractor,p),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    rejected(()->policy.validatePreTrip(actor(),tenant,trip,UUID.randomUUID(),p),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    db.execute("UPDATE tractors SET active=false WHERE id=?",tractor);rejected(()->pretrip(p),EvidenceValidationException.Code.RESOURCE_CONFLICT);
  }
  @Test void preTripRevalidatesRoleAndAssignment() throws Exception {
    var p=photos();db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);rejected(()->pretrip(p),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);
    db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    assertThatThrownBy(()->tx(()->pretrip(p))).hasMessage("The requested action is not permitted.");
  }
  @ParameterizedTest @EnumSource(Mode.class)
  void bothDeliveryModesValidateFinalizedExactStopDocuments(Mode mode) throws Exception {
    var request=mode==Mode.SIGNED_BOL?signed():noReceiver();tx(()->policy.validateDelivery(actor(),tenant,stop,request));
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"SIGNED_MISSING_BOL","NO_BOL","NO_PHOTO","NO_COMMENT","BLANK_COMMENT","DUPLICATE","WRONG_MODE"})
  void missingOrMalformedDeliveryEvidenceRejects(String variant) throws Exception {
    var valid=noReceiver();Delivery request=switch(variant) {
      case "SIGNED_MISSING_BOL" -> new Delivery(Mode.SIGNED_BOL,null,null,null);
      case "NO_BOL" -> new Delivery(Mode.NO_RECEIVER_AVAILABLE,null,valid.deliveryPhotoDocumentId(),"comment");
      case "NO_PHOTO" -> new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),null,"comment");
      case "NO_COMMENT" -> new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),valid.deliveryPhotoDocumentId(),null);
      case "BLANK_COMMENT" -> new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),valid.deliveryPhotoDocumentId()," \t\n ");
      case "DUPLICATE" -> new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),valid.bolDocumentId(),"comment");
      default -> new Delivery(null,valid.bolDocumentId(),null,null);
    };
    var code=Set.of("DUPLICATE","WRONG_MODE").contains(variant)?EvidenceValidationException.Code.VALIDATION_FAILED:EvidenceValidationException.Code.EVIDENCE_REQUIRED;
    rejected(()->policy.validateDelivery(actor(),tenant,stop,request),code);
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @ParameterizedTest @ValueSource(strings={"POD","DELIVERY_PHOTO","DAMAGE_PHOTO"})
  void signedBolCannotBeReplacedByOtherDocumentType(String type) throws Exception {
    var id=document(type,DocumentContracts.Target.TRIP_STOP,stop);
    rejected(()->policy.validateDelivery(actor(),tenant,stop,new Delivery(Mode.SIGNED_BOL,id,null,null)),EvidenceValidationException.Code.VALIDATION_FAILED);
  }
  @Test void deliveryRejectsPendingWrongStopAndForeignStop() throws Exception {
    var pending=pending();rejected(()->policy.validateDelivery(actor(),tenant,stop,new Delivery(Mode.SIGNED_BOL,pending,null,null)),EvidenceValidationException.Code.UPLOAD_NOT_COMPLETE);
    var valid=signed();var second=UUID.randomUUID();db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,2,'DELIVERY','IN_SERVICE','{\"name\":\"Second\"}',now(),now())",second,tenant,trip);
    rejected(()->policy.validateDelivery(actor(),tenant,second,valid),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    rejected(()->policy.validateDelivery(actor(),tenant,UUID.randomUUID(),valid),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
  }
  @Test void sharedStopUsesDriverAuthorityAndNeverPartialCustomerVisibility() throws Exception {
    var valid=signed();mapOrder(customerB);tx(()->policy.validateDelivery(actor(),tenant,stop,valid));
    assertProblem(http("customer","GET",base()+"/"+valid.bolDocumentId(),null,null),404,"RESOURCE_NOT_FOUND");
    assertProblem(http("customer","POST",base()+"/"+valid.bolDocumentId()+"/download-url","{}",null),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);rejected(()->policy.validateDelivery(actor(),tenant,stop,valid),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    assertProblem(http("driver","GET",base()+"/"+valid.bolDocumentId(),null,null),404,"RESOURCE_NOT_FOUND");
  }
  @ParameterizedTest @ValueSource(strings={"PICKUP","BASE_RETURN","INCOMPLETE","MEMBERSHIP"})
  void deliveryRequiresExecutionStateTypeAndFreshMembership(String variant) throws Exception {
    var valid=signed();
    if(variant.equals("MEMBERSHIP")) {
      db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
      assertThatThrownBy(()->tx(()->policy.validateDelivery(actor(),tenant,stop,valid))).hasMessage("Access to the requested Tenant is denied.");
    } else {
      if(variant.equals("INCOMPLETE")) db.execute("UPDATE trip_stops SET status='ARRIVED' WHERE id=?",stop);
      else db.execute("UPDATE trip_stops SET stop_type=? WHERE id=?",variant,stop);
      rejected(()->policy.validateDelivery(actor(),tenant,stop,valid),EvidenceValidationException.Code.INVALID_STATE_TRANSITION);
    }
  }
  @Test void fuelReceiptRequiresExactTypeOwnerAndContextAndCreatesNoFuelEvent() throws Exception {
    var receipt=document("FUEL_RECEIPT",DocumentContracts.Target.TRIP,trip);
    tx(()->policy.validateFuel(actor(),tenant,trip,tractor,receipt));
    rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,null),EvidenceValidationException.Code.EVIDENCE_REQUIRED);
    var wrong=document("DAMAGE_PHOTO",DocumentContracts.Target.TRIP,trip);
    rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,wrong),EvidenceValidationException.Code.VALIDATION_FAILED);
    rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,UUID.randomUUID()),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    var initialized=initialize(initBody("FUEL_RECEIPT"),"driver");var pending=UUID.fromString(JsonPath.read(initialized.body(),"$.documentId"));
    rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,pending),EvidenceValidationException.Code.UPLOAD_NOT_COMPLETE);
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,receipt),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);tx(()->policy.validateFuel(actor(),tenant,trip,tractor,receipt));
  }
  @Test void driverDamageUsesExactTripStopAndNeverConfersTrailerOosAuthority() throws Exception {
    var photo=document("DAMAGE_PHOTO",DocumentContracts.Target.TRIP_STOP,stop);
    tx(()->policy.validateDriverDamage(actor(),tenant,trip,stop,List.of(photo)));
    var wrong=signed();rejected(()->policy.validateDriverDamage(actor(),tenant,trip,stop,List.of(wrong.bolDocumentId())),EvidenceValidationException.Code.VALIDATION_FAILED);
    rejected(()->policy.validateDriverDamage(actor(),tenant,UUID.randomUUID(),stop,List.of(photo)),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    assertThatThrownBy(()->tx(()->policy.validateTrailerOos(actor(),tenant,UUID.randomUUID(),List.of(photo)))).hasMessage("The requested action is not permitted.");
    assertProblem(http("driver","POST",base()+"/"+photo+"/links","{\"entityType\":\"TRAILER\",\"entityId\":\""+UUID.randomUUID()+"\"}",null),400,"VALIDATION_FAILED");
  }
  @Test void oosPolicyIsReadyForExactTrailerContextButPublicWorkflowRemainsDeferred() throws Exception {
    var photo=document("DAMAGE_PHOTO",DocumentContracts.Target.TRIP,trip);
    new TrailerFixtures(flyway).role(tenant,membership,"TENANT_ADMIN",null);
    var trailer=new TrailerFixtures(flyway).trailer(tenant,"Evidence-"+UUID.randomUUID(),customerA);
    rejected(()->policy.validateTrailerOos(actor(),tenant,trailer,List.of(photo)),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    // Test-only fixture represents the future #49 approved attachment, not a production bypass.
    db.execute("INSERT INTO document_links VALUES(?,?,?,'TRAILER',?,now())",UUID.randomUUID(),tenant,photo,trailer);
    tx(()->policy.validateTrailerOos(actor(),tenant,trailer,List.of(photo)));
    rejected(()->policy.validateTrailerOos(actor(),tenant,UUID.randomUUID(),List.of(photo)),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    assertThat(db.value("SELECT serviceability_state FROM trailers WHERE id=?",trailer)).isEqualTo("IN_SERVICE");
  }

  @org.springframework.boot.test.context.TestComponent
  @RestController static class Harness {
    private final OperationalEvidencePolicy policy;private final TransactionTemplate tx;private final JdbcTemplate jdbc;
    private final MobileOperationLedger ledger;
    volatile Runnable beforeCompletion=()->{};
    Harness(OperationalEvidencePolicy p,PlatformTransactionManager m,JdbcTemplate j,MobileOperationLedger l) { policy=p;tx=new TransactionTemplate(m);jdbc=j;ledger=l; }
    // The application orchestration owns its HTTP boundary; reuse the existing safe error mapping.
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    org.springframework.http.ProblemDetail reused(IdempotencyKeyReusedException e) { return new DocumentExceptionHandler().reused(e); }
    @PostMapping("/api/v1/tenants/{tenant}/evidence-test/{stop}/validate")
    Map<String,Boolean> validate(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenant,@PathVariable UUID stop,@RequestBody Delivery delivery) {
      tx.executeWithoutResult(s->policy.validateDelivery(actor,tenant,stop,delivery));return Map.of("validated",true);
    }
    @PostMapping("/api/v1/tenants/{tenant}/evidence-test/{stop}/record")
    Receipt record(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenant,@PathVariable UUID stop,@RequestBody Delivery delivery,
        @RequestParam(defaultValue="false") boolean fail,@RequestParam(defaultValue="false") boolean noop,@RequestParam(defaultValue="false") boolean noEvent) {
      return tx.execute(s->policy.recordDelivery(actor,tenant,stop,delivery,()-> {
        complete(actor,tenant,stop,fail,noop,noEvent);
      }));
    }
    @PostMapping("/api/v1/tenants/{tenant}/evidence-test/{stop}/ledger")
    Receipt ledger(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenant,@PathVariable UUID stop,@RequestBody Delivery delivery,@RequestHeader("Idempotency-Key") UUID key) {
      return tx.execute(s->{
        policy.validateDeliveryForReplay(actor,tenant,stop,delivery);
        var canonical=stop+"|"+delivery.mode()+"|"+delivery.bolDocumentId()+"|"+delivery.deliveryPhotoDocumentId()+"|"+DatabaseOperationalEvidencePolicy.comment(delivery.comment());
        var hash=SignedS3TestServer.sha(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return ledger.execute(tenant,actor.userId(),key,"DELIVERY_EVIDENCE_TEST",hash,"TRIP_STOP",Receipt::stopId,Receipt.class,
            ()->policy.recordDelivery(actor,tenant,stop,delivery,()->complete(actor,tenant,stop,false,false,false)));
      });
    }
    private void complete(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,boolean fail,boolean noop,boolean noEvent) {
        beforeCompletion.run();if(noop) return;
        int updated=jdbc.update("UPDATE trip_stops SET status='COMPLETED',completed_at=now(),updated_at=now(),version=version+1 WHERE tenant_id=? AND id=? AND status='IN_SERVICE'",tenant,stop);
        assertThat(updated).isEqualTo(1);
        if(!noEvent) jdbc.update("""
            INSERT INTO trip_stop_events(id,tenant_id,trip_stop_id,trip_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at)
            SELECT ?,tenant_id,id,trip_id,'COMPLETED','HUMAN_USER','USER_ACTION',?,completed_at,now() FROM trip_stops WHERE tenant_id=? AND id=?
            """,UUID.randomUUID(),actor.userId(),tenant,stop);
        if(fail) throw new IllegalStateException("Test-only downstream failure");
    }
  }
  String deliveryJson(Delivery d) { return "{\"mode\":\""+d.mode()+"\",\"bolDocumentId\":\""+d.bolDocumentId()+"\",\"deliveryPhotoDocumentId\":"+(d.deliveryPhotoDocumentId()==null?"null":"\""+d.deliveryPhotoDocumentId()+"\"")+",\"comment\":"+(d.comment()==null?"null":"\""+d.comment()+"\"")+"}"; }
  String harness(String command) { return "/api/v1/tenants/"+tenant+"/evidence-test/"+stop+"/"+command; }
  @ParameterizedTest @EnumSource(Mode.class)
  void evidencePersistsOnlyWithValidCompletedStopAndServerActorInOneTransaction(Mode mode) throws Exception {
    var delivery=mode==Mode.SIGNED_BOL?signed():noReceiver();
    var response=http("driver","POST",harness("record"),deliveryJson(delivery),null);
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("COMPLETED");
    assertThat(db.value("SELECT completed_by_user_id FROM delivery_evidence WHERE trip_stop_id=?",stop)).isEqualTo(user);
    assertThat(db.value("SELECT evidence_mode FROM delivery_evidence WHERE trip_stop_id=?",stop)).isEqualTo(mode.name());
    assertThat(db.value("SELECT completed_at IS NOT NULL FROM delivery_evidence WHERE trip_stop_id=?",stop)).isEqualTo(true);
    assertThat(db.value("SELECT comment FROM delivery_evidence WHERE trip_stop_id=?",stop)).isEqualTo(mode==Mode.SIGNED_BOL?null:"No receiver available");
    assertProblem(http("driver","POST",harness("record"),deliveryJson(delivery),null),409,"INVALID_STATE_TRANSITION");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @ParameterizedTest @ValueSource(strings={"?noop=true","?noEvent=true","?fail=true"})
  void incompleteOrFailingOwnerTransitionCannotCommitEvidence(String query) throws Exception {
    var delivery=signed();var failed=http("driver","POST",harness("record")+query,deliveryJson(delivery),null);
    if(query.contains("fail")) assertThat(failed.statusCode()).isGreaterThanOrEqualTo(500);
    else assertProblem(failed,409,"INVALID_STATE_TRANSITION");
    assertThat(failed.body()).doesNotContain("Test-only",peer.secret,"tenant/");
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    assertThat(db.value("SELECT version FROM trip_stops WHERE id=?",stop)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=? AND event_type='COMPLETED'",tenant)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(http("driver","POST",harness("record"),deliveryJson(delivery),null).statusCode()).isEqualTo(200);
  }
  @Test void failedEvidenceInsertRollsBackOwnerTransitionAndEvent() throws Exception {
    var delivery=signed();db.execute("CREATE FUNCTION evidence_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only'; END $$");
    db.execute("CREATE TRIGGER evidence_fail BEFORE INSERT ON delivery_evidence FOR EACH ROW EXECUTE FUNCTION evidence_fail()");
    try {assertThat(http("driver","POST",harness("record"),deliveryJson(delivery),null).statusCode()).isGreaterThanOrEqualTo(500);}
    finally {db.execute("DROP TRIGGER evidence_fail ON delivery_evidence");db.execute("DROP FUNCTION evidence_fail()");}
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(http("driver","POST",harness("record"),deliveryJson(delivery),null).statusCode()).isEqualTo(200);
  }
  @Test void concurrencySerializesAtTripAndNeverCreatesDuplicateEvidence() throws Exception {
    var delivery=signed();var holding=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var owner=executor.submit(()->new TransactionTemplate(manager).execute(s->policy.recordDelivery(actor(),tenant,stop,delivery,()-> {
        holding.countDown();try {if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Latch timeout");} catch(InterruptedException e) {throw new AssertionError(e);}
        new JdbcTemplate(runtime).update("UPDATE trip_stops SET status='COMPLETED',completed_at=now(),version=version+1 WHERE tenant_id=? AND id=?",tenant,stop);
        new JdbcTemplate(runtime).update("INSERT INTO trip_stop_events(id,tenant_id,trip_stop_id,trip_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at) SELECT ?,tenant_id,id,trip_id,'COMPLETED','HUMAN_USER','USER_ACTION',?,completed_at,now() FROM trip_stops WHERE tenant_id=? AND id=?",UUID.randomUUID(),user,tenant,stop);
      })));
      assertThat(holding.await(10,TimeUnit.SECONDS)).isTrue();
      var contender=executor.submit(()->http("driver","POST",harness("record"),deliveryJson(delivery),null));
      long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
      while(System.nanoTime()<until) { if((Long)db.value("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%trips%' AND query ILIKE '%FOR UPDATE%'")>0) {waiting=true;break;}Thread.onSpinWait(); }
      assertThat(waiting).isTrue();release.countDown();assertThat(owner.get(15,TimeUnit.SECONDS)).isNotNull();
      assertProblem(contender.get(15,TimeUnit.SECONDS),409,"INVALID_STATE_TRANSITION");
    } finally {release.countDown();}
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void stableEvidenceProblemDetailsAndFreshHttpAuthorization() throws Exception {
    var valid=signed();assertProblem(http("driver","POST",harness("validate"),"{\"mode\":\"SIGNED_BOL\"}",null),409,"EVIDENCE_REQUIRED");
    assertThat(http(null,"POST",harness("validate"),deliveryJson(valid),null).statusCode()).isEqualTo(401);
    assertProblem(http("customer","POST",harness("validate"),deliveryJson(valid),null),403,"CAPABILITY_DENIED");
    db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
    assertProblem(http("driver","POST",harness("validate"),deliveryJson(valid),null),403,"TENANT_ACCESS_DENIED");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void databaseEvidenceCatalogFkUniquenessAndHistoryAreStrict() throws Exception {
    for(String mode:List.of("BAD","NO_RECEIVER_AVAILABLE"))
      assertThatThrownBy(()->db.execute("INSERT INTO delivery_evidence VALUES(?,?,?,?,NULL,?,now())",UUID.randomUUID(),tenant,stop,mode,user)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute("INSERT INTO delivery_evidence VALUES(?,?,?,'SIGNED_BOL',NULL,?,now())",UUID.randomUUID(),other,stop,user)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->db.execute("INSERT INTO delivery_evidence VALUES(?,?,?,'SIGNED_BOL',NULL,?,now())",UUID.randomUUID(),tenant,stop,UUID.randomUUID())).isInstanceOf(SQLException.class);
    var valid=signed();assertThat(http("driver","POST",harness("record"),deliveryJson(valid),null).statusCode()).isEqualTo(200);
    assertThatThrownBy(()->db.execute("INSERT INTO delivery_evidence VALUES(?,?,?,'SIGNED_BOL',NULL,?,now())",UUID.randomUUID(),tenant,stop,user)).isInstanceOf(SQLException.class);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("25");flyway.validate();
    try(var c=runtime.getConnection();var q=c.createStatement()) {
      c.setAutoCommit(false);assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
      try(var r=q.executeQuery("SELECT has_table_privilege(current_user,'delivery_evidence','SELECT'),has_table_privilege(current_user,'delivery_evidence','INSERT'),has_table_privilege(current_user,'delivery_evidence','UPDATE'),has_table_privilege(current_user,'delivery_evidence','DELETE')")) {r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isTrue();assertThat(r.getBoolean(3)).isFalse();assertThat(r.getBoolean(4)).isFalse();}
      try(var r=q.executeQuery("SELECT count(*) FROM delivery_evidence")) {r.next();assertThat(r.getLong(1)).isZero();}
      q.execute("SELECT set_config('app.current_tenant_id','"+tenant+"',true)");
      try(var r=q.executeQuery("SELECT count(*) FROM delivery_evidence")) {r.next();assertThat(r.getLong(1)).isEqualTo(1L);}
      q.execute("SELECT set_config('app.current_user_id','"+user+"',true)");
      try(var r=q.executeQuery("SELECT count(*) FROM delivery_evidence")) {r.next();assertThat(r.getLong(1)).isZero();}
      c.rollback();q.execute("SELECT set_config('app.current_tenant_id','"+other+"',true)");
      try(var r=q.executeQuery("SELECT count(*) FROM delivery_evidence")) {r.next();assertThat(r.getLong(1)).isZero();}
      assertThatThrownBy(()->q.executeUpdate("INSERT INTO delivery_evidence VALUES('"+UUID.randomUUID()+"','"+tenant+"','"+stop+"','SIGNED_BOL',NULL,'"+user+"',now())")).isInstanceOf(SQLException.class);
      c.rollback();
      assertThatThrownBy(()->q.executeUpdate("UPDATE delivery_evidence SET comment='changed'")).isInstanceOf(SQLException.class);c.rollback();
      assertThatThrownBy(()->q.executeUpdate("DELETE FROM delivery_evidence")).isInstanceOf(SQLException.class);c.rollback();
    }
  }
  @Test void noReceiverRequiresFinalizedCorrectPhotoAndNoPartialRecord() throws Exception {
    var valid=noReceiver();var response=initialize(initBody("DELIVERY_PHOTO"),"driver");var pending=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    rejected(()->policy.validateDelivery(actor(),tenant,stop,new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),pending,"comment")),EvidenceValidationException.Code.UPLOAD_NOT_COMPLETE);
    var wrong=document("BOL",DocumentContracts.Target.TRIP_STOP,stop);
    rejected(()->policy.validateDelivery(actor(),tenant,stop,new Delivery(Mode.NO_RECEIVER_AVAILABLE,valid.bolDocumentId(),wrong,"comment")),EvidenceValidationException.Code.VALIDATION_FAILED);
    assertProblem(http("driver","POST",harness("record"),"{\"mode\":\"SIGNED_BOL\"}",null),409,"EVIDENCE_REQUIRED");
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void actualForeignDeliveryStopIsInvisibleDespiteValidOwnEvidence() throws Exception {
    var valid=signed();var foreignTrip=UUID.randomUUID();var foreignStop=UUID.randomUUID();
    db.execute("INSERT INTO trips(id,tenant_id,status,created_at,updated_at) VALUES(?,?,'AT_STOP',now(),now())",foreignTrip,other);
    db.execute("INSERT INTO trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) VALUES(?,?,?,1,'DELIVERY','IN_SERVICE','{\"name\":\"Foreign\"}',now(),now())",foreignStop,other,foreignTrip);
    var hidden=http("driver","POST","/api/v1/tenants/"+tenant+"/evidence-test/"+foreignStop+"/validate",deliveryJson(valid),null);
    assertProblem(hidden,404,"RESOURCE_NOT_FOUND");assertThat(hidden.body()).doesNotContain(foreignTrip.toString(),other.toString(),"tenant/");
  }
  @Test void damageMissingPendingAndDuplicatesCannotApproveContext() throws Exception {
    rejected(()->policy.validateDriverDamage(actor(),tenant,trip,stop,List.of()),EvidenceValidationException.Code.EVIDENCE_REQUIRED);
    var response=initialize(initBody("DAMAGE_PHOTO"),"driver");var pending=UUID.fromString(JsonPath.read(response.body(),"$.documentId"));
    rejected(()->policy.validateDriverDamage(actor(),tenant,trip,stop,List.of(pending)),EvidenceValidationException.Code.UPLOAD_NOT_COMPLETE);
    var photo=document("DAMAGE_PHOTO",DocumentContracts.Target.TRIP_STOP,stop);
    rejected(()->policy.validateDriverDamage(actor(),tenant,trip,stop,List.of(photo,photo)),EvidenceValidationException.Code.VALIDATION_FAILED);
  }
  @Test void uploadedReceiptOfAnotherDriverIsNotReusable() throws Exception {
    var stranger=db.user();db.membership(tenant,stranger,"ACTIVE","DRIVER");db.driver(tenant,stranger);
    org.mockito.Mockito.when(verifier.verify("receipt-stranger")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+stranger,null,null));
    var init=initialize(initBody("FUEL_RECEIPT"),"receipt-stranger");assertThat(init.statusCode()).isEqualTo(201);
    var receipt=UUID.fromString(JsonPath.read(init.body(),"$.documentId"));peer.objects.put(DocumentService.temporary(tenant,receipt),new SignedS3TestServer.Blob(bytes,"image/jpeg"));
    assertThat(http("receipt-stranger","POST",base()+"/"+receipt+"/upload-completion","{}",UUID.randomUUID()).statusCode()).isEqualTo(200);
    rejected(()->policy.validateFuel(actor(),tenant,trip,tractor,receipt),EvidenceValidationException.Code.RESOURCE_NOT_FOUND);
    assertProblem(http("driver","GET",base()+"/"+receipt,null,null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void validationRequiresExistingTransactionAndRejectsPriorCompletionProof() throws Exception {
    var valid=signed();
    assertThatThrownBy(()->policy.validateDelivery(actor(),tenant,stop,valid)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    Instant old=Instant.now().minusSeconds(30);
    db.execute("INSERT INTO trip_stop_events(id,tenant_id,trip_stop_id,trip_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at) VALUES(?,?,?,?,'COMPLETED','HUMAN_USER','USER_ACTION',?,?,now())",UUID.randomUUID(),tenant,stop,trip,user,Timestamp.from(old));
    rejected(()->policy.recordDelivery(actor(),tenant,stop,valid,()->new JdbcTemplate(runtime).update("UPDATE trip_stops SET status='COMPLETED',version=version+1,completed_at=? WHERE tenant_id=? AND id=?",Timestamp.from(old),tenant,stop)),EvidenceValidationException.Code.INVALID_STATE_TRANSITION);
    assertThat(db.value("SELECT status FROM trip_stops WHERE id=?",stop)).isEqualTo("IN_SERVICE");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void capabilityRevokedDuringDocumentLockWaitIsRevalidated() throws Exception {
    var valid=signed();
    try(var connection=flyway.getConfiguration().getDataSource().getConnection();var q=connection.createStatement();var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      connection.setAutoCommit(false);q.executeQuery("SELECT id FROM documents WHERE id='"+valid.bolDocumentId()+"' FOR UPDATE").close();
      var validation=executor.submit(()->http("driver","POST",harness("validate"),deliveryJson(valid),null));
      long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
      while(System.nanoTime()<until) { if((Long)db.value("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%documents%' AND query ILIKE '%FOR SHARE%'")>0) {waiting=true;break;}Thread.onSpinWait(); }
      assertThat(waiting).isTrue();db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);connection.commit();
      assertProblem(validation.get(15,TimeUnit.SECONDS),403,"CAPABILITY_DENIED");
    }
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void newEvidenceTableHasNoPublicOwnerBypassOrCascadePrivileges() throws Exception {
    try(var c=flyway.getConfiguration().getDataSource().getConnection();var q=c.createStatement()) {
      try(var r=q.executeQuery("SELECT relrowsecurity,relowner=(SELECT oid FROM pg_roles WHERE rolname='"+RUNTIME_USERNAME+"') FROM pg_class WHERE relname='delivery_evidence'")) {r.next();assertThat(r.getBoolean(1)).isTrue();assertThat(r.getBoolean(2)).isFalse();}
      try(var r=q.executeQuery("SELECT count(*) FROM information_schema.role_table_grants WHERE table_name='delivery_evidence' AND grantee='PUBLIC'")) {r.next();assertThat(r.getLong(1)).isZero();}
      try(var r=q.executeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid='delivery_evidence'::regclass AND contype='f' AND confdeltype='c'")) {r.next();assertThat(r.getLong(1)).isZero();}
    }
    assertThatThrownBy(()->db.execute("INSERT INTO delivery_evidence VALUES(?,?,?,'NO_RECEIVER_AVAILABLE',?, ?,now())",UUID.randomUUID(),tenant,stop," \t\n ",user)).isInstanceOf(SQLException.class);
  }
  @Test void futureWorkflowReusesExistingLedgerAfterLostAckAndStillRevalidatesAccess() throws Exception {
    var delivery=signed();var key=UUID.randomUUID();var first=http("driver","POST",harness("ledger"),deliveryJson(delivery),key);
    assertThat(first.statusCode()).withFailMessage(first.body()).isEqualTo(200);
    var replay=http("driver","POST",harness("ledger"),deliveryJson(delivery),key);
    assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.body()).isEqualTo(first.body());
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
    var alternative=signed();assertProblem(http("driver","POST",harness("ledger"),deliveryJson(alternative),key),409,"IDEMPOTENCY_KEY_REUSED");
    db.execute("UPDATE trips SET current_driver_id=NULL WHERE id=?",trip);
    assertProblem(http("driver","POST",harness("ledger"),deliveryJson(delivery),key),404,"RESOURCE_NOT_FOUND");
    db.execute("UPDATE trips SET current_driver_id=? WHERE id=?",driver,trip);
    db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
    assertProblem(http("driver","POST",harness("ledger"),deliveryJson(delivery),key),403,"CAPABILITY_DENIED");
  }
  @Test void concurrentSameKeyWorkflowClaimsAfterExclusiveTripLockWithoutUpgradeDeadlock() throws Exception {
    var delivery=signed();var key=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    workflow.beforeCompletion=()->{entered.countDown();try {if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Completion wait timeout");} catch(InterruptedException e) {throw new AssertionError(e);} };
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var first=executor.submit(()->http("driver","POST",harness("ledger"),deliveryJson(delivery),key));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
      var second=executor.submit(()->http("driver","POST",harness("ledger"),deliveryJson(delivery),key));
      long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
      while(System.nanoTime()<until) {if((Long)db.value("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%trips%' AND query ILIKE '%FOR UPDATE%'")>0) {waiting=true;break;}Thread.onSpinWait();}
      assertThat(waiting).isTrue();release.countDown();var one=first.get(15,TimeUnit.SECONDS);var two=second.get(15,TimeUnit.SECONDS);
      assertThat(one.statusCode()).withFailMessage(one.body()).isEqualTo(200);assertThat(two.statusCode()).withFailMessage(two.body()).isEqualTo(200);assertThat(two.body()).isEqualTo(one.body());
    } finally {release.countDown();workflow.beforeCompletion=()->{};}
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(1L);
    assertThat(db.value("SELECT count(*) FROM trip_stop_events WHERE tenant_id=?",tenant)).isEqualTo(1L);
  }
  @Test void publicWorkflowMapsInactiveDriverToCapabilityDenialNotInfrastructureFailure() throws Exception {
    var delivery=signed();db.execute("UPDATE drivers SET active=false WHERE id=?",driver);
    assertProblem(http("driver","POST",harness("validate"),deliveryJson(delivery),null),403,"CAPABILITY_DENIED");
    rejected(()->policy.validateFuelUpload(actor(),tenant,delivery.bolDocumentId()),EvidenceValidationException.Code.CAPABILITY_DENIED);
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void oversizedCorruptAttachmentSetFailsClosedAtPublicWorkflowBoundary() throws Exception {
    var delivery=signed();
    for(int i=0;i<50;i++) db.execute("INSERT INTO document_links VALUES(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,delivery.bolDocumentId(),UUID.randomUUID());
    var denied=http("driver","POST",harness("validate"),deliveryJson(delivery),null);
    assertProblem(denied,404,"RESOURCE_NOT_FOUND");
    assertThat(JsonPath.<Map<String,Object>>read(denied.body(),"$")).containsOnlyKeys("type","title","status","detail","instance","code","traceId");
    assertThat(denied.body()).doesNotContain("\"links\"","\"count\"","tenant/");
    assertThat(db.value("SELECT count(*) FROM delivery_evidence WHERE tenant_id=?",tenant)).isEqualTo(0L);
  }
}
