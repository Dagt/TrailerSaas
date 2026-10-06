package com.trailersaas.identity.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity/bootstrap exception for /me, restricted to the authenticated global User.
 * Uses the normal runtime connection. #26 must provide a dedicated User-limited
 * RLS bootstrap strategy; this is never a global tenant browse or admin bypass.
 * Directory entries describe persisted data, not permission to use a Tenant.
 */
@Service
class MembershipDirectory {
    private final MembershipDirectoryRepository repository;

    MembershipDirectory(MembershipDirectoryRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<MembershipSummary> forUser(AuthenticatedUserPrincipal user) {
        Objects.requireNonNull(user, "Authenticated User is required");
        // One statement gives a consistent snapshot of memberships and their role sets.
        var rows = repository.forUser(user.userId());
        var memberships = new LinkedHashMap<UUID, MembershipDirectoryRow>();
        var roles = new LinkedHashMap<UUID, List<RoleSummary>>();
        for (var row : rows) {
            memberships.putIfAbsent(row.membershipId(), row);
            var roleSet = roles.computeIfAbsent(row.membershipId(), ignored -> new ArrayList<>());
            if (row.roleCode() != null) {
                roleSet.add(new RoleSummary(row.roleCode(), row.scopeType(), row.customerId()));
            }
        }
        return memberships.values().stream().map(row -> new MembershipSummary(row.tenantId(),
                row.tenantName(), row.status(), List.copyOf(roles.get(row.membershipId())))).toList();
    }

    record MembershipSummary(UUID tenantId, String tenantName, MembershipStatus status, List<RoleSummary> roles) {}
    record RoleSummary(RoleCode roleCode, RoleScopeType scopeType, UUID customerId) {}
}
