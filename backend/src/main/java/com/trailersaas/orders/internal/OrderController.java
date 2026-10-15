package com.trailersaas.orders.internal;

import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.trailersaas.orders.internal.OrderContracts.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/orders")
class OrderController {
    private final OrderService service;
    private final OrderMutationService mutations;
    OrderController(OrderService service, OrderMutationService mutations) { this.service = service; this.mutations = mutations; }
    @PatchMapping("/{orderId}")
    Detail patch(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @PathVariable UUID orderId, @RequestBody Map<String, Object> input, @RequestParam Map<String, String> parameters) {
        OrderValidation.fields(parameters, java.util.Set.of(), "query"); return mutations.patch(actor, tenantId, orderId, input);
    }
    @PostMapping("/{orderId}/cancellations")
    Detail cancel(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @PathVariable UUID orderId, @RequestBody Map<String, Object> input, @RequestParam Map<String, String> parameters) {
        OrderValidation.fields(parameters, java.util.Set.of(), "query"); return mutations.cancel(actor, tenantId, orderId, input);
    }
    @PostMapping("/{orderId}/completion")
    Detail complete(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @PathVariable UUID orderId, @RequestBody Map<String, Object> input, @RequestParam Map<String, String> parameters) {
        OrderValidation.fields(parameters, java.util.Set.of(), "query"); return mutations.complete(actor, tenantId, orderId, input);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Detail create(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> input, @RequestParam Map<String, String> parameters) {
        OrderValidation.fields(parameters, java.util.Set.of(), "query");
        return service.create(actor, tenantId, input);
    }
    @GetMapping
    Page list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @RequestParam Map<String, String> parameters) { return service.list(actor, tenantId, parameters); }
    @GetMapping("/{orderId}")
    Detail get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID tenantId,
            @PathVariable UUID orderId, @RequestParam Map<String, String> parameters) {
        OrderValidation.fields(parameters, java.util.Set.of(), "query");
        return service.get(actor, tenantId, orderId);
    }
    @GetMapping("/customer-context")
    Map<String,Object> customerContext(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
            @RequestParam UUID customerId,@RequestParam Map<String,String> parameters) {
        OrderValidation.fields(parameters,java.util.Set.of("customerId"),"query");
        return Map.of("customerId",customerId,"capabilities",service.customerActions(actor,tenantId,customerId));
    }
    @GetMapping("/{orderId}/progress")
    Progress progress(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
            @PathVariable UUID tenantId,@PathVariable UUID orderId,@RequestParam Map<String,String> parameters) {
        return service.progress(actor,tenantId,orderId,parameters);
    }
}
