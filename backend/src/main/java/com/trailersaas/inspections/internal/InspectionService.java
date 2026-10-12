package com.trailersaas.inspections.internal;

import com.trailersaas.drivers.*;
import com.trailersaas.identity.*;
import com.trailersaas.documents.OperationalEvidencePolicy;
import com.trailersaas.tractors.TractorPlanningPolicy;
import com.trailersaas.trips.EvidenceWorkDirectory;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Owns inspection creation; Documents validates/attaches bytes but never approves domain state. */
@Service @Transactional
class InspectionService {
  private final ShiftService shifts;
  private final ShiftRepository repository;
  private final DriverIdentityPolicy drivers;
  private final TenantContext context;
  private final TenantAuthorization auth;
  private final EvidenceWorkDirectory work;
  private final TractorPlanningPolicy tractors;
  private final OperationalEvidencePolicy evidence;
  private final MobileOperationLedger ledger;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  InspectionService(ShiftService s,ShiftRepository r,DriverIdentityPolicy d,TenantContext c,TenantAuthorization a,EvidenceWorkDirectory w,TractorPlanningPolicy t,OperationalEvidencePolicy e,MobileOperationLedger l,JdbcTemplate j,JsonMapper m) {shifts=s;repository=r;drivers=d;context=c;auth=a;work=w;tractors=t;evidence=e;ledger=l;jdbc=j;json=m;}
  private UUID prepare(AuthenticatedUserPrincipal actor,UUID tenant,UUID shift,UUID trip,UUID tractor,Capability capability,Instant at,BigDecimal odometer) {
    if(shift==null || tractor==null || odometer==null || odometer.signum()<0 || odometer.compareTo(new BigDecimal("1000000000000"))>0 || odometer.scale()>3)throw ShiftFailure.invalid();
    ShiftService.time(at);context.establishTenant(tenant);auth.requireTenantCapability(actor,tenant,capability);
    var own=drivers.activeForUser(tenant,actor.userId()).orElseThrow(ShiftFailure::missing);
    EvidenceWorkDirectory.Trip parent=null;
    if(trip!=null)parent=work.trip(tenant,trip,true).orElseThrow(ShiftFailure::missing);
    else if(capability==Capability.PRETRIP_CREATE) {
      // Accepted UX: a Driver without an equipment assignment contacts Dispatch.
      // Resolve a real parent BEFORE the exclusive Driver lock; never reverse execution's order.
      var candidates=jdbc.query("SELECT id FROM trips WHERE tenant_id=? AND current_driver_id=? AND current_tractor_id=? AND status NOT IN ('COMPLETED','CANCELLED') ORDER BY id LIMIT 1",(r,n)->r.getObject(1,UUID.class),tenant,own,tractor);
      if(candidates.isEmpty())throw ShiftFailure.missing();
      parent=work.trip(tenant,candidates.getFirst(),true).orElseThrow(ShiftFailure::missing);
    }
    if(parent!=null && (!own.equals(parent.driverId()) || !tractor.equals(parent.tractorId()) || capability==Capability.PRETRIP_CREATE && Set.of("COMPLETED","CANCELLED").contains(parent.status())))throw ShiftFailure.missing();
    UUID driver=shifts.authorize(actor,tenant,capability);
    var current=repository.own(tenant,driver,shift,true);
    // Ownership is rechecked before replay; closed-state checks belong to the new action.
    if(tractors.check(tenant,tractor)!=TractorPlanningPolicy.Eligibility.ELIGIBLE)throw ShiftFailure.missing();
    if(trip==null && capability==Capability.POSTTRIP_CREATE && !jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inspections WHERE tenant_id=? AND shift_id=? AND driver_id=? AND tractor_id=? AND inspection_type='PRE_TRIP')",Boolean.class,tenant,shift,driver,tractor))throw ShiftFailure.missing();
    if(at.isBefore(current.onDutyAt()))throw ShiftFailure.state();
    return driver;
  }
  DriverShiftContext.Inspection pre(AuthenticatedUserPrincipal actor,UUID tenant,UUID key,ShiftContracts.PreTrip r) {
    if(r==null || key==null)throw ShiftFailure.invalid();checklist(r.checklist());
    UUID driver=prepare(actor,tenant,r.shiftId(),r.tripId(),r.tractorId(),Capability.PRETRIP_CREATE,r.occurredAt(),r.odometer());
    var hash=ShiftService.hash(json.writeValueAsString(canonical(r)));
    return ledger.execute(tenant,actor.userId(),key,"INSPECTION_PRE_TRIP",hash,"INSPECTION",DriverShiftContext.Inspection::id,DriverShiftContext.Inspection.class,()-> {
      open(tenant,driver,r.shiftId());
      var result=insert(tenant,driver,r.shiftId(),r.tripId(),r.tractorId(),"PRE_TRIP",r.odometer(),null,null,null,r.checklist(),r.occurredAt());
      evidence.attachPreTripInspection(actor,tenant,result.id(),r.tripId(),r.tractorId(),r.documentIds());return result;
    });
  }
  DriverShiftContext.Inspection post(AuthenticatedUserPrincipal actor,UUID tenant,UUID key,ShiftContracts.PostTrip r) {
    if(r==null || key==null)throw ShiftFailure.invalid();text(r.damages());text(r.failures());text(r.comments());
    UUID driver=prepare(actor,tenant,r.shiftId(),r.tripId(),r.tractorId(),Capability.POSTTRIP_CREATE,r.occurredAt(),r.odometer());
    var canonical=new ShiftContracts.PostTrip(r.shiftId(),r.tripId(),r.tractorId(),r.odometer().stripTrailingZeros(),text(r.damages()),text(r.failures()),text(r.comments()),r.occurredAt());
    return ledger.execute(tenant,actor.userId(),key,"INSPECTION_POST_TRIP",ShiftService.hash(json.writeValueAsString(canonical)),"INSPECTION",DriverShiftContext.Inspection::id,DriverShiftContext.Inspection.class,()-> {
      open(tenant,driver,r.shiftId());
      if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inspections WHERE tenant_id=? AND shift_id=? AND tractor_id=? AND occurred_at>?)",Boolean.class,tenant,r.shiftId(),r.tractorId(),Timestamp.from(r.occurredAt())))throw ShiftFailure.state();
      return insert(tenant,driver,r.shiftId(),r.tripId(),r.tractorId(),"POST_TRIP",r.odometer(),text(r.damages()),text(r.failures()),text(r.comments()),null,r.occurredAt());
    });
  }
  private void open(UUID tenant,UUID driver,UUID shift) {if(!"ON_DUTY".equals(repository.own(tenant,driver,shift,true).state()))throw ShiftFailure.state();}
  private DriverShiftContext.Inspection insert(UUID tenant,UUID driver,UUID shift,UUID trip,UUID tractor,String type,BigDecimal odometer,String damages,String failures,String comments,Map<String,Object> checklist,Instant at) {
    UUID id=UUID.randomUUID();Instant recorded=Instant.now();
    jdbc.update("""
      INSERT INTO inspections(id,tenant_id,driver_id,tractor_id,shift_id,trip_id,inspection_type,odometer,damages,failures,comments,checklist_data,occurred_at,created_at)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,?)
      """,id,tenant,driver,tractor,shift,trip,type,odometer,damages,failures,comments,checklist==null?null:json.writeValueAsString(checklist),Timestamp.from(at),Timestamp.from(recorded));
    return jdbc.query("SELECT * FROM inspections WHERE tenant_id=? AND id=?",(r,n)->new DriverShiftContext.Inspection(r.getObject("id",UUID.class),r.getObject("shift_id",UUID.class),r.getObject("trip_id",UUID.class),r.getObject("tractor_id",UUID.class),r.getString("inspection_type"),r.getBigDecimal("odometer"),ShiftRepository.instant(r,"occurred_at"),ShiftRepository.instant(r,"created_at")),tenant,id).getFirst();
  }
  static String text(String value) {if(value==null)return null;if(value.length()>4000)throw ShiftFailure.invalid();var t=value.strip();return t.isBlank()?null:t;}
  static void checklist(Map<String,Object> checklist) {
    if(checklist==null || !checklist.keySet().equals(Set.of("items")) || !(checklist.get("items") instanceof List<?> items) || items.size()>100)throw ShiftFailure.invalid();
    for(var item:items) {
      if(!(item instanceof Map<?,?> fields) || fields.isEmpty() || fields.size()>20)throw ShiftFailure.invalid();
      for(var e:fields.entrySet())if(!(e.getKey() instanceof String k) || k.isBlank() || k.length()>80 || !(e.getValue() instanceof String || e.getValue() instanceof Boolean || e.getValue() instanceof Number) || e.getValue().toString().length()>2000)throw ShiftFailure.invalid();
    }
  }
  private ShiftContracts.PreTrip canonical(ShiftContracts.PreTrip r) {
    var items=new ArrayList<Map<String,Object>>();for(var item:(List<?>)r.checklist().get("items")) {var sorted=new TreeMap<String,Object>();((Map<?,?>)item).forEach((k,v)->sorted.put((String)k,v));items.add(sorted);}
    return new ShiftContracts.PreTrip(r.shiftId(),r.tripId(),r.tractorId(),r.odometer().stripTrailingZeros(),Map.of("items",items),r.documentIds(),r.occurredAt());
  }
}
