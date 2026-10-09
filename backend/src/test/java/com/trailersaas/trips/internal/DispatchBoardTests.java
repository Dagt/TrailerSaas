package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.trailersaas.identity.*;
import com.trailersaas.orders.OrderDispatchDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.*;
import org.junit.jupiter.api.Test;

class DispatchBoardTests {
  @Test
  void checksBothFreshTenantCapabilitiesBeforeAnyProjectionRead() {
    var auth = mock(TenantAuthorization.class);
    var orders = mock(OrderDispatchDirectory.class);
    var em = mock(EntityManager.class);
    var query = mock(Query.class);
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.setMaxResults(20)).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());
    var actor = new AuthenticatedUserPrincipal(UUID.randomUUID(), "verified-uid", null, null);
    var tenant = UUID.randomUUID();
    when(orders.pending(actor, tenant, null)).thenReturn(List.of());
    var result = new DispatchBoardService(auth, orders, em).get(actor, tenant, Map.of());
    var sequence = inOrder(auth, em, orders);
    sequence.verify(auth).requireTenantCapability(actor, tenant, Capability.TRIP_VIEW_ALL);
    sequence.verify(auth).requireTenantCapability(actor, tenant, Capability.ORDER_VIEW_ALL);
    sequence.verify(em).createNativeQuery(anyString());
    sequence.verify(orders).pending(actor, tenant, null);
    assertThat(result.generatedAt()).isNotNull();
    assertThat(result.pendingOrders()).isEmpty();
    assertThat(result.activeTrips()).isEmpty();
    assertThat(result.attentionItems()).isEmpty();
    verify(query).setMaxResults(20);
  }

  @Test
  void unknownFiltersCannotReachPersistenceAndProjectionContainsNoPrivateIdentityOrHistory() {
    var auth = mock(TenantAuthorization.class);
    var orders = mock(OrderDispatchDirectory.class);
    var em = mock(EntityManager.class);
    assertThatThrownBy(
            () ->
                new DispatchBoardService(auth, orders, em)
                    .get(
                        new AuthenticatedUserPrincipal(UUID.randomUUID(), "uid", null, null),
                        UUID.randomUUID(),
                        Map.of("date", "2026-10-09")))
        .isInstanceOf(TripFailure.class);
    verifyNoInteractions(orders, em);
    for (var projection :
        List.of(
            DispatchBoardService.Board.class,
            DispatchBoardService.ActiveTrip.class,
            OrderDispatchDirectory.Pending.class)) {
      assertThat(Arrays.stream(projection.getRecordComponents()).map(c -> c.getName()))
          .doesNotContain(
              "tenantId",
              "assignmentHistory",
              "operationalHistory",
              "createdByUserId",
              "actorUserId");
    }
  }
}
