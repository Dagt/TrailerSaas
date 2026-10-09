package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.trailers.TrailerCouplingTransactions.Result;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/trips/{tripId}")
class TripCouplingController {
  private final TripCouplingService service;

  TripCouplingController(TripCouplingService service) {
    this.service = service;
  }

  @PostMapping("/hooks")
  Result hook(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestHeader(name = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return service.hook(actor, tenantId, tripId, key, body);
  }

  @PostMapping("/drops")
  Result drop(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestHeader(name = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return service.drop(actor, tenantId, tripId, key, body);
  }
}
