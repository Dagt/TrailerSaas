package com.trailersaas.drivers.internal;

import com.trailersaas.identity.*;
import com.trailersaas.drivers.DriverShiftContext;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Own operational profile only; never a Driver directory or an execution command. */
@RestController
class DriverSelfController {
  private final EntityManager em;
  private final TenantContext context;
  private final TenantAuthorization auth;
  private final org.springframework.beans.factory.ObjectProvider<DriverShiftContext> shifts;

  DriverSelfController(EntityManager em, TenantContext context, TenantAuthorization auth,org.springframework.beans.factory.ObjectProvider<DriverShiftContext> shifts) {
    this.em = em; this.context = context; this.auth = auth;this.shifts=shifts;
  }

  record Profile(UUID id, String name, boolean active, long version,DriverShiftContext.Home shiftContext) {}

  @GetMapping("/api/v1/tenants/{tenantId}/driver/me")
  @Transactional(readOnly = true)
  Profile get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId, @RequestParam Map<String,String> query) {
    if (!query.isEmpty()) throw DriverFailure.invalid();
    context.establishTenant(tenantId);
    var driver = em.createQuery("select d from DriverEntity d where d.tenantId=:tenant and d.userId=:user and d.active=true", DriverEntity.class)
        .setParameter("tenant", tenantId).setParameter("user", actor.userId()).getResultStream().findFirst();
    auth.requireAssignedWorkCapability(actor, tenantId, Capability.TRIP_VIEW_ASSIGNED, driver.isPresent());
    var d = driver.orElseThrow(DriverFailure::missing);
    var reader=shifts.getIfAvailable();
    return new Profile(d.id, d.name, d.active, d.version,reader==null?null:reader.read(tenantId,d.id));
  }
}
