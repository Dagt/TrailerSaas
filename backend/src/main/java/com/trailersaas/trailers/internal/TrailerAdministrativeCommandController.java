package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.internal.TrailerCommandRequests.*;
import static com.trailersaas.trailers.internal.TrailerDtos.*;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/trailers/{trailerId}")
class TrailerAdministrativeCommandController {
  private final TrailerAdministrativeCommandService service;

  TrailerAdministrativeCommandController(TrailerAdministrativeCommandService service) {
    this.service = service;
  }

  @PostMapping("/location-corrections")
  Trailer correct(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody LocationCorrection input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.correct(actor, tenantId, trailerId, input);
  }

  @PostMapping("/mark-location-unknown")
  Trailer unknown(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody MarkUnknown input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.unknown(actor, tenantId, trailerId, input);
  }

  @PostMapping("/load-state-changes")
  Trailer load(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody LoadChange input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.load(actor, tenantId, trailerId, input);
  }

  @PostMapping("/out-of-service")
  Trailer oos(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody OutOfService input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.oos(actor, tenantId, trailerId, input);
  }

  @PostMapping("/return-to-service")
  Trailer restore(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody ReturnToService input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.restore(actor, tenantId, trailerId, input);
  }

  @PostMapping("/customer-designation-changes")
  Trailer designate(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody CustomerDesignation input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.designate(actor, tenantId, trailerId, input);
  }
}
