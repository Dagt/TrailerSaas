package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ActiveMembershipRepository extends Repository<TenantMembershipEntity, UUID> {
    // The left join distinguishes an ACTIVE roleless membership from no access.
    @Query("""
            select new com.trailersaas.identity.internal.AuthorizationRoleRow(
                m.id, r.roleCode, r.scopeType, r.customerId)
            from TenantMembershipEntity m
            left join RoleAssignmentEntity r on r.tenantId = m.tenantId and r.membershipId = m.id
            where m.userId = :userId and m.tenantId = :tenantId
                and m.status = com.trailersaas.identity.internal.MembershipStatus.ACTIVE
            """)
    List<AuthorizationRoleRow> forActiveUserInTenant(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
