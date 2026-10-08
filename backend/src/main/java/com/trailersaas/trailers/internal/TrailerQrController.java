package com.trailersaas.trailers.internal;
import java.util.Map;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
class TrailerQrController {
    private final TrailerService service;
    TrailerQrController(TrailerService service) { this.service=service; }
    @GetMapping("/api/v1/trailer-qr/{publicToken}")
    TrailerDtos.Scan resolve(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable String publicToken,
            @RequestParam Map<String,String> parameters) {
        TrailerValidation.noParameters(parameters); return service.resolve(actor,publicToken);
    }
}
