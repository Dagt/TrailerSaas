package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/driver/me/updates")
class DriverUpdatesController {
  private final DriverUpdatesService service;

  DriverUpdatesController(DriverUpdatesService service) {
    this.service = service;
  }

  @GetMapping
  TripContracts.DriverUpdates get(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestParam Map<String, String> query) {
    return service.list(actor, tenantId, query);
  }
}
