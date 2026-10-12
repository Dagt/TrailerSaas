package com.trailersaas.inspections.internal;

import com.trailersaas.drivers.*;
import com.trailersaas.identity.*;
import com.trailersaas.inspections.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
class ShiftService {
  private final TenantContext context;
  private final TenantAuthorization auth;
  private final DriverIdentityPolicy drivers;
  private final MobileOperationLedger ledger;
  private final ShiftRepository repository;
  private final ApplicationEventPublisher events;
  ShiftService(TenantContext c,TenantAuthorization a,DriverIdentityPolicy d,MobileOperationLedger l,ShiftRepository r,ApplicationEventPublisher e) {context=c;auth=a;drivers=d;ledger=l;repository=r;events=e;}
  UUID authorize(AuthenticatedUserPrincipal actor,UUID tenant,Capability capability) {
    context.establishTenant(tenant);auth.requireTenantCapability(actor,tenant,capability);
    UUID driver=drivers.exclusiveActiveForUser(tenant,actor.userId()).orElseThrow(ShiftFailure::missing);
    auth.requireTenantCapability(actor,tenant,capability);return driver;
  }
  static void time(Instant at) {if(at==null || at.isAfter(Instant.now()))throw ShiftFailure.invalid();}
  static String hash(String value) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
  }
  DriverShiftContext.Shift start(AuthenticatedUserPrincipal actor,UUID tenant,UUID key,ShiftContracts.OnDuty request) {
    if(request==null || key==null)throw ShiftFailure.invalid();time(request.occurredAt());
    UUID driver=authorize(actor,tenant,Capability.SHIFT_START);
    return ledger.execute(tenant,actor.userId(),key,"SHIFT_ON_DUTY",hash(request.occurredAt().toString()),"SHIFT",DriverShiftContext.Shift::id,DriverShiftContext.Shift.class,()-> {
      var result=repository.create(tenant,driver,request.occurredAt());events.publishEvent(new DriverWentOnDuty(tenant,driver,result.id(),actor.userId(),request.occurredAt()));return result;
    });
  }
  DriverShiftContext.Shift end(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,UUID key,ShiftContracts.OffDuty request) {
    if(request==null || key==null || request.expectedShiftState()!=ShiftContracts.ExpectedState.ON_DUTY)throw ShiftFailure.invalid();time(request.occurredAt());
    UUID driver=authorize(actor,tenant,Capability.SHIFT_END);repository.own(tenant,driver,id,true);
    return ledger.execute(tenant,actor.userId(),key,"SHIFT_OFF_DUTY",hash(id+"|"+request.occurredAt()+"|ON_DUTY"),"SHIFT",DriverShiftContext.Shift::id,DriverShiftContext.Shift.class,()-> {
      var result=repository.close(tenant,driver,id,request.occurredAt());events.publishEvent(new DriverWentOffDuty(tenant,driver,id,actor.userId(),request.occurredAt()));return result;
    });
  }
}
