package com.trailersaas.inspections.internal;

import static com.trailersaas.inspections.internal.RoadContracts.*;
import com.trailersaas.documents.OperationalEvidencePolicy;
import com.trailersaas.documents.EvidenceValidationException;
import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.orders.OrderFulfillment;
import com.trailersaas.trips.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Application boundary: real domain transitions and evidence join one Tenant transaction. */
@Service @Transactional
class RoadOperationsService {
  private final TenantContext context;
  private final TenantAuthorization auth;
  private final DriverIdentityPolicy drivers;
  private final EvidenceWorkDirectory work;
  private final DriverDeliveryExecution execution;
  private final OperationalEvidencePolicy evidence;
  private final OrderFulfillment orders;
  private final MobileOperationLedger ledger;
  private final ShiftRepository shifts;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  RoadOperationsService(TenantContext c,TenantAuthorization a,DriverIdentityPolicy d,EvidenceWorkDirectory w,
      DriverDeliveryExecution e,OperationalEvidencePolicy p,OrderFulfillment o,MobileOperationLedger l,
      ShiftRepository s,JdbcTemplate j,JsonMapper mapper) {
    context=c;auth=a;drivers=d;work=w;execution=e;evidence=p;orders=o;ledger=l;shifts=s;jdbc=j;json=mapper;
  }
  DriverDeliveryExecution.Result delivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,UUID key,Delivery request) {
    if(request==null || key==null || request.evidenceMode()==null)throw ShiftFailure.invalid();
    ShiftService.time(request.occurredAt());
    if(request.evidenceMode()==OperationalEvidencePolicy.Mode.SIGNED_BOL && (request.comment()!=null || request.deliveryPhotoDocumentId()!=null))throw ShiftFailure.invalid();
    String comment=text(request.comment(),false);
    var input=new OperationalEvidencePolicy.Delivery(request.evidenceMode(),request.bolDocumentId(),request.deliveryPhotoDocumentId(),comment);
    context.establishTenant(tenant);
    var locked=orders.lockDeliveryOrders(tenant,stop);
    execution.prepare(actor,tenant,stop);
    evidence.validateDeliveryForReplay(actor,tenant,stop,input);
    return ledger.execute(tenant,actor.userId(),key,"DELIVERY_COMPLETION",hash(stop,input,request.occurredAt()),"TRIP_STOP",DriverDeliveryExecution.Result::stopId,DriverDeliveryExecution.Result.class,()-> {
      var result=new DriverDeliveryExecution.Result[1];
      evidence.recordDelivery(actor,tenant,stop,input,()-> {
        result[0]=execution.complete(actor,tenant,stop,key,request.occurredAt());
        orders.delivered(actor,tenant,stop,key,request.occurredAt(),locked);
      });
      return result[0];
    });
  }
  private EvidenceWorkDirectory.Trip assigned(AuthenticatedUserPrincipal actor,UUID tenant,UUID trip,Capability capability) {
    context.establishTenant(tenant);auth.requireTenantCapability(actor,tenant,capability);
    if(trip==null)throw ShiftFailure.invalid();
    var parent=work.trip(tenant,trip,true).orElseThrow(ShiftFailure::missing);
    var driver=drivers.lockActiveForUser(tenant,actor.userId());
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,driver.isPresent());
    auth.requireTenantCapability(actor,tenant,capability);
    if(!driver.orElseThrow(ShiftFailure::missing).equals(parent.driverId()))throw ShiftFailure.missing();
    return parent;
  }
  FuelResult fuel(AuthenticatedUserPrincipal actor,UUID tenant,UUID key,Fuel request) {
    if(request==null || key==null || request.tractorId()==null)throw ShiftFailure.invalid();
    ShiftService.time(request.occurredAt());odometer(request.odometer());
    var trip=assigned(actor,tenant,request.tripId(),Capability.FUEL_EVENT_CREATE);
    if(!request.tractorId().equals(trip.tractorId()))throw ShiftFailure.missing();
    evidence.validateFuel(actor,tenant,trip.id(),trip.tractorId(),request.receiptDocumentId());
    return ledger.execute(tenant,actor.userId(),key,"FUEL_EVENT",hash(request.tripId(),request.tractorId(),request.odometer().stripTrailingZeros().toPlainString(),request.receiptDocumentId(),request.occurredAt()),"FUEL_EVENT",FuelResult::id,FuelResult.class,()-> {
      if(!Set.of("IN_TRANSIT","AT_STOP").contains(trip.status()))throw ShiftFailure.state();
      var shift=shifts.open(tenant,trip.driverId());
      if(shift==null || request.occurredAt().isBefore(shift.onDutyAt()))throw ShiftFailure.conflict();
      UUID id=UUID.randomUUID();Instant now=Instant.now();
      if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fuel_events WHERE tenant_id=? AND receipt_document_id=?)",Boolean.class,tenant,request.receiptDocumentId())))throw ShiftFailure.conflict();
      jdbc.update("INSERT INTO fuel_events VALUES(?,?,?,?,?,?,?,?,?)",id,tenant,trip.driverId(),trip.tractorId(),trip.id(),request.odometer(),request.receiptDocumentId(),Timestamp.from(request.occurredAt()),Timestamp.from(now));
      return new FuelResult(id,trip.id(),trip.tractorId(),request.odometer(),request.receiptDocumentId(),request.occurredAt(),now);
    });
  }
  ProblemResult problem(AuthenticatedUserPrincipal actor,UUID tenant,UUID tripId,UUID key,Problem request) {
    if(request==null || key==null || request.category()==null)throw ShiftFailure.invalid();
    ShiftService.time(request.occurredAt());String comment=text(request.comment(),true);
    var ids=request.documentIds()==null?List.<UUID>of():request.documentIds();
    if(ids.size()>50 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())throw ShiftFailure.invalid();
    var trip=assigned(actor,tenant,tripId,Capability.EXCEPTION_REPORT);
    if(request.stopId()!=null && !work.stop(tenant,request.stopId(),true).orElseThrow(ShiftFailure::missing).trip().id().equals(tripId))throw ShiftFailure.missing();
    if(request.category()==Category.EQUIPMENT_DAMAGE || !ids.isEmpty())evidence.validateDriverDamage(actor,tenant,tripId,request.stopId(),ids);
    var sorted=ids.stream().sorted().toList();
    return ledger.execute(tenant,actor.userId(),key,"OPERATIONAL_EXCEPTION",hash(tripId,request.stopId(),request.category(),comment,sorted,request.occurredAt()),"OPERATIONAL_EXCEPTION",ProblemResult::id,ProblemResult.class,()-> {
      if(Set.of("COMPLETED","CANCELLED").contains(trip.status()))throw ShiftFailure.state();
      UUID id=UUID.randomUUID();Instant now=Instant.now();
      jdbc.update("INSERT INTO operational_exceptions VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,tenant,tripId,request.stopId(),trip.driverId(),actor.userId(),request.category().name(),comment,Timestamp.from(request.occurredAt()),Timestamp.from(now),key);
      for(var document:sorted)jdbc.update("INSERT INTO operational_exception_documents VALUES(?,?,?)",tenant,id,document);
      return new ProblemResult(id,tripId,request.stopId(),request.category(),comment,sorted,request.occurredAt(),now);
    });
  }
  private String hash(Object... fields){return ShiftService.hash(json.writeValueAsString(Arrays.asList(fields)));}
  static String text(String raw,boolean required) {
    if(raw==null){if(required)throw ShiftFailure.invalid();return null;}
    int start=0,end=raw.length();
    while(start<end && space(raw.codePointAt(start)))start+=Character.charCount(raw.codePointAt(start));
    while(start<end && space(raw.codePointBefore(end)))end-=Character.charCount(raw.codePointBefore(end));
    String result=raw.substring(start,end);
    if(result.length()>4000 || (required && result.isEmpty()))throw ShiftFailure.invalid();return result.isEmpty()?null:result;
  }
  private static boolean space(int cp){return Character.isWhitespace(cp)||Character.isSpaceChar(cp);}
  private static void odometer(BigDecimal value){if(value==null || value.signum()<0 || value.compareTo(new BigDecimal("1000000000000"))>0 || value.stripTrailingZeros().scale()>3)throw ShiftFailure.invalid();}
}
