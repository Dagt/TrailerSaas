package com.trailersaas.yard.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/yard")
class YardController {
  private final YardContextService service;
  YardController(YardContextService service) { this.service=service; }
  @GetMapping("/sites")
  YardDtos.Page<YardDtos.Site> sites(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@RequestParam Map<String,String> query) {
    pageFields(query); return service.sites(actor,tenantId,cursor(query),limit(query,50));
  }
  @GetMapping("/sites/{siteId}/locations")
  YardDtos.Page<YardDtos.Location> locations(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID siteId,@RequestParam Map<String,String> query) {
    pageFields(query); return service.locations(actor,tenantId,siteId,cursor(query),limit(query,200));
  }
  @GetMapping("/trailers/{trailerId}")
  YardDtos.Trailer trailer(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID trailerId,@RequestParam Map<String,String> query) {
    if(!query.isEmpty())throw YardFailure.invalid(); return service.trailer(actor,tenantId,trailerId);
  }
  private static void pageFields(Map<String,String> query) { if(!Set.of("cursor","limit").containsAll(query.keySet()))throw YardFailure.invalid(); }
  private static UUID cursor(Map<String,String> query) {
    try { return query.containsKey("cursor")?UUID.fromString(query.get("cursor")):null; } catch(RuntimeException e){throw YardFailure.invalid();}
  }
  private static int limit(Map<String,String> query,int maximum) {
    try { int value=query.containsKey("limit")?Integer.parseInt(query.get("limit")):maximum;
      if(value<1||value>maximum)throw YardFailure.invalid();return value;
    } catch(RuntimeException e){throw YardFailure.invalid();}
  }
}
