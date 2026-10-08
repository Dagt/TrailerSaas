package com.trailersaas.tractors.internal;

import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.trailersaas.tractors.internal.TractorDtos.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/tractors")
class TractorController {
    private final TractorService service;
    TractorController(TractorService service) { this.service=service; }
    @ModelAttribute void rejectFilters(@RequestParam Map<String,String> parameters) {
        if(!parameters.isEmpty()) { throw TractorFailure.invalid(); }
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Tractor create(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestBody Map<String,Object> fields) {
        return service.create(actor,tenantId,fields);
    }
    @GetMapping
    Items list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId) { return service.list(actor,tenantId); }
    @GetMapping("/{tractorId}")
    Tractor get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID tractorId) {
        return service.get(actor,tenantId,tractorId);
    }
    @PatchMapping("/{tractorId}")
    Tractor patch(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID tractorId,@RequestBody Map<String,Object> fields) {
        return service.patch(actor,tenantId,tractorId,fields);
    }
}
