package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.trailersaas.customers.TripDestinationPolicy;
import com.trailersaas.drivers.DriverPlanningPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import com.trailersaas.tractors.TractorPlanningPolicy;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripServiceTests {
  @ParameterizedTest
  @ValueSource(strings = {"driver", "tractor", "trailer"})
  void optionalInitialIdsRequireTheirOwnCapabilityAndCannotInheritItFromTripPlan(String kind) {
    var auth = mock(TenantAuthorization.class);
    var orders = mock(OrderPlanningPolicy.class);
    var snapshots = mock(OrderDestinationSnapshots.class);
    var destinations = mock(TripDestinationPolicy.class);
    var drivers = mock(DriverPlanningPolicy.class);
    var tractors = mock(TractorPlanningPolicy.class);
    var trailers = mock(TrailerPlanningPolicy.class);
    var repository = mock(TripRepository.class);
    var actor = new AuthenticatedUserPrincipal(UUID.randomUUID(), "fixture", null, null);
    var tenant = UUID.randomUUID();
    var order = UUID.randomUUID();
    var equipment = UUID.randomUUID();
    var capability =
        switch (kind) {
          case "driver" -> Capability.TRIP_ASSIGN_DRIVER;
          case "tractor" -> Capability.TRIP_ASSIGN_TRACTOR;
          default -> Capability.TRIP_ASSIGN_TRAILER;
        };
    var denied = new IllegalStateException("test-only capability rejection");
    doThrow(denied).when(auth).requireTenantCapability(actor, tenant, capability);
    when(snapshots.freeze(any())).thenReturn(Map.of("name", "Frozen"));
    var input =
        new HashMap<String, Object>(
            Map.of(
                "orderIds",
                List.of(order.toString()),
                "stops",
                List.of(
                    Map.of(
                        "sequenceNumber",
                        1,
                        "stopType",
                        "DELIVERY",
                        "orderIds",
                        List.of(order.toString()),
                        "siteSnapshot",
                        Map.of("name", "Frozen")))));
    input.put(
        kind.equals("trailer") ? "plannedInitialTrailerId" : kind + "Id", equipment.toString());
    var service =
        new TripService(
            auth, orders, snapshots, destinations, drivers, tractors, trailers, repository);
    assertThatThrownBy(() -> service.create(actor, tenant, input)).isSameAs(denied);
    var sequence = inOrder(auth);
    sequence.verify(auth).requireTenantCapability(actor, tenant, Capability.TRIP_PLAN);
    sequence.verify(auth).requireTenantCapability(actor, tenant, capability);
    verifyNoInteractions(orders, drivers, tractors, trailers, repository);
  }
}
