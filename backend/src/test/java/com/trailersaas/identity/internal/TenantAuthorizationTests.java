package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import static com.trailersaas.identity.internal.AuthorizationDeniedException.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantAuthorizationTests {
    final ActiveMembershipRepository repository = mock(ActiveMembershipRepository.class);
    final TenantContext context = mock(TenantContext.class);
    final DatabaseTenantAuthorization authorization = new DatabaseTenantAuthorization(repository, context);
    final UUID tenant = UUID.randomUUID();
    final UUID customer = UUID.randomUUID();
    final AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(UUID.randomUUID(), "uid", null, null);

    @BeforeEach
    void roleless() {
        when(repository.forActiveUserInTenant(principal.userId(), tenant))
                .thenReturn(List.of(new AuthorizationRoleRow(UUID.randomUUID(), null, null, null)));
    }

    @Test
    void repositoryExposesOnlyScopedLookupAndServiceIsReadOnly() {
        assertThat(ActiveMembershipRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(ActiveMembershipRepository.class.getDeclaredMethods()).hasSize(1);
        var method = ActiveMembershipRepository.class.getDeclaredMethods()[0];
        assertThat(method.getParameterTypes()).containsExactly(UUID.class, UUID.class);
        assertThat(DatabaseTenantAuthorization.class.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void missingMembershipAndInvalidIdentityDenyBeforeGrantChecks() {
        when(repository.forActiveUserInTenant(principal.userId(), tenant)).thenReturn(List.of());
        denied(() -> authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireTenantCapability(null, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        denied(() -> authorization.requireTenantCapability(principal, null, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
    }

    @Test
    void activeRolelessAndNullCapabilityDeny() {
        denied(() -> authorization.requireTenantCapability(principal, tenant, Capability.ORDER_EDIT), CAPABILITY_DENIED);
        denied(() -> authorization.requireTenantCapability(principal, tenant, null), CAPABILITY_DENIED);
    }

    @Test
    void customerGrantCannotAuthorizeTenantOrOtherCustomer() {
        grant(RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, customer);
        authorization.requireCustomerCapability(principal, tenant, customer, Capability.ORDER_EDIT);
        denied(() -> authorization.requireCustomerCapability(principal, tenant, UUID.randomUUID(), Capability.ORDER_EDIT),
                CUSTOMER_SCOPE_DENIED);
        denied(() -> authorization.requireTenantCapability(principal, tenant, Capability.ORDER_EDIT), CAPABILITY_DENIED);
        denied(() -> authorization.requireCustomerCapability(principal, tenant, customer, Capability.USER_INVITE), CAPABILITY_DENIED);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"TENANT_ADMIN", "TRANSPORT_OPERATIONS"})
    void tenantGrantAppliesToAnyCustomerButNeverMissingTarget(RoleCode role) {
        grant(role, RoleScopeType.TENANT, null);
        authorization.requireCustomerCapability(principal, tenant, customer, Capability.ORDER_EDIT);
        authorization.requireCustomerCapability(principal, tenant, UUID.randomUUID(), Capability.ORDER_EDIT);
        denied(() -> authorization.requireCustomerCapability(principal, tenant, null, Capability.ORDER_EDIT),
                CUSTOMER_SCOPE_DENIED);
    }

    @ParameterizedTest
    @EnumSource(value = Capability.class, names = {"TRIP_EXECUTE_ASSIGNED", "TRIP_VIEW_ASSIGNED"})
    void assignmentRequiredCannotBeBypassedByTenantOrCustomerApi(Capability capability) {
        grant(RoleCode.DRIVER, RoleScopeType.TENANT, null);
        authorization.requireAssignedWorkCapability(principal, tenant, capability, true);
        denied(() -> authorization.requireAssignedWorkCapability(principal, tenant, capability, false), CAPABILITY_DENIED);
        denied(() -> authorization.requireTenantCapability(principal, tenant, capability), CAPABILITY_DENIED);
        denied(() -> authorization.requireCustomerCapability(principal, tenant, customer, capability), CAPABILITY_DENIED);
    }

    @Test
    void assignmentApiCannotAuthorizeUnrelatedCapabilitiesOrMissingGrants() {
        grant(RoleCode.TENANT_ADMIN, RoleScopeType.TENANT, null);
        denied(() -> authorization.requireAssignedWorkCapability(principal, tenant, Capability.USER_INVITE, true), CAPABILITY_DENIED);
        denied(() -> authorization.requireAssignedWorkCapability(principal, tenant, Capability.TRIP_EXECUTE_ASSIGNED, true),
                CAPABILITY_DENIED);
    }

    @Test
    void everyCheckReadsAgainForSamePrincipalAndTenant() {
        grant(RoleCode.TENANT_ADMIN, RoleScopeType.TENANT, null);
        authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE);
        roleless();
        denied(() -> authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE), CAPABILITY_DENIED);
        when(repository.forActiveUserInTenant(principal.userId(), tenant)).thenReturn(List.of());
        denied(() -> authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE), TENANT_ACCESS_DENIED);
        verify(repository, times(3)).forActiveUserInTenant(principal.userId(), tenant);
    }

    @Test
    void establishesTenantOnEveryCheckBeforeReadingRoles() {
        grant(RoleCode.TENANT_ADMIN, RoleScopeType.TENANT, null);
        authorization.requireTenantCapability(principal, tenant, Capability.USER_INVITE);
        var ordered = inOrder(context, repository);
        ordered.verify(context).establishTenant(tenant);
        ordered.verify(repository).forActiveUserInTenant(principal.userId(), tenant);
    }

    private void grant(RoleCode role, RoleScopeType scope, UUID target) {
        when(repository.forActiveUserInTenant(principal.userId(), tenant))
                .thenReturn(List.of(new AuthorizationRoleRow(UUID.randomUUID(), role, scope, target)));
    }

    static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            AuthorizationDeniedException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AuthorizationDeniedException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }
}
