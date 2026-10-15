package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Only the identity bootstrap lookup; no generic or unscoped tenant-owned reads. */
interface MembershipDirectoryRepository extends Repository<TenantMembershipEntity, UUID> {
    @Query("""
            select new com.trailersaas.identity.internal.MembershipDirectoryRow(
                t.id, t.name, m.id, m.status, r.roleCode, r.scopeType, r.customerId, t.status)
            from TenantMembershipEntity m
            join TenantEntity t on t.id = m.tenantId
            left join RoleAssignmentEntity r on r.tenantId = m.tenantId and r.membershipId = m.id
            where m.userId = :userId
            order by t.name, t.id, r.roleCode, r.scopeType, r.customerId
            """)
    List<MembershipDirectoryRow> forUser(@Param("userId") UUID userId);
}
