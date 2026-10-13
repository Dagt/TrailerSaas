package com.trailersaas.yard.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
class YardMoveController {
  private final YardMoveService service;
  YardMoveController(YardMoveService service){this.service=service;}
  @PostMapping("/api/v1/tenants/{tenantId}/yard-moves")
  YardMoveService.Result move(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader(name="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty())throw YardFailure.invalid();return service.move(actor,tenantId,key,body);
  }
}
