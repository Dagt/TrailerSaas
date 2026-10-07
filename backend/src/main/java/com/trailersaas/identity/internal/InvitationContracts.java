package com.trailersaas.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;

final class InvitationContracts {
    private InvitationContracts() {}

    static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    record Role(RoleCode roleCode, RoleScopeType scopeType, UUID customerId) {
        static Role parse(Object value) {
            if (!(value instanceof Map<?, ?> map)) { throw InvitationFailure.invalid(); }
            only(map, Set.of("roleCode", "scopeType", "customerId"));
            try {
                var role = RoleCode.valueOf(string(map.get("roleCode")));
                var scope = RoleScopeType.valueOf(string(map.get("scopeType")));
                var customer = map.get("customerId") == null ? null : UUID.fromString(string(map.get("customerId")));
                if (customer != null && !customer.toString().equalsIgnoreCase(string(map.get("customerId")))) {
                    throw InvitationFailure.invalid();
                }
                if ((role == RoleCode.CUSTOMER_DISPATCHER) != (scope == RoleScopeType.CUSTOMER)
                        || (scope == RoleScopeType.CUSTOMER) != (customer != null)) { throw InvitationFailure.invalid(); }
                return new Role(role, scope, customer);
            } catch (IllegalArgumentException ignored) { throw InvitationFailure.invalid(); }
        }
    }

    record CreateRequest(String email, String displayName, List<Role> roles) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static CreateRequest parse(Map<String, Object> map) {
            if (map == null) { throw InvitationFailure.invalid(); }
            only(map, Set.of("email", "displayName", "roles"));
            String email = string(map.get("email"));
            String normalized = normalizeEmail(email);
            // Structural email validation only; never provider-specific alias rules.
            if (!normalized.matches("[^\\s@]+@[^\\s@]+") || normalized.chars().anyMatch(Character::isISOControl)) {
                throw InvitationFailure.invalid();
            }
            String name = map.get("displayName") == null ? null : string(map.get("displayName"));
            if (!(map.get("roles") instanceof List<?> inputs) || inputs.isEmpty()) { throw InvitationFailure.invalid(); }
            var roles = inputs.stream().map(Role::parse).toList();
            if (roles.stream().distinct().count() != roles.size()) { throw InvitationFailure.invalid(); }
            return new CreateRequest(email, name, roles);
        }
    }

    record AcceptRequest(String token) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static AcceptRequest parse(Map<String, Object> map) {
            if (map == null || !map.keySet().equals(Set.of("token"))) { throw InvitationFailure.invalid(); }
            return new AcceptRequest(string(map.get("token")));
        }
        @Override public String toString() { return "AcceptRequest[token=redacted]"; }
    }
    record Summary(UUID id, String email, String displayName, InvitationStatus status, Instant expiresAt, List<Role> roles) {}
    record Created(UUID id, String email, String displayName, InvitationStatus status, Instant expiresAt,
            List<Role> roles, String acceptanceToken) {
        @Override public String toString() { return "Created[id=" + id + ", acceptanceToken=redacted]"; }
    }
    record Invitations(List<Summary> items) {}
    record Accepted(UUID invitationId, UUID tenantId, UUID membershipId, boolean accepted) {}

    private static String string(Object value) {
        if (!(value instanceof String text)) { throw InvitationFailure.invalid(); }
        return text;
    }
    private static void only(Map<?, ?> map, Set<String> allowed) {
        if (!allowed.containsAll(map.keySet())) { throw InvitationFailure.invalid(); }
    }
}
