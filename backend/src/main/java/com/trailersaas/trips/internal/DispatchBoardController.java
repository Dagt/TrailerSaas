package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/dispatch/board")
class DispatchBoardController {
  private final DispatchBoardService service;

  DispatchBoardController(DispatchBoardService service) {
    this.service = service;
  }

  @GetMapping
  DispatchBoardService.Board get(
      @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,
      @RequestParam Map<String, String> query) {
    return service.get(actor, tenantId, query);
  }
}
