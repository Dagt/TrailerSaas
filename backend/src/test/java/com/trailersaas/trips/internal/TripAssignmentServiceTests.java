package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.trailersaas.drivers.DriverPlanningPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.tractors.TractorPlanningPolicy;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripAssignmentServiceTests {
  final TenantAuthorization auth = mock(TenantAuthorization.class);
  final TripRepository repository = mock(TripRepository.class);
  final TripService reads = mock(TripService.class);
  final DriverPlanningPolicy drivers = mock(DriverPlanningPolicy.class);
  final TractorPlanningPolicy tractors = mock(TractorPlanningPolicy.class);
  final TrailerPlanningPolicy trailers = mock(TrailerPlanningPolicy.class);
  final TripAssignmentService service =
      new TripAssignmentService(
          auth, repository, reads, drivers, tractors, trailers, mock(TripDriverUpdateSink.class));
  final UUID tenant = UUID.randomUUID(),
      tripId = UUID.randomUUID(),
      customer = UUID.randomUUID(),
      foreign = UUID.randomUUID(),
      resource = UUID.randomUUID();
  final AuthenticatedUserPrincipal actor =
      new AuthenticatedUserPrincipal(UUID.randomUUID(), "verified", null, null);

  Map<String, Object> body() {
    return Map.of("expectedVersion", 0, "driverId", resource.toString(), "reason", "Dispatch");
  }

  TripEntity trip() {
    var t = new TripEntity();
    t.id = tripId;
    t.tenantId = tenant;
    t.status = "PLANNED";
    return t;
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "mixed"})
  void scopedTripMustHaveNonemptyMappingsWhoseEveryCustomerIsAuthorized(String mode) {
    when(auth.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_DRIVER))
        .thenReturn(new CustomerAccessScope(false, Set.of(customer)));
    when(repository.resource(tenant, tripId, true)).thenReturn(trip());
    when(repository.mappedCustomers(tenant, tripId))
        .thenReturn(mode.equals("empty") ? Set.of() : Set.of(customer, foreign));
    assertThatThrownBy(
            () -> service.assign(actor, tenant, tripId, TripAssignmentService.Kind.DRIVER, body()))
        .isInstanceOfSatisfying(
            TripFailure.class, e -> assertThat(e.code).isEqualTo("RESOURCE_NOT_FOUND"));
    verifyNoInteractions(drivers, tractors, trailers, reads);
    verify(repository, never()).insert(any());
    verify(repository, never()).flush();
  }

  @Test
  void sameResourceNoOpNeverChecksTargetOrAppendsHistory() {
    var t = trip();
    t.currentDriverId = resource;
    when(auth.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_DRIVER))
        .thenReturn(new CustomerAccessScope(true, Set.of()));
    when(repository.resource(tenant, tripId, true)).thenReturn(t);
    service.assign(actor, tenant, tripId, TripAssignmentService.Kind.DRIVER, body());
    verify(reads).detail(t);
    verifyNoInteractions(drivers, tractors, trailers);
    verify(repository, never()).insert(any());
    verify(repository, never()).flush();
    assertThat(t.version).isZero();
    assertThat(t.updatedAt).isNull();
  }
}
