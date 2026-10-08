package com.trailersaas.trailers.internal;
import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.trailersaas.trailers.internal.TrailerDtos.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/trailers")
class TrailerController {
    private final TrailerService service;
    TrailerController(TrailerService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    Trailer create(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
            @RequestBody Map<String,Object> input,@RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters); return service.create(actor,tenantId,input);
    }
    @GetMapping
    Items list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestParam Map<String,String> filters) {
        return service.list(actor,tenantId,filters);
    }
    @GetMapping("/{trailerId}")
    Trailer get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID trailerId,
            @RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters); return service.get(actor,tenantId,trailerId);
    }
    @PatchMapping("/{trailerId}")
    Trailer patch(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID trailerId,
            @RequestBody Map<String,Object> input,@RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters); return service.patch(actor,tenantId,trailerId,input);
    }
    @GetMapping("/{trailerId}/qr-identities")
    QrItems qrList(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID trailerId,
            @RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters); return service.qrList(actor,tenantId,trailerId);
    }
    @PostMapping("/{trailerId}/qr-identities") @ResponseStatus(HttpStatus.CREATED)
    Qr issue(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID trailerId,
            @RequestBody(required=false) Map<String,Object> input,@RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters);
        if(input!=null && !input.isEmpty()) { throw TrailerFailure.invalid(); }
        return service.issue(actor,tenantId,trailerId);
    }
    @PostMapping("/{trailerId}/qr-identities/{qrId}/revocation") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID trailerId,@PathVariable UUID qrId,
            @RequestBody(required=false) Map<String,Object> input,@RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters);
        if(input!=null && !input.isEmpty()) { throw TrailerFailure.invalid(); }
        service.revoke(actor,tenantId,trailerId,qrId);
    }
}
