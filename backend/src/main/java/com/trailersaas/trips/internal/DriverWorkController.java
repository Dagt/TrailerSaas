package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
class DriverWorkController {
  private final DriverWorkService service;
  DriverWorkController(DriverWorkService service) { this.service = service; }

  @GetMapping("/api/v1/tenants/{tenantId}/driver/me/trips")
  DriverWorkService.Page get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId, @RequestParam Map<String,String> query) {
    return service.list(actor, tenantId, query);
  }
}
