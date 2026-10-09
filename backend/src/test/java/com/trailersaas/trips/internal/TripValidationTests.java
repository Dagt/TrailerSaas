package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import com.trailersaas.orders.OrderDestinationSnapshots;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripValidationTests {
  final UUID order = UUID.randomUUID();
  final OrderDestinationSnapshots snapshots =
      value -> value == null ? null : Map.of("name", "Frozen");

  Map<String, Object> stop(int sequence, List<String> ids) {
    return new HashMap<>(
        Map.of(
            "sequenceNumber",
            sequence,
            "stopType",
            "DELIVERY",
            "orderIds",
            ids,
            "siteSnapshot",
            Map.of("name", "Frozen")));
  }

  Map<String, Object> body() {
    return new HashMap<>(
        Map.of(
            "orderIds",
            List.of(order.toString()),
            "stops",
            List.of(stop(1, List.of(order.toString())))));
  }

  @Test
  void distinctExecutableStopsAreOrderedAndOperationalStopsNeedNoOrder() {
    var b = body();
    b.put(
        "stops",
        List.of(
            stop(8, List.of()),
            stop(3, List.of(order.toString())),
            stop(6, List.of(order.toString()))));
    var result = TripValidation.create(b, snapshots);
    assertThat(result.stops())
        .extracting(TripContracts.StopInput::sequenceNumber)
        .containsExactly(3, 6, 8);
    assertThat(result.stops().getLast().orderIds()).isEmpty();
    assertThat(result.stops()).hasSize(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "version", "orderStopId", "startedAt", "currentTrailerId"})
  void rejectsUnknownCreateFields(String field) {
    var b = body();
    b.put(field, null);
    assertThatThrownBy(() -> TripValidation.create(b, snapshots)).isInstanceOf(TripFailure.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"orderStopId", "status", "arrivedAt", "version", "customerId"})
  void rejectsUnknownStopFields(String field) {
    var b = body();
    var s = stop(1, List.of(order.toString()));
    s.put(field, null);
    b.put("stops", List.of(s));
    assertThatThrownBy(() -> TripValidation.create(b, snapshots)).isInstanceOf(TripFailure.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "emptyOrders",
        "duplicateOrders",
        "emptyStops",
        "unmapped",
        "foreignStopOrder",
        "duplicateSequence",
        "duplicateStopOrder",
        "zeroSequence",
        "decimalSequence",
        "fuel",
        "noDestination",
        "locationWithoutSite",
        "badTime"
      })
  void rejectsMalformedOrInconsistentPlans(String kind) {
    var b = body();
    var s = stop(1, List.of(order.toString()));
    b.put("stops", List.of(s));
    switch (kind) {
      case "emptyOrders" -> b.put("orderIds", List.of());
      case "duplicateOrders" -> b.put("orderIds", List.of(order.toString(), order.toString()));
      case "emptyStops" -> b.put("stops", List.of());
      case "unmapped" -> s.put("orderIds", List.of());
      case "foreignStopOrder" -> s.put("orderIds", List.of(UUID.randomUUID().toString()));
      case "duplicateSequence" -> b.put("stops", List.of(s, s));
      case "duplicateStopOrder" -> s.put("orderIds", List.of(order.toString(), order.toString()));
      case "zeroSequence" -> s.put("sequenceNumber", 0);
      case "decimalSequence" -> s.put("sequenceNumber", 1.0);
      case "fuel" -> s.put("stopType", "FUEL");
      case "noDestination" -> s.remove("siteSnapshot");
      case "locationWithoutSite" -> s.put("locationId", UUID.randomUUID().toString());
      case "badTime" -> s.put("scheduledAt", "tomorrow");
    }
    assertThatThrownBy(() -> TripValidation.create(b, snapshots)).isInstanceOf(TripFailure.class);
  }

  @Test
  void normalizedSiteAndSnapshotCanCoexist() {
    var b = body();
    var s = stop(1, List.of(order.toString()));
    s.put("siteId", UUID.randomUUID().toString());
    b.put("stops", List.of(s));
    assertThat(TripValidation.create(b, snapshots).stops().getFirst().siteSnapshot())
        .containsEntry("name", "Frozen");
  }

  @ParameterizedTest
  @ValueSource(strings = {"from", "to", "active", "unplanned", "appointmentFrom"})
  void rejectsDeferredFilters(String name) {
    assertThatThrownBy(() -> TripValidation.filters(Map.of(name, "true")))
        .isInstanceOf(TripFailure.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "201", "1.5", "no"})
  void enforcesPageLimit(String value) {
    assertThatThrownBy(() -> TripValidation.filters(Map.of("limit", value)))
        .isInstanceOf(TripFailure.class);
  }

  @Test
  void keysetCursorIsCanonicalAndRoundTripsExactTimestampAndUuid() {
    var p = new TripValidation.Position(Instant.parse("2026-01-01T01:02:03.123456Z"), order);
    assertThat(TripValidation.Position.decode(p.encode())).isEqualTo(p);
    assertThatThrownBy(() -> TripValidation.Position.decode(p.encode() + "="))
        .isInstanceOf(TripFailure.class);
    assertThat(TripValidation.filters(Map.of()).limit()).isEqualTo(50);
  }
}
