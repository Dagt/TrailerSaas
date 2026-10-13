package com.trailersaas.yard.internal;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/yard/out-of-service")
class YardOosController {
  private final YardOosService service;
  YardOosController(YardOosService service){this.service=service;}
  @PostMapping
  YardOosService.Result mark(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader(name="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty()) throw YardFailure.invalid();return service.execute(actor,tenantId,key,body);
  }
}
