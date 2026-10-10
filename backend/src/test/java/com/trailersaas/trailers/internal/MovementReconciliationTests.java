package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import static com.trailersaas.trailers.TrailerProjectionReconciliation.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovementReconciliationTests {
  final UUID location = UUID.randomUUID(), driver = UUID.randomUUID();
  final Instant at = Instant.parse("2026-10-01T12:00:00Z");

  MovementRules.State actual() {
    return new MovementRules.State(
        LocationState.AT_LOCATION, location, location, at, driver, LoadState.LOADED);
  }

  TrailerMovementEntity event(String type, int order) {
    var e = new TrailerMovementEntity();
    e.id = new UUID(0, order);
    e.eventType = type;
    e.locationId = location;
    e.driverId = driver;
    e.occurredAt = at;
    e.recordedAt = at.plusSeconds(order);
    return e;
  }

  @Test
  void missingBaselineAndPartialHistoryRemainExplicitlyInsufficient() {
    assertThat(MovementReconciliation.compare(List.of(), actual()).status())
        .isEqualTo(Status.INSUFFICIENT_HISTORY);
    var comparison = MovementReconciliation.compare(List.of(event("DROPPED", 1)), actual());
    assertThat(comparison.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
    assertThat(comparison.derivedFields()).doesNotContain(Field.LOAD_STATE);
  }

  @Test
  void knownMismatchIsDriftEvenWhenOtherBaselineFieldsAreUnknown() {
    var e = event("DROPPED", 1);
    e.locationId = UUID.randomUUID();
    assertThat(MovementReconciliation.compare(List.of(e), actual()).driftFields())
        .containsExactlyInAnyOrder(Field.CURRENT_LOCATION, Field.LAST_KNOWN_LOCATION);
  }

  @Test
  void serverChronologyAndIdTieBreakNeverOccurrenceTimeChooseCurrentState() {
    var arrival = event("ARRIVED", 1);
    arrival.occurredAt = at.plusSeconds(100);
    var yard = event("YARD_MOVED", 2);
    yard.recordedAt = arrival.recordedAt;
    yard.occurredAt = at;
    var load = event("LOAD_STATE_CHANGED", 3);
    load.metadata = Map.of("newLoadState", "LOADED");
    assertThat(MovementReconciliation.compare(List.of(load, yard, arrival), actual()).status())
        .isEqualTo(Status.MATCH);
  }

  @Test
  void preservationMetadataCanDeriveNullWithoutInventingAnObservation() {
    var e = event("LOCATION_MARKED_UNKNOWN", 1);
    e.metadata = new LinkedHashMap<>();
    e.metadata.put("preservedLastKnownLocationId", null);
    e.metadata.put("preservedLastSeenAt", null);
    e.metadata.put("preservedLastDriverId", null);
    var load = event("LOAD_STATE_CHANGED", 2);
    load.metadata = Map.of("newLoadState", "UNKNOWN");
    var a =
        new MovementRules.State(
            LocationState.LOCATION_UNKNOWN, null, null, null, null, LoadState.UNKNOWN);
    assertThat(MovementReconciliation.compare(List.of(e, load), a).status())
        .isEqualTo(Status.MATCH);
  }

  @Test
  void unrecognizedLaterFactsCannotPretendTheEarlierProjectionIsComplete() {
    var e = event("DROPPED", 1);
    var load = event("LOAD_STATE_CHANGED", 2);
    load.metadata = Map.of("newLoadState", "LOADED");
    assertThat(
            MovementReconciliation.compare(List.of(e, load, event("FUTURE_FACT", 3)), actual())
                .status())
        .isEqualTo(Status.INSUFFICIENT_HISTORY);
  }

  List<TrailerMovementEntity> completeHistory() {
    var load = event("LOAD_STATE_CHANGED", 2);
    load.metadata = Map.of("newLoadState", "LOADED");
    return new ArrayList<>(List.of(event("ARRIVED", 1), load));
  }

  TrailerMovementEntity nonOwnedEvent(String type, int order) {
    var e = event(type, order);
    e.locationId = UUID.randomUUID();
    e.driverId = UUID.randomUUID();
    e.occurredAt = at.plusSeconds(100);
    return e;
  }

  void assertCompleteMatch(List<TrailerMovementEntity> history) {
    var comparison = MovementReconciliation.compare(history, actual());
    assertThat(comparison.status()).isEqualTo(Status.MATCH);
    assertThat(comparison.derivedFields()).containsExactlyInAnyOrder(Field.values());
    assertThat(comparison.driftFields()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MARKED_OOS", "RETURNED_TO_SERVICE", "DESIGNATED_CUSTOMER_CHANGED"})
  void knownNonOwnedFactsPreserveCompleteMovementAndLoadDerivation(String type) {
    var history = completeHistory();
    assertCompleteMatch(history);
    history.add(nonOwnedEvent(type, 3));
    assertCompleteMatch(history);
  }

  @Test
  void mixedKnownNonOwnedFactsPreserveCompleteMovementAndLoadDerivation() {
    var history = completeHistory();
    history.add(nonOwnedEvent("MARKED_OOS", 3));
    history.add(nonOwnedEvent("RETURNED_TO_SERVICE", 4));
    history.add(nonOwnedEvent("DESIGNATED_CUSTOMER_CHANGED", 5));
    assertCompleteMatch(history);
  }

  @Test
  void trulyUnknownFactStillInvalidatesCoverageAfterKnownNonOwnedFacts() {
    var history = completeHistory();
    history.add(nonOwnedEvent("MARKED_OOS", 3));
    history.add(nonOwnedEvent("RETURNED_TO_SERVICE", 4));
    history.add(nonOwnedEvent("DESIGNATED_CUSTOMER_CHANGED", 5));
    assertCompleteMatch(history);
    history.add(event("FUTURE_FACT", 6));
    var comparison = MovementReconciliation.compare(history, actual());
    assertThat(comparison.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
    assertThat(comparison.derivedFields()).isEmpty();
    assertThat(comparison.driftFields()).isEmpty();
  }
}
