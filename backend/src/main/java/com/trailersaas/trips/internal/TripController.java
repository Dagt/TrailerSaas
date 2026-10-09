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

  private final TripAssignmentService assignments;
  private final TripOperationalService operations;

  TripController(
      TripService service, TripAssignmentService assignments, TripOperationalService operations) {
    this.service = service;
    this.assignments = assignments;
    this.operations = operations;
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

  @PostMapping("/{tripId}/driver-assignments")
  Detail driver(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return assignments.assign(actor, tenantId, tripId, TripAssignmentService.Kind.DRIVER, body);
  }

  @PostMapping("/{tripId}/tractor-assignments")
  Detail tractor(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return assignments.assign(actor, tenantId, tripId, TripAssignmentService.Kind.TRACTOR, body);
  }

  @PostMapping("/{tripId}/planned-trailer-assignments")
  Detail trailer(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return assignments.assign(actor, tenantId, tripId, TripAssignmentService.Kind.TRAILER, body);
  }

  @PostMapping("/{tripId}/trailer-substitution-decisions")
  Detail substitution(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return assignments.decide(actor, tenantId, tripId, body);
  }

  @PostMapping("/{tripId}/reroutes")
  Detail reroute(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return operations.reroute(actor, tenantId, tripId, body);
  }

  @PostMapping("/{tripId}/cancellations")
  Detail cancel(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestBody Map<String, Object> body,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return operations.cancel(actor, tenantId, tripId, body);
  }

  @GetMapping("/{tripId}/timeline")
  Timeline timeline(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID tripId,
      @RequestParam Map<String, String> query) {
    TripValidation.fields(query, Set.of());
    return service.timeline(actor, tenantId, tripId);
  }
}
