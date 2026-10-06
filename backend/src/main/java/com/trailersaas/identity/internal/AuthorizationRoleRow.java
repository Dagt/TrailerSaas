package com.trailersaas.identity.internal;

import java.util.UUID;

record AuthorizationRoleRow(UUID membershipId, RoleCode roleCode, RoleScopeType scopeType, UUID customerId) {}
