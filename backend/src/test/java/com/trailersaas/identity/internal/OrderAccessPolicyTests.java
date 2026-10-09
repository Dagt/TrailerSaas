package com.trailersaas.identity.internal;

import java.util.*;
import com.trailersaas.identity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderAccessPolicyTests {
    final ActiveMembershipRepository repository = mock(ActiveMembershipRepository.class);
    final TenantContext context = mock(TenantContext.class);
    final DatabaseOrderAccessPolicy policy = new DatabaseOrderAccessPolicy(repository, context);
    final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), customer = UUID.randomUUID();
    final AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(user, "uid", null, null);
    @ParameterizedTest @EnumSource(RoleCode.class)
    void freshReadScopeUsesOnlyApprovedViewCapabilities(RoleCode role) {
        when(repository.forActiveUserInTenant(user, tenant)).thenReturn(List.of(new AuthorizationRoleRow(UUID.randomUUID(), role,
                role == RoleCode.CUSTOMER_DISPATCHER ? RoleScopeType.CUSTOMER : RoleScopeType.TENANT,
                role == RoleCode.CUSTOMER_DISPATCHER ? customer : null)));
        if (role == RoleCode.TENANT_ADMIN || role == RoleCode.TRANSPORT_OPERATIONS) { assertThat(policy.readScope(principal, tenant).tenantWide()).isTrue(); }
        else if (role == RoleCode.CUSTOMER_DISPATCHER) { assertThat(policy.readScope(principal, tenant)).isEqualTo(new CustomerAccessScope(false, Set.of(customer))); }
        else { assertThatThrownBy(() -> policy.readScope(principal, tenant)).isInstanceOf(AuthorizationDeniedException.class); }
        verify(context).establishTenant(tenant);
    }
    @Test void multipleCustomersSupportedAndRolesAreNeverCached() {
        var second = UUID.randomUUID();
        when(repository.forActiveUserInTenant(user, tenant)).thenReturn(List.of(
                new AuthorizationRoleRow(UUID.randomUUID(), RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, customer),
                new AuthorizationRoleRow(UUID.randomUUID(), RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, second)));
        assertThat(policy.readScope(principal, tenant).customerIds()).containsExactlyInAnyOrder(customer, second);
        when(repository.forActiveUserInTenant(user, tenant)).thenReturn(List.of());
        assertThatThrownBy(() -> policy.readScope(principal, tenant)).isInstanceOf(AuthorizationDeniedException.class);
        verify(repository, times(2)).forActiveUserInTenant(user, tenant);
    }
}
