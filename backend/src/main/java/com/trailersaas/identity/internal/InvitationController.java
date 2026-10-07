package com.trailersaas.identity.internal;

import java.util.UUID;
import java.util.Set;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
class InvitationController {
    private final InvitationService invitations;
    InvitationController(InvitationService invitations) { this.invitations = invitations; }

    @PostMapping("/api/v1/tenants/{tenantId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationContracts.Created create(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @RequestBody InvitationContracts.CreateRequest request) {
        return invitations.create(user, tenantId, request);
    }
    @GetMapping("/api/v1/tenants/{tenantId}/invitations")
    InvitationContracts.Invitations list(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @RequestParam MultiValueMap<String, String> parameters) {
        if (!Set.of("status").containsAll(parameters.keySet())
                || (parameters.containsKey("status") && parameters.get("status").size() != 1)) {
            throw InvitationFailure.invalid();
        }
        InvitationStatus status = null;
        if (parameters.containsKey("status")) {
            try { status = InvitationStatus.valueOf(parameters.getFirst("status")); }
            catch (IllegalArgumentException ignored) { throw InvitationFailure.invalid(); }
        }
        return invitations.list(user, tenantId, status);
    }
    @PostMapping("/api/v1/tenants/{tenantId}/invitations/{invitationId}/revoke")
    InvitationContracts.Summary revoke(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID tenantId, @PathVariable UUID invitationId) {
        return invitations.revoke(user, tenantId, invitationId);
    }
    @PostMapping("/api/v1/invitations/accept")
    InvitationContracts.Accepted accept(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestBody InvitationContracts.AcceptRequest request, @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) { throw InvitationFailure.invalid(); }
        return invitations.accept(user, request);
    }
}
