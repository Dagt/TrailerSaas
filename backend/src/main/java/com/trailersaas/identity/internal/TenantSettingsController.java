package com.trailersaas.identity.internal;

import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/settings")
class TenantSettingsController {
    private final TenantSettingsService service;
    TenantSettingsController(TenantSettingsService service) { this.service = service; }
    @ModelAttribute void rejectParameters(@RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty()) { throw TenantSettingsFailure.invalid("query"); }
    }
    @GetMapping TenantSettingsService.Settings get(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId) {
        return service.get(user, tenantId);
    }
    @PatchMapping TenantSettingsService.Settings patch(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID tenantId,
            @RequestBody Map<String, Object> fields) { return service.patch(user, tenantId, fields); }
}
