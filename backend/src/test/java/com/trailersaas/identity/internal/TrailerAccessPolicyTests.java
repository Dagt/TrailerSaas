package com.trailersaas.identity.internal;
import java.util.*;
import com.trailersaas.identity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrailerAccessPolicyTests {
    final ActiveMembershipRepository repository=mock(ActiveMembershipRepository.class);
    final TenantContext context=mock(TenantContext.class);
    final DatabaseTrailerAccessPolicy policy=new DatabaseTrailerAccessPolicy(repository,context);
    final UUID tenant=UUID.randomUUID(),user=UUID.randomUUID(),customer=UUID.randomUUID();
    final AuthenticatedUserPrincipal principal=new AuthenticatedUserPrincipal(user,"uid",null,null);
    void roles(RoleCode role) {
        when(repository.forActiveUserInTenant(user,tenant)).thenReturn(List.of(new AuthorizationRoleRow(UUID.randomUUID(),role,
                role==RoleCode.CUSTOMER_DISPATCHER?RoleScopeType.CUSTOMER:RoleScopeType.TENANT,role==RoleCode.CUSTOMER_DISPATCHER?customer:null)));
    }
    @ParameterizedTest @EnumSource(RoleCode.class)
    void readAndScanGrantsAreDistinct(RoleCode role) {
        roles(role);
        if(role==RoleCode.TENANT_ADMIN || role==RoleCode.TRANSPORT_OPERATIONS) {
            assertThat(policy.readScope(principal,tenant).tenantWide()).isTrue();
        } else if(role==RoleCode.CUSTOMER_DISPATCHER) {
            assertThat(policy.readScope(principal,tenant)).isEqualTo(new CustomerAccessScope(false,Set.of(customer)));
            assertThat(policy.mayResolveQr(principal,tenant,null)).isFalse();
            assertThat(policy.mayResolveQr(principal,tenant,UUID.randomUUID())).isFalse();
        } else { assertThatThrownBy(()->policy.readScope(principal,tenant)).isInstanceOf(AuthorizationDeniedException.class); }
        assertThat(policy.mayResolveQr(principal,tenant,customer)).isTrue();
        verify(context,atLeastOnce()).establishTenant(tenant);
    }
    @Test void missingActiveMembershipDeniesScanAndDoesNotConvertInfrastructureFailureToDenial() {
        when(repository.forActiveUserInTenant(user,tenant)).thenReturn(List.of());
        assertThat(policy.mayResolveQr(principal,tenant,customer)).isFalse();
        when(repository.forActiveUserInTenant(user,tenant)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));
        assertThatThrownBy(()->policy.mayResolveQr(principal,tenant,customer)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
}
