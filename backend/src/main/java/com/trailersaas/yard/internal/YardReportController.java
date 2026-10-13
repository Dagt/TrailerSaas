package com.trailersaas.yard.internal;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/yard/discrepancy-reports")
class YardReportController {
  private final YardReportService service;
  YardReportController(YardReportService service){this.service=service;}
  @PostMapping
  YardReportService.Result report(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader(name="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty()) throw YardFailure.invalid();return service.create(actor,tenantId,key,body);
  }
  @GetMapping
  YardDtos.Page<YardReportService.Report> review(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestParam(required=false) UUID cursor,@RequestParam(defaultValue="50")int limit,@RequestParam Map<String,String> query) {
    if(!Set.of("cursor","limit").containsAll(query.keySet())) throw YardFailure.invalid();return service.review(actor,tenantId,cursor,limit);
  }
}
