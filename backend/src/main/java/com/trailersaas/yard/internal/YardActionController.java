package com.trailersaas.yard.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/yard")
class YardActionController {
  private final YardActionService service;
  YardActionController(YardActionService service) { this.service=service; }
  @PostMapping("/location-corrections")
  YardActionService.Result correct(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader(name="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty()) throw YardFailure.invalid();return service.execute(actor,tenantId,key,"LOCATION_CORRECTION",body);
  }
  @PostMapping("/load-state-changes")
  YardActionService.Result load(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader(name="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty()) throw YardFailure.invalid();return service.execute(actor,tenantId,key,"LOAD_STATE_CHANGE",body);
  }
}
