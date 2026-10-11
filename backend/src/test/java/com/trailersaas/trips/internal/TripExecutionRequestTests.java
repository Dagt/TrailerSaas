package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TripExecutionRequestTests {
  @Test
  void pendingInspectionPolicyNeverFabricatesEligibility() {
    assertThat(
            new PendingDeparturePrerequisites()
                .satisfied(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        .isFalse();
  }

  @Test
  void nullOccurrenceCannotAdvanceAnything() {
    for (var a : TripExecutionRequests.Action.values())
      assertThatThrownBy(() -> TripExecutionRequests.parse(a, Map.of()))
          .isInstanceOf(TripFailure.class);
  }

  @Test
  void departureRequiresAnExplicitNextStop() {
    for (var action :
        List.of(
            TripExecutionRequests.Action.FIRST_DEPARTURE, TripExecutionRequests.Action.DEPARTURE))
      assertThatThrownBy(
              () ->
                  TripExecutionRequests.parse(action, Map.of("occurredAt", "2026-10-10T10:00:00Z")))
          .isInstanceOf(TripFailure.class);
  }

  @Test
  void coordinateOrderAndEquivalentNumericEncodingHaveOneLogicalPayload() {
    var a = new LinkedHashMap<String, Object>();
    a.put("latitude", 1);
    a.put("longitude", 2);
    a.put("accuracyM", 3);
    var b = new LinkedHashMap<String, Object>();
    b.put("accuracyM", 3.0);
    b.put("longitude", 2.0);
    b.put("latitude", 1.0);
    var x =
        TripExecutionRequests.parse(
            TripExecutionRequests.Action.ARRIVAL,
            Map.of("occurredAt", "2026-10-10T10:00:00Z", "source", "MANUAL", "coordinates", a));
    var y =
        TripExecutionRequests.parse(
            TripExecutionRequests.Action.ARRIVAL,
            Map.of("occurredAt", "2026-10-10T10:00:00Z", "source", "MANUAL", "coordinates", b));
    assertThat(x).isEqualTo(y);
    assertThat(x.coordinates().keySet()).containsExactly("latitude", "longitude", "accuracyM");
  }
}
