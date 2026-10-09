package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/trips")
class TripController {
  private final TripService service;

  TripController(TripService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Detail create(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return service.create(actor, tenantId, body);
  }

  @GetMapping
  Page list(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestParam Map<String, String> query) {
    return service.list(actor, tenantId, query);
  }

  @GetMapping("/{tripId}")
  Detail get(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return service.get(actor, tenantId, tripId);
  }
}
