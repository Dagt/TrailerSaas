package com.trailersaas.customers.internal;

import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.trailersaas.customers.internal.MasterDataDtos.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class MasterDataController {
    private final MasterDataService service;
    MasterDataController(MasterDataService service) { this.service = service; }

    @ModelAttribute
    void rejectUnsupportedFilters(@RequestParam Map<String, String> parameters, jakarta.servlet.http.HttpServletRequest request) {
        boolean choices="GET".equals(request.getMethod()) && (request.getRequestURI().endsWith("/sites") || request.getRequestURI().matches(".*/sites/[^/]+/locations"));
        if (parameters.keySet().stream().anyMatch(k->!choices || !k.equals("customerId"))) { throw MasterDataFailure.invalid("query"); }
    }

    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    Customer createCustomer(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @RequestBody CreateCustomer request) {
        return service.createCustomer(user, tenantId, request);
    }
    @GetMapping("/customers")
    Items<Customer> customers(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId) {
        return service.customers(user, tenantId);
    }
    @GetMapping("/customers/{customerId}")
    Customer customer(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID customerId) {
        return service.customer(user, tenantId, customerId);
    }
    @PatchMapping("/customers/{customerId}")
    Customer updateCustomer(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID customerId, @RequestBody Map<String, Object> fields) {
        return service.updateCustomer(user, tenantId, customerId, fields);
    }
    @PostMapping("/sites") @ResponseStatus(HttpStatus.CREATED)
    Site createSite(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @RequestBody CreateSite request) {
        return service.createSite(user, tenantId, request);
    }
    @GetMapping("/sites")
    Items<Site> sites(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId,@RequestParam(required=false) UUID customerId) {
        return service.sites(user, tenantId,customerId);
    }
    @GetMapping("/sites/{siteId}")
    Site site(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId, @PathVariable UUID siteId) {
        return service.site(user, tenantId, siteId);
    }
    @PatchMapping("/sites/{siteId}")
    Site updateSite(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId,
            @PathVariable UUID siteId, @RequestBody Map<String, Object> fields) {
        return service.updateSite(user, tenantId, siteId, fields);
    }
    @PostMapping("/customers/{customerId}/sites") @ResponseStatus(HttpStatus.CREATED)
    CustomerSite associate(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID customerId, @RequestBody AssociateSite request) {
        return service.associate(user, tenantId, customerId, request);
    }
    @DeleteMapping("/customers/{customerId}/sites/{siteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void disassociate(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID customerId, @PathVariable UUID siteId) {
        service.disassociate(user, tenantId, customerId, siteId);
    }
    @PostMapping("/sites/{siteId}/locations") @ResponseStatus(HttpStatus.CREATED)
    Location createLocation(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID siteId, @RequestBody CreateLocation request) {
        return service.createLocation(user, tenantId, siteId, request);
    }
    @GetMapping("/sites/{siteId}/locations")
    Items<Location> locations(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID siteId,@RequestParam(required=false) UUID customerId) {
        return service.locations(user, tenantId, siteId,customerId);
    }
    @PatchMapping("/locations/{locationId}")
    Location updateLocation(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID locationId, @RequestBody Map<String, Object> fields) {
        return service.updateLocation(user, tenantId, locationId, fields);
    }
}
