package com.trailersaas.yard.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/yard-checks")
class YardCheckController {
  private final YardCheckService service;
  YardCheckController(YardCheckService service){this.service=service;}
  @PostMapping
  YardCheckDtos.Detail start(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestHeader(name="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty())throw YardFailure.invalid();return service.start(actor,tenantId,key,body);
  }
  @GetMapping
  YardDtos.Page<YardCheckDtos.Summary> list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestParam Map<String,String> q) {
    keys(q,"siteId","status","cursor","limit","from","to");
    return service.list(actor,tenantId,id(q,"siteId"),q.get("status"),id(q,"cursor"),limit(q,50,50),at(q,"from"),at(q,"to"));
  }
  @GetMapping("/{yardCheckId}")
  YardCheckDtos.Detail detail(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID yardCheckId,@RequestParam Map<String,String> q) {
    keys(q,"cursor","limit","expectedOnly","expectedVersion");
    if(q.containsKey("expectedOnly")&&!Set.of("true","false").contains(q.get("expectedOnly")))throw YardFailure.invalid();
    Long version=null;try {if(q.containsKey("expectedVersion")){version=Long.parseLong(q.get("expectedVersion"));if(version<0)throw YardFailure.invalid();}}catch(NumberFormatException e){throw YardFailure.invalid();}
    return service.detail(actor,tenantId,yardCheckId,id(q,"cursor"),limit(q,200,200),Boolean.parseBoolean(q.get("expectedOnly")),version);
  }
  static void keys(Map<String,String> q,String... allowed) {if(!Set.of(allowed).containsAll(q.keySet()))throw YardFailure.invalid();}
  @PostMapping("/{yardCheckId}/scans")
  YardCheckService.ScanResult scan(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID yardCheckId,@RequestHeader(name="Idempotency-Key",required=false)String key,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty())throw YardFailure.invalid();return service.scan(actor,tenantId,yardCheckId,key,body);
  }
  static UUID id(Map<String,String> q,String key){return q.containsKey(key)?YardCheckRequest.uuid(q.get(key)):null;}
  static Instant at(Map<String,String> q,String key){return q.containsKey(key)?YardCheckRequest.time(q.get(key)):null;}
  static int limit(Map<String,String> q,int fallback,int max) {try {int n=q.containsKey("limit")?Integer.parseInt(q.get("limit")):fallback;if(n<1||n>max)throw YardFailure.invalid();return n;}catch(NumberFormatException e){throw YardFailure.invalid();}}
}
