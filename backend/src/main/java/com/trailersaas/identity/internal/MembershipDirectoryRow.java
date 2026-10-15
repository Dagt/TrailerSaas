package com.trailersaas.identity.internal;

import java.util.UUID;

/** Internal flat projection: the joined role may be absent. */
record MembershipDirectoryRow(UUID tenantId, String tenantName, UUID membershipId,
        MembershipStatus status, RoleCode roleCode, RoleScopeType scopeType, UUID customerId, String tenantStatus) {}
