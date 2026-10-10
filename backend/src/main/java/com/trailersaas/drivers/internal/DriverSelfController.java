package com.trailersaas.drivers.internal;

import com.trailersaas.identity.*;
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

  DriverSelfController(EntityManager em, TenantContext context, TenantAuthorization auth) {
    this.em = em; this.context = context; this.auth = auth;
  }

  record Profile(UUID id, String name, boolean active, long version) {}

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
    return new Profile(d.id, d.name, d.active, d.version);
  }
}
