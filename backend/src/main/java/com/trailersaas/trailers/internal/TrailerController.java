package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.internal.TrailerDtos.*;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/trailers")
class TrailerController {
  private final TrailerService service;
  private final TrailerReadService reads;

  TrailerController(TrailerService service, TrailerReadService reads) {
    this.service = service;
    this.reads = reads;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Trailer create(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestBody Map<String, Object> input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.create(actor, tenantId, input);
  }

  @GetMapping
  TrailerReadDtos.Items list(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestParam Map<String, String> filters) {
    return reads.list(actor, tenantId, filters);
  }

  @GetMapping("/{trailerId}")
  TrailerReadDtos.Detail get(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return reads.detail(actor, tenantId, trailerId);
  }

  @GetMapping("/{trailerId}/timeline")
  TrailerReadDtos.TimelinePage timeline(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestParam Map<String, String> parameters) {
    return reads.timeline(actor, tenantId, trailerId, parameters);
  }

  @PatchMapping("/{trailerId}")
  Trailer patch(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody Map<String, Object> input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.patch(actor, tenantId, trailerId, input);
  }

  @GetMapping("/{trailerId}/qr-identities")
  QrItems qrList(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    return service.qrList(actor, tenantId, trailerId);
  }

  @PostMapping("/{trailerId}/qr-identities")
  @ResponseStatus(HttpStatus.CREATED)
  Qr issue(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @RequestBody(required = false) Map<String, Object> input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    if (input != null && !input.isEmpty()) {
      throw TrailerFailure.invalid();
    }
    return service.issue(actor, tenantId, trailerId);
  }

  @PostMapping("/{trailerId}/qr-identities/{qrId}/revocation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @PathVariable UUID trailerId,
      @PathVariable UUID qrId,
      @RequestBody(required = false) Map<String, Object> input,
      @RequestParam Map<String, String> parameters) {
    TrailerValidation.noParameters(parameters);
    if (input != null && !input.isEmpty()) {
      throw TrailerFailure.invalid();
    }
    service.revoke(actor, tenantId, trailerId, qrId);
  }
}
