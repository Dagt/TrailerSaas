package com.trailersaas.drivers.internal;

import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.trailersaas.drivers.internal.DriverDtos.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/drivers")
class DriverController {
    private final DriverService service;
    DriverController(DriverService service) { this.service=service; }
    @ModelAttribute void rejectFilters(@RequestParam Map<String,String> parameters) {
        if(!parameters.isEmpty()) { throw DriverFailure.invalid(); }
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Driver create(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestBody Map<String,Object> fields) {
        return service.create(actor,tenantId,fields);
    }
    @GetMapping
    Items list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId) { return service.list(actor,tenantId); }
    @GetMapping("/{driverId}")
    Driver get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID driverId) {
        return service.get(actor,tenantId,driverId);
    }
    @PatchMapping("/{driverId}")
    Driver patch(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID driverId,@RequestBody Map<String,Object> fields) {
        return service.patch(actor,tenantId,driverId,fields);
    }
    @PostMapping("/{driverId}/user-link")
    Driver link(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID driverId,@RequestBody Map<String,Object> fields) {
        return service.link(actor,tenantId,driverId,fields);
    }
    @DeleteMapping("/{driverId}/user-link") @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlink(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID driverId,@RequestBody Map<String,Object> fields) {
        service.unlink(actor,tenantId,driverId,fields);
    }
}
