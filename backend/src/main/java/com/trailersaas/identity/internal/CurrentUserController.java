package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CurrentUserController {
    @GetMapping("/api/v1/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        return new CurrentUserResponse(user.userId(), user.email(), user.displayName(), List.of());
    }

    record CurrentUserResponse(UUID userId, String email, String displayName, List<Object> memberships) {}
}
