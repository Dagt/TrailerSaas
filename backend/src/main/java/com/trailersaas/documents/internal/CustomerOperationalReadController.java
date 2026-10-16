package com.trailersaas.documents.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/customers/{customerId}")
class CustomerOperationalReadController {
  private final CustomerOperationalReads reads;
  CustomerOperationalReadController(CustomerOperationalReads reads){this.reads=reads;}
  @GetMapping("/deliveries")
  CustomerReadContracts.Page<CustomerReadContracts.Delivery> deliveries(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID customerId,@RequestParam Map<String,String> query) {
    return reads.deliveries(actor,tenantId,customerId,query);
  }
  @GetMapping("/deliveries/{orderId}/{stopId}")
  CustomerReadContracts.Delivery delivery(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID customerId,@PathVariable UUID orderId,@PathVariable UUID stopId,@RequestParam Map<String,String> query) {
    return reads.delivery(actor,tenantId,customerId,orderId,stopId,query);
  }
  @GetMapping("/documents")
  CustomerReadContracts.Page<CustomerReadContracts.Document> documents(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID customerId,@RequestParam Map<String,String> query) {
    return reads.documents(actor,tenantId,customerId,query);
  }
}
