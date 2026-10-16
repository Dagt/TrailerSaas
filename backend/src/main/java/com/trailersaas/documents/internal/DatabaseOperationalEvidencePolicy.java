package com.trailersaas.documents.internal;

import com.trailersaas.documents.*;
import com.trailersaas.identity.*;
import com.trailersaas.trips.EvidenceWorkDirectory;
import com.trailersaas.trailers.TrailerEvidenceDirectory;
import com.trailersaas.tractors.TractorPlanningPolicy;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.documents.internal.DocumentContracts.*;
import static com.trailersaas.documents.EvidenceValidationException.Code.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
class DatabaseOperationalEvidencePolicy implements OperationalEvidencePolicy {
  private final TenantAuthorization auth;
  private final DocumentAccessPolicy access;
  private final DocumentUploadAuthority uploads;
  private final DocumentRepository documents;
  private final DocumentResourceAccess resources;
  private final EvidenceWorkDirectory work;
  private final TrailerEvidenceDirectory trailers;
  private final TractorPlanningPolicy tractors;
  private final JdbcTemplate jdbc;
  private final org.springframework.beans.factory.ObjectProvider<com.trailersaas.documents.OperationalEvidenceReplayDirectory> replayFacts;
  DatabaseOperationalEvidencePolicy(TenantAuthorization a,DocumentAccessPolicy s,DocumentUploadAuthority u,
      DocumentRepository d,DocumentResourceAccess r,EvidenceWorkDirectory w,TrailerEvidenceDirectory t,
      TractorPlanningPolicy p,JdbcTemplate j,org.springframework.beans.factory.ObjectProvider<com.trailersaas.documents.OperationalEvidenceReplayDirectory> replayFacts) { auth=a;access=s;uploads=u;documents=d;resources=r;work=w;trailers=t;tractors=p;jdbc=j;this.replayFacts=replayFacts; }

  public void validatePreTrip(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,PreTripPhotos photos) {
    var expected=preTripSet(photos);
    assignedTrip(actor,tenant,trip,tractor,Type.PRETRIP_FRONT);
    validateDocuments(actor,tenant,expected,Target.TRIP,trip,Capability.PRETRIP_CREATE);
  }
  public void validatePreTripUploads(AuthenticatedUserPrincipal actor,UUID tenant,PreTripPhotos photos) {
    var expected=preTripSet(photos);uploads.require(actor,tenant,Type.PRETRIP_FRONT,true);
    validateDocuments(actor,tenant,expected,null,null,Capability.PRETRIP_CREATE);
  }
  public void attachPreTripInspection(AuthenticatedUserPrincipal actor,UUID tenant,UUID inspection,UUID trip,UUID tractor,PreTripPhotos photos) {
    var expected=preTripSet(photos);
    var owner=jdbc.query("""
      SELECT i.driver_id FROM inspections i JOIN shifts s ON s.tenant_id=i.tenant_id AND s.id=i.shift_id AND s.driver_id=i.driver_id
      JOIN drivers d ON d.tenant_id=i.tenant_id AND d.id=i.driver_id AND d.active
      WHERE i.tenant_id=? AND i.id=? AND i.inspection_type='PRE_TRIP' AND i.tractor_id=? AND i.trip_id IS NOT DISTINCT FROM ?
        AND d.user_id=? AND s.off_duty_at IS NULL AND i.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)
      """,(r,n)->r.getObject(1,UUID.class),tenant,inspection,tractor,trip,actor.userId());
    if(owner.size()!=1)throw failure(RESOURCE_NOT_FOUND);
    uploads.require(actor,tenant,Type.PRETRIP_FRONT,true);
    for(UUID id:expected.keySet().stream().sorted().toList()) {
      DocumentRepository.Row row;
      try {row=documents.find(tenant,id,true);}catch(DocumentFailure e){if(e.status==404)throw failure(RESOURCE_NOT_FOUND);throw e;}
      auth.requireTenantCapability(actor,tenant,Capability.PRETRIP_CREATE);
      if(!actor.userId().equals(row.uploader()))throw failure(RESOURCE_NOT_FOUND);
      if(row.type()!=expected.get(id))throw failure(VALIDATION_FAILED);
      if(row.status()!=Status.UPLOADED)throw failure(UPLOAD_NOT_COMPLETE);
      var links=documents.links(tenant,id);
      if(links.stream().anyMatch(l->l.entityType()==Target.INSPECTION))throw failure(RESOURCE_CONFLICT);
      if(links.stream().anyMatch(l->l.entityType()!=Target.TRIP || !Objects.equals(l.entityId(),trip)))throw failure(RESOURCE_NOT_FOUND);
      if(trip!=null)documents.link(tenant,id,new LinkRequest(Target.TRIP,trip));
    }
    if(trip==null)validatePreTripUploads(actor,tenant,photos);else validatePreTrip(actor,tenant,trip,tractor,photos);
    for(UUID id:expected.keySet().stream().sorted().toList())documents.link(tenant,id,new LinkRequest(Target.INSPECTION,inspection));
  }
  private static Map<UUID,Type> preTripSet(PreTripPhotos photos) {
    if(photos==null) throw failure(EVIDENCE_REQUIRED);
    var expected=new LinkedHashMap<UUID,Type>();
    var ids=Arrays.asList(photos.front(),photos.rear(),photos.driverSide(),photos.passengerSide());
    required(ids);expected.put(photos.front(),Type.PRETRIP_FRONT);expected.put(photos.rear(),Type.PRETRIP_REAR);
    expected.put(photos.driverSide(),Type.PRETRIP_DRIVER_SIDE);expected.put(photos.passengerSide(),Type.PRETRIP_PASSENGER_SIDE);
    return expected;
  }
  public void validateFuel(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,UUID receipt) {
    if(receipt==null) throw failure(EVIDENCE_REQUIRED);
    assignedTrip(actor,tenant,trip,tractor,Type.FUEL_RECEIPT);
    validateDocuments(actor,tenant,Map.of(receipt,Type.FUEL_RECEIPT),Target.TRIP,trip,Capability.FUEL_EVENT_CREATE);
  }
  public void validateFuelUpload(AuthenticatedUserPrincipal actor,UUID tenant,UUID receipt) {
    if(receipt==null) throw failure(EVIDENCE_REQUIRED);
    uploads.require(actor,tenant,Type.FUEL_RECEIPT,true);
    validateDocuments(actor,tenant,Map.of(receipt,Type.FUEL_RECEIPT),null,null,Capability.FUEL_EVENT_CREATE);
  }
  public void validateFuelForReplay(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,UUID receipt,UUID operation) {
    if(receipt==null)throw failure(EVIDENCE_REQUIRED);
    var facts=replayFacts.getIfAvailable();boolean committed=facts!=null&&facts.fuelCommitted(tenant,actor.userId(),operation,trip);
    assignedTrip(actor,tenant,trip,tractor,Type.FUEL_RECEIPT,committed);
    validateDocuments(actor,tenant,Map.of(receipt,Type.FUEL_RECEIPT),Target.TRIP,trip,Capability.FUEL_EVENT_CREATE);
  }
  public void validateDriverDamageForReplay(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID stop,List<UUID> photos,UUID operation) {
    var facts=replayFacts.getIfAvailable();boolean committed=facts!=null&&facts.damageCommitted(tenant,actor.userId(),operation,trip);
    if(!committed){validateDriverDamage(actor,tenant,trip,stop,photos);return;}
    required(photos);assignedTrip(actor,tenant,trip,null,Type.DAMAGE_PHOTO,true);
    if(stop!=null&&!work.belongs(tenant,stop,trip))throw failure(RESOURCE_NOT_FOUND);
    validateDocuments(actor,tenant,expected(photos,Type.DAMAGE_PHOTO),stop==null?Target.TRIP:Target.TRIP_STOP,stop==null?trip:stop,Capability.EXCEPTION_REPORT);
  }
  public void validateDriverDamage(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID stop,List<UUID> photos) {
    required(photos);auth.requireTenantCapability(actor,tenant,Capability.EXCEPTION_REPORT);
    if(stop==null) assignedTrip(actor,tenant,trip,null,Type.DAMAGE_PHOTO);
    else {
      if(!work.belongs(tenant,stop,trip))throw failure(RESOURCE_NOT_FOUND);
      var parent=work.stop(tenant,stop,false).orElseThrow(()->failure(RESOURCE_NOT_FOUND));
      if(!parent.trip().id().equals(trip)) throw failure(RESOURCE_NOT_FOUND);
      assigned(actor,tenant,parent.trip(),Type.DAMAGE_PHOTO);
    }
    validateDocuments(actor,tenant,expected(photos,Type.DAMAGE_PHOTO),stop==null?Target.TRIP:Target.TRIP_STOP,stop==null?trip:stop,Capability.EXCEPTION_REPORT);
  }
  public void validateTrailerOos(AuthenticatedUserPrincipal actor,UUID tenant,UUID trailer,List<UUID> photos) {
    required(photos);auth.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
    if(trailer==null || !trailers.lockExists(tenant,trailer)) throw failure(RESOURCE_NOT_FOUND);
    auth.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
    validateDocuments(actor,tenant,expected(photos,Type.DAMAGE_PHOTO),Target.TRAILER,trailer,Capability.TRAILER_OOS_MARK);
  }
  public void recordTrailerOosEvidence(AuthenticatedUserPrincipal actor,UUID tenant,UUID trailer,UUID event,UUID operation,List<UUID> photos) {
    validateTrailerOos(actor,tenant,trailer,photos);
    for(var id:photos.stream().sorted().toList()) {
      var links=documents.links(tenant,id);
      if(links.size()!=1 || links.getFirst().entityType()!=Target.TRAILER || !links.getFirst().entityId().equals(trailer)) throw failure(RESOURCE_NOT_FOUND);
      if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from trailer_oos_evidence where tenant_id=? and document_id=?)",Boolean.class,tenant,id))) throw failure(RESOURCE_CONFLICT);
      jdbc.update("insert into trailer_oos_evidence(tenant_id,trailer_id,event_id,document_id,actor_user_id,client_operation_id) values(?,?,?,?,?,?)",tenant,trailer,event,id,actor.userId(),operation);
    }
  }
  public void validateDelivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request) {
    delivery(actor,tenant,stop,request,false,false);
  }
  public void validateDeliveryForReplay(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request) {
    // The command must acquire the exclusive parent BEFORE claiming the ledger, avoiding SHARE upgrade cycles.
    delivery(actor,tenant,stop,request,true,true);
  }
  public Receipt recordDelivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request,Runnable completion) {
    if(completion==null) throw failure(VALIDATION_FAILED);
    var before=delivery(actor,tenant,stop,request,true,false);
    if(!jdbc.queryForList("SELECT id FROM delivery_evidence WHERE tenant_id=? AND trip_stop_id=?",UUID.class,tenant,stop).isEmpty())
      throw failure(RESOURCE_CONFLICT);
    completion.run();
    var after=work.stop(tenant,stop,true).orElseThrow(()->failure(RESOURCE_NOT_FOUND));
    if(!"COMPLETED".equals(after.status()) || after.version()!=before.version()+1 || after.completedAt()==null
        || !after.completionInCurrentTransaction()
        || !"DELIVERY".equals(after.type()) || !after.trip().id().equals(before.trip().id())
        || !actor.userId().equals(after.completionActorId()) || !Objects.equals(after.trip().driverId(),before.trip().driverId()))
      throw failure(INVALID_STATE_TRANSITION);
    var id=UUID.randomUUID();var time=Instant.now();var comment=comment(request.comment());
    if(jdbc.update("""
        INSERT INTO delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,comment,completed_by_user_id,completed_at,bol_document_id,delivery_photo_document_id)
        VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(tenant_id,trip_stop_id) DO NOTHING
        """,id,tenant,stop,request.mode().name(),comment,actor.userId(),java.sql.Timestamp.from(time),request.bolDocumentId(),request.deliveryPhotoDocumentId())!=1)
      throw failure(RESOURCE_CONFLICT);
    return new Receipt(id,stop,request.mode(),comment,time);
  }

  private EvidenceWorkDirectory.Stop delivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request,boolean exclusive,boolean replay) {
    if(request==null || request.mode()==null) throw failure(VALIDATION_FAILED);
    if(request.bolDocumentId()==null || (request.mode()==Mode.NO_RECEIVER_AVAILABLE && (request.deliveryPhotoDocumentId()==null || comment(request.comment())==null)))
      throw failure(EVIDENCE_REQUIRED);
    if(request.mode()==Mode.SIGNED_BOL && request.deliveryPhotoDocumentId()!=null) throw failure(VALIDATION_FAILED);
    var ids=request.mode()==Mode.SIGNED_BOL?List.of(request.bolDocumentId()):List.of(request.bolDocumentId(),request.deliveryPhotoDocumentId());
    required(ids);auth.requireTenantCapability(actor,tenant,Capability.POD_UPLOAD);
    if(stop==null) throw failure(VALIDATION_FAILED);
    var parent=work.stop(tenant,stop,exclusive).orElseThrow(()->failure(RESOURCE_NOT_FOUND));
    UUID driver=uploads.require(actor,tenant,Type.BOL,true);
    if(!driver.equals(parent.trip().driverId())) throw failure(RESOURCE_NOT_FOUND);
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,true);
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_EXECUTE_ASSIGNED,true);
    boolean completedReplay=replay && "COMPLETED".equals(parent.status())
        && !jdbc.queryForList("SELECT id FROM delivery_evidence WHERE tenant_id=? AND trip_stop_id=?",UUID.class,tenant,stop).isEmpty();
    if(!"DELIVERY".equals(parent.type()) || (!completedReplay && (!"IN_SERVICE".equals(parent.status()) || !"AT_STOP".equals(parent.trip().status()))))
      throw failure(INVALID_STATE_TRANSITION);
    var expected=new HashMap<UUID,Type>();expected.put(request.bolDocumentId(),Type.BOL);
    if(request.deliveryPhotoDocumentId()!=null) expected.put(request.deliveryPhotoDocumentId(),Type.DELIVERY_PHOTO);
    validateDocuments(actor,tenant,expected,Target.TRIP_STOP,stop,Capability.POD_UPLOAD);return parent;
  }
  private void assignedTrip(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,Type type) {
    assignedTrip(actor,tenant,trip,tractor,type,false);
  }
  private void assignedTrip(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,Type type,boolean committedReplay) {
    auth.requireTenantCapability(actor,tenant,DocumentUploadAuthority.capability(type));
    if(trip==null) throw failure(VALIDATION_FAILED);
    var parent=work.trip(tenant,trip,false).orElseThrow(()->failure(RESOURCE_NOT_FOUND));assigned(actor,tenant,parent,type,committedReplay);
    if(type!=Type.DAMAGE_PHOTO) {
      if(tractor==null || !tractor.equals(parent.tractorId())) throw failure(RESOURCE_NOT_FOUND);
      var eligibility=tractors.check(tenant,tractor);
      if(eligibility==TractorPlanningPolicy.Eligibility.NOT_FOUND) throw failure(RESOURCE_NOT_FOUND);
      if(eligibility!=TractorPlanningPolicy.Eligibility.ELIGIBLE) throw failure(RESOURCE_CONFLICT);
    }
  }
  private void assigned(AuthenticatedUserPrincipal actor,UUID tenant,EvidenceWorkDirectory.Trip parent,Type type) {
    assigned(actor,tenant,parent,type,false);
  }
  private void assigned(AuthenticatedUserPrincipal actor,UUID tenant,EvidenceWorkDirectory.Trip parent,Type type,boolean committedReplay) {
    UUID driver=uploads.require(actor,tenant,type,true);
    if(!driver.equals(parent.driverId())) throw failure(RESOURCE_NOT_FOUND);
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,true);
    if(!committedReplay && Set.of("COMPLETED","CANCELLED").contains(parent.status())) throw failure(INVALID_STATE_TRANSITION);
  }
  private void validateDocuments(AuthenticatedUserPrincipal actor,UUID tenant,Map<UUID,Type> expected,Target target,UUID resource,Capability capability) {
    for(UUID id:expected.keySet().stream().sorted().toList()) {
      DocumentRepository.Row document;
      try { document=documents.shared(tenant,id); }
      catch(DocumentFailure e) { if(e.status==404) throw failure(RESOURCE_NOT_FOUND);throw e; }
      // Parent/Document waits must not preserve a capability or Customer scope revoked while waiting.
      auth.requireTenantCapability(actor,tenant,capability);
      var scope=access.readScope(actor,tenant);
      if(!actor.userId().equals(document.uploader())) throw failure(RESOURCE_NOT_FOUND);
      if(document.type()!=expected.get(id)) throw failure(VALIDATION_FAILED);
      if(document.status()!=Status.UPLOADED) throw failure(UPLOAD_NOT_COMPLETE);
      List<Link> links;
      try { links=documents.links(tenant,id); }
      catch(DocumentFailure e) { if(e.status==404) throw failure(RESOURCE_NOT_FOUND);throw e; }
      if(target==null) {
        if(!links.isEmpty()) throw failure(RESOURCE_NOT_FOUND);
        continue;
      }
      if(links.stream().noneMatch(l->l.entityType()==target && l.entityId().equals(resource))
          || resources.visible(actor,tenant,links,scope).size()!=links.size()) throw failure(RESOURCE_NOT_FOUND);
    }
  }
  static void required(List<UUID> ids) {
    if(ids==null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) throw failure(EVIDENCE_REQUIRED);
    if(ids.size()>50 || new HashSet<>(ids).size()!=ids.size()) throw failure(VALIDATION_FAILED);
  }
  static String comment(String value) {
    if(value==null) return null;
    int start=0,end=value.length();
    while(start<end && space(value.codePointAt(start))) start+=Character.charCount(value.codePointAt(start));
    while(start<end && space(value.codePointBefore(end))) end-=Character.charCount(value.codePointBefore(end));
    return start==end?null:value.substring(start,end);
  }
  private static boolean space(int cp) { return cp==0x85 || Character.isWhitespace(cp) || Character.isSpaceChar(cp); }
  private static Map<UUID,Type> expected(List<UUID> ids,Type type) { var result=new HashMap<UUID,Type>();ids.forEach(id->result.put(id,type));return result; }
  private static EvidenceValidationException failure(EvidenceValidationException.Code code) { return new EvidenceValidationException(code); }
}
