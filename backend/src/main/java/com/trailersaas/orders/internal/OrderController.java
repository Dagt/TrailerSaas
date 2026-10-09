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
    OrderController(OrderService service) { this.service = service; }
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
}
