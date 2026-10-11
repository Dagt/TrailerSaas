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
  DatabaseOperationalEvidencePolicy(TenantAuthorization a,DocumentAccessPolicy s,DocumentUploadAuthority u,
      DocumentRepository d,DocumentResourceAccess r,EvidenceWorkDirectory w,TrailerEvidenceDirectory t,
      TractorPlanningPolicy p,JdbcTemplate j) { auth=a;access=s;uploads=u;documents=d;resources=r;work=w;trailers=t;tractors=p;jdbc=j; }

  public void validatePreTrip(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,PreTripPhotos photos) {
    if(photos==null) throw failure(EVIDENCE_REQUIRED);
    var expected=new LinkedHashMap<UUID,Type>();
    var ids=Arrays.asList(photos.front(),photos.rear(),photos.driverSide(),photos.passengerSide());
    required(ids);expected.put(photos.front(),Type.PRETRIP_FRONT);expected.put(photos.rear(),Type.PRETRIP_REAR);
    expected.put(photos.driverSide(),Type.PRETRIP_DRIVER_SIDE);expected.put(photos.passengerSide(),Type.PRETRIP_PASSENGER_SIDE);
    assignedTrip(actor,tenant,trip,tractor,Type.PRETRIP_FRONT);
    validateDocuments(actor,tenant,expected,Target.TRIP,trip);
  }
  public void validateFuel(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,UUID receipt) {
    if(receipt==null) throw failure(EVIDENCE_REQUIRED);
    assignedTrip(actor,tenant,trip,tractor,Type.FUEL_RECEIPT);
    validateDocuments(actor,tenant,Map.of(receipt,Type.FUEL_RECEIPT),Target.TRIP,trip);
  }
  public void validateDriverDamage(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID stop,List<UUID> photos) {
    required(photos);auth.requireTenantCapability(actor,tenant,Capability.EXCEPTION_REPORT);
    if(stop==null) assignedTrip(actor,tenant,trip,null,Type.DAMAGE_PHOTO);
    else {
      var parent=work.stop(tenant,stop,false).orElseThrow(()->failure(RESOURCE_NOT_FOUND));
      if(!parent.trip().id().equals(trip)) throw failure(RESOURCE_NOT_FOUND);
      assigned(actor,tenant,parent.trip(),Type.DAMAGE_PHOTO);
    }
    validateDocuments(actor,tenant,expected(photos,Type.DAMAGE_PHOTO),stop==null?Target.TRIP:Target.TRIP_STOP,stop==null?trip:stop);
  }
  public void validateTrailerOos(AuthenticatedUserPrincipal actor,UUID tenant,UUID trailer,List<UUID> photos) {
    required(photos);auth.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
    if(trailer==null || !trailers.lockExists(tenant,trailer)) throw failure(RESOURCE_NOT_FOUND);
    auth.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
    validateDocuments(actor,tenant,expected(photos,Type.DAMAGE_PHOTO),Target.TRAILER,trailer);
  }
  public void validateDelivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request) {
    delivery(actor,tenant,stop,request,false);
  }
  public Receipt recordDelivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request,Runnable completion) {
    if(completion==null) throw failure(VALIDATION_FAILED);
    var before=delivery(actor,tenant,stop,request,true);
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
        INSERT INTO delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,comment,completed_by_user_id,completed_at)
        VALUES(?,?,?,?,?,?,?) ON CONFLICT(tenant_id,trip_stop_id) DO NOTHING
        """,id,tenant,stop,request.mode().name(),comment,actor.userId(),java.sql.Timestamp.from(time))!=1)
      throw failure(RESOURCE_CONFLICT);
    return new Receipt(id,stop,request.mode(),comment,time);
  }

  private EvidenceWorkDirectory.Stop delivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,Delivery request,boolean exclusive) {
    if(request==null || request.mode()==null) throw failure(VALIDATION_FAILED);
    if(request.bolDocumentId()==null || (request.mode()==Mode.NO_RECEIVER_AVAILABLE && (request.deliveryPhotoDocumentId()==null || comment(request.comment())==null)))
      throw failure(EVIDENCE_REQUIRED);
    if(request.mode()==Mode.SIGNED_BOL && request.deliveryPhotoDocumentId()!=null) throw failure(VALIDATION_FAILED);
    var ids=request.mode()==Mode.SIGNED_BOL?List.of(request.bolDocumentId()):List.of(request.bolDocumentId(),request.deliveryPhotoDocumentId());
    required(ids);auth.requireTenantCapability(actor,tenant,Capability.POD_UPLOAD);
    if(stop==null) throw failure(VALIDATION_FAILED);
    var parent=work.stop(tenant,stop,exclusive).orElseThrow(()->failure(RESOURCE_NOT_FOUND));
    assigned(actor,tenant,parent.trip(),Type.BOL);
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_EXECUTE_ASSIGNED,true);
    if(!"DELIVERY".equals(parent.type()) || !"IN_SERVICE".equals(parent.status()) || !"AT_STOP".equals(parent.trip().status()))
      throw failure(INVALID_STATE_TRANSITION);
    var expected=new HashMap<UUID,Type>();expected.put(request.bolDocumentId(),Type.BOL);
    if(request.deliveryPhotoDocumentId()!=null) expected.put(request.deliveryPhotoDocumentId(),Type.DELIVERY_PHOTO);
    validateDocuments(actor,tenant,expected,Target.TRIP_STOP,stop);return parent;
  }
  private void assignedTrip(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,UUID tractor,Type type) {
    auth.requireTenantCapability(actor,tenant,DocumentUploadAuthority.capability(type));
    if(trip==null) throw failure(VALIDATION_FAILED);
    var parent=work.trip(tenant,trip,false).orElseThrow(()->failure(RESOURCE_NOT_FOUND));assigned(actor,tenant,parent,type);
    if(type!=Type.DAMAGE_PHOTO) {
      if(tractor==null || !tractor.equals(parent.tractorId())) throw failure(RESOURCE_NOT_FOUND);
      var eligibility=tractors.check(tenant,tractor);
      if(eligibility==TractorPlanningPolicy.Eligibility.NOT_FOUND) throw failure(RESOURCE_NOT_FOUND);
      if(eligibility!=TractorPlanningPolicy.Eligibility.ELIGIBLE) throw failure(RESOURCE_CONFLICT);
    }
  }
  private void assigned(AuthenticatedUserPrincipal actor,UUID tenant,EvidenceWorkDirectory.Trip parent,Type type) {
    UUID driver=uploads.require(actor,tenant,type,true);
    if(!driver.equals(parent.driverId())) throw failure(RESOURCE_NOT_FOUND);
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,true);
    if(Set.of("COMPLETED","CANCELLED").contains(parent.status())) throw failure(INVALID_STATE_TRANSITION);
  }
  private void validateDocuments(AuthenticatedUserPrincipal actor,UUID tenant,Map<UUID,Type> expected,Target target,UUID resource) {
    var scope=access.readScope(actor,tenant);
    for(UUID id:expected.keySet().stream().sorted().toList()) {
      DocumentRepository.Row document;
      try { document=documents.shared(tenant,id); }
      catch(DocumentFailure e) { if(e.status==404) throw failure(RESOURCE_NOT_FOUND);throw e; }
      if(!actor.userId().equals(document.uploader())) throw failure(RESOURCE_NOT_FOUND);
      if(document.type()!=expected.get(id)) throw failure(VALIDATION_FAILED);
      if(document.status()!=Status.UPLOADED) throw failure(UPLOAD_NOT_COMPLETE);
      var links=documents.links(tenant,id);
      if(links.stream().noneMatch(l->l.entityType()==target && l.entityId().equals(resource))
          || resources.visible(actor,tenant,links,scope).size()!=links.size()) throw failure(RESOURCE_NOT_FOUND);
    }
  }
  static void required(List<UUID> ids) {
    if(ids==null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) throw failure(EVIDENCE_REQUIRED);
    if(ids.size()>50 || new HashSet<>(ids).size()!=ids.size()) throw failure(VALIDATION_FAILED);
  }
  static String comment(String value) { return value==null || value.strip().isEmpty()?null:value.strip(); }
  private static Map<UUID,Type> expected(List<UUID> ids,Type type) { var result=new HashMap<UUID,Type>();ids.forEach(id->result.put(id,type));return result; }
  private static EvidenceValidationException failure(EvidenceValidationException.Code code) { return new EvidenceValidationException(code); }
}
