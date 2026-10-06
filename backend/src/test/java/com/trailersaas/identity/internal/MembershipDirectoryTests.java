package com.trailersaas.identity.internal;

import java.util.List;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipDirectoryTests {
    final MembershipDirectoryRepository repository = mock(MembershipDirectoryRepository.class);
    final TenantContext context = mock(TenantContext.class);
    final MembershipDirectory directory = new MembershipDirectory(repository, context);
    final UUID userId = UUID.randomUUID();
    final AuthenticatedUserPrincipal user = new AuthenticatedUserPrincipal(userId, "uid", null, null);

    @Test
    void bootstrapRequiresAuthenticatedPrincipalAndPassesOnlyItsInternalUserId() {
        when(repository.forUser(userId)).thenReturn(List.of());
        assertThat(directory.forUser(user)).isEmpty();
        var ordered = inOrder(context, repository);
        ordered.verify(context).establishBootstrapUser(user);
        ordered.verify(repository).forUser(userId);
        verifyNoMoreInteractions(repository);
        assertThatNullPointerException().isThrownBy(() -> directory.forUser(null));
    }

    @Test
    void includesRolelessAndDisabledMembershipsWithoutGrantingAccess() {
        var tenant = UUID.randomUUID();
        var membership = UUID.randomUUID();
        var customer = UUID.randomUUID();
        when(repository.forUser(userId)).thenReturn(List.of(
                new MembershipDirectoryRow(tenant, "Tenant", membership, MembershipStatus.DISABLED,
                        RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, customer),
                new MembershipDirectoryRow(tenant, "Tenant", membership, MembershipStatus.DISABLED,
                        RoleCode.DRIVER, RoleScopeType.TENANT, null),
                new MembershipDirectoryRow(UUID.randomUUID(), "Roleless", UUID.randomUUID(),
                        MembershipStatus.SUSPENDED, null, null, null)));
        var result = directory.forUser(user);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().status()).isEqualTo(MembershipStatus.DISABLED);
        assertThat(result.getFirst().roles()).containsExactly(
                new MembershipDirectory.RoleSummary(RoleCode.CUSTOMER_DISPATCHER, RoleScopeType.CUSTOMER, customer),
                new MembershipDirectory.RoleSummary(RoleCode.DRIVER, RoleScopeType.TENANT, null));
        assertThat(result.getLast().roles()).isEmpty();
        assertThatThrownBy(() -> result.getFirst().roles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void directoryReadsAgainInsteadOfCachingMembershipsInPrincipal() {
        when(repository.forUser(userId)).thenReturn(List.of(), List.of(
                new MembershipDirectoryRow(UUID.randomUUID(), "Added", UUID.randomUUID(),
                        MembershipStatus.ACTIVE, null, null, null)));
        assertThat(directory.forUser(user)).isEmpty();
        assertThat(directory.forUser(user)).hasSize(1);
        verify(repository, times(2)).forUser(userId);
    }

    @Test
    void repositoryExposesOnlyUserLimitedBootstrapAndServiceUsesReadOnlyTransaction() throws Exception {
        assertThat(MembershipDirectoryRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(MembershipDirectoryRepository.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .containsExactly("forUser");
        assertThat(MembershipDirectoryRepository.class.getMethod("forUser", UUID.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value())
                .contains("where m.userId = :userId", "r.tenantId = m.tenantId", "r.membershipId = m.id");
        assertThat(MembershipDirectory.class.getMethod("forUser", AuthenticatedUserPrincipal.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
