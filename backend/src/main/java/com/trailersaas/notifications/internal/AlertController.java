package com.trailersaas.notifications.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/alerts")
class AlertController {
 final AlertService service;AlertController(AlertService service){this.service=service;}
 @GetMapping AlertService.Page list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestParam Map<String,String> query){return service.list(actor,tenantId,query);}
 @GetMapping("/{alertId}") AlertService.Alert detail(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID alertId,@RequestParam Map<String,String> query){if(!query.isEmpty())throw AlertFailure.invalid();return service.detail(actor,tenantId,alertId);}
 @PostMapping("/{alertId}/acknowledgement") AlertService.Alert acknowledge(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID alertId,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query){if(!query.isEmpty())throw AlertFailure.invalid();return service.transition(actor,tenantId,alertId,body,false);}
 @PostMapping("/{alertId}/resolution") AlertService.Alert resolve(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID alertId,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query){if(!query.isEmpty())throw AlertFailure.invalid();return service.transition(actor,tenantId,alertId,body,true);}
}
