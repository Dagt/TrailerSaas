package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestServiceTests {
    final TenantAuthorization auth = mock(TenantAuthorization.class);
    final TrailerPlanningPolicy trailers = mock(TrailerPlanningPolicy.class);
    final ManifestRepository repo = mock(ManifestRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final ManifestService service = new ManifestService(auth, trailers, repo, orders);
    final UUID tenant = UUID.randomUUID(), manifestId = UUID.randomUUID(), orderId = UUID.randomUUID();
    final AuthenticatedUserPrincipal actor = new AuthenticatedUserPrincipal(UUID.randomUUID(), "fixture", null, null);
    LoadManifestEntity manifest(String status) {
        var m = new LoadManifestEntity(); m.id = manifestId; m.tenantId = tenant; m.status = ManifestStatus.valueOf(status); m.updatedAt = m.createdAt = Instant.now();
        when(repo.lock(tenant, manifestId)).thenReturn(m); return m;
    }
    @Test void quantitiesAcceptNullZeroAndIndependentDecimalsButRejectNegatives() {
        ManifestService.amounts(null, null, null); ManifestService.amounts(BigDecimal.ZERO, 0, BigDecimal.ZERO);
        ManifestService.amounts(new BigDecimal("0.25"), null, new BigDecimal("1.75"));
        assertThatThrownBy(() -> ManifestService.amounts(new BigDecimal("-0.01"), null, null)).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> ManifestService.amounts(null, -1, null)).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> ManifestService.amounts(null, null, new BigDecimal("-1"))).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"allocate", "remove", "close"})
    void closedAndStaleRejectEveryMutationWithoutChildWrites(String command) {
        var m = manifest("CLOSED");
        assertThatThrownBy(() -> invoke(command, 0)).isInstanceOf(OrderFailure.class).extracting(e -> ((OrderFailure)e).code).isEqualTo("RESOURCE_CONFLICT");
        m.status = ManifestStatus.OPEN; m.version = 1;
        assertThatThrownBy(() -> invoke(command, 0)).isInstanceOf(OrderFailure.class).extracting(e -> ((OrderFailure)e).code).isEqualTo("STALE_VERSION");
        verify(repo, never()).insert(any()); verify(repo, never()).remove(any()); verify(repo, never()).flush();
    }
    void invoke(String command, long version) {
        switch (command) {
            case "allocate" -> service.allocate(actor, tenant, manifestId, version, orderId, null, null, null);
            case "remove" -> service.remove(actor, tenant, manifestId, version, orderId);
            default -> service.close(actor, tenant, manifestId, version);
        }
    }
    @ParameterizedTest @EnumSource(OrderStatus.class)
    void allocationRequiresOpenOrder(OrderStatus status) {
        manifest("OPEN"); var scope = new CustomerAccessScope(true, Set.of());
        when(auth.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_TRAILER)).thenReturn(scope);
        var order = new OrderEntity(); order.id = orderId; order.status = status;
        when(orders.resource(tenant, orderId, scope)).thenReturn(order);
        if (status == OrderStatus.OPEN) { service.allocate(actor, tenant, manifestId, 0, orderId, null, null, null); verify(repo).insert(any(ManifestOrderEntity.class)); }
        else { assertThatThrownBy(() -> service.allocate(actor, tenant, manifestId, 0, orderId, null, null, null)).isInstanceOf(OrderFailure.class); verify(repo, never()).insert(any()); }
    }
    @Test void closeRequiresTenantWideCapabilityAndKeepsAllocations() {
        var m = manifest("OPEN"); var result = service.close(actor, tenant, manifestId, 0);
        verify(auth).requireTenantCapability(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
        assertThat(result.status()).isEqualTo(ManifestStatus.CLOSED); assertThat(m.closedAt).isEqualTo(m.updatedAt).isAfter(m.createdAt);
        verify(repo, never()).remove(any());
    }
    @ParameterizedTest @EnumSource(TrailerPlanningPolicy.Eligibility.class)
    void trailerBoundaryMapsSafeResults(TrailerPlanningPolicy.Eligibility eligibility) {
        var trailer = UUID.randomUUID(); when(trailers.check(actor, tenant, trailer)).thenReturn(eligibility);
        if (eligibility == TrailerPlanningPolicy.Eligibility.ELIGIBLE) { service.validateTrailer(actor, tenant, trailer); }
        else { assertThatThrownBy(() -> service.validateTrailer(actor, tenant, trailer)).isInstanceOf(OrderFailure.class); }
    }
    @Test void repositoryApiRemainsInternalAndScopedWithNoManifestDirectory() {
        assertThat(java.lang.reflect.Modifier.isPublic(ManifestRepository.class.getModifiers())).isFalse();
        assertThat(Arrays.stream(ManifestRepository.class.getDeclaredMethods()).map(m -> m.getName())).doesNotContain("findAll", "findById", "list");
        for (var m : ManifestRepository.class.getDeclaredMethods()) { if (Set.of("lock", "allocation").contains(m.getName())) { assertThat(m.getParameterTypes()[0]).isEqualTo(UUID.class); } }
    }
    @Test void optimisticPersistenceRaceIsStaleAndInfrastructureErrorsAreNotConflicts() {
        manifest("OPEN"); doThrow(new jakarta.persistence.OptimisticLockException("private")).when(repo).flush();
        assertThatThrownBy(() -> service.close(actor, tenant, manifestId, 0)).isInstanceOf(OrderFailure.class)
                .extracting(e -> ((OrderFailure)e).code).isEqualTo("STALE_VERSION");
        manifest("OPEN"); var failure = new org.springframework.dao.DataAccessResourceFailureException("private");
        doThrow(failure).when(repo).flush();
        assertThatThrownBy(() -> service.close(actor, tenant, manifestId, 0)).isSameAs(failure);
    }
}
