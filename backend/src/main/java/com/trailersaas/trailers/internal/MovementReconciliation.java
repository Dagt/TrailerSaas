package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerProjectionReconciliation.*;

import java.time.Instant;
import java.util.*;

/** Nullable derived values are distinct from unknown baseline fields (absent keys). */
final class MovementReconciliation {
  static Comparison compare(List<TrailerMovementEntity> history, MovementRules.State actual) {
    var derived = new EnumMap<Field, Object>(Field.class);
    // Database and pure reducer share durable server chronology, never occurrence chronology.
    history.stream()
        .sorted(
            Comparator.comparing((TrailerMovementEntity e) -> e.recordedAt)
                .thenComparing(e -> e.id.toString()))
        .forEach(e -> apply(derived, e));
    var current = new EnumMap<Field, Object>(Field.class);
    current.put(Field.LOCATION_STATE, actual.locationState().name());
    current.put(Field.CURRENT_LOCATION, actual.currentLocationId());
    current.put(Field.LAST_KNOWN_LOCATION, actual.lastKnownLocationId());
    current.put(Field.LAST_SEEN, actual.lastSeenAt());
    current.put(Field.LAST_DRIVER, actual.lastDriverId());
    current.put(Field.LOAD_STATE, actual.loadState().name());
    var drift = EnumSet.noneOf(Field.class);
    derived.forEach(
        (field, value) -> {
          if (!Objects.equals(value, current.get(field))) drift.add(field);
        });
    return new Comparison(
        !drift.isEmpty()
            ? Status.DRIFT
            : derived.size() == Field.values().length ? Status.MATCH : Status.INSUFFICIENT_HISTORY,
        derived.keySet(),
        drift);
  }

  private static void apply(Map<Field, Object> d, TrailerMovementEntity e) {
    var m = e.metadata == null ? Map.<String, Object>of() : e.metadata;
    switch (e.eventType) {
      case "HOOKED" -> observation(d, e);
      case "DROPPED", "ARRIVED", "YARD_MOVED", "LOCATION_RECOVERED" -> {
        at(d, e.locationId);
        observation(d, e);
      }
      case "DEPARTED" -> {
        d.put(Field.LOCATION_STATE, "IN_TRANSIT");
        d.put(Field.CURRENT_LOCATION, null);
        d.put(Field.LAST_KNOWN_LOCATION, e.locationId);
        observation(d, e);
      }
      case "LOCATION_CORRECTED" -> {
        at(d, e.locationId);
        if (Boolean.TRUE.equals(m.get("physicalObservation"))) observation(d, e);
        else preserved(d, m);
      }
      case "LOCATION_MARKED_UNKNOWN" -> {
        d.put(Field.LOCATION_STATE, "LOCATION_UNKNOWN");
        d.put(Field.CURRENT_LOCATION, null);
        if (m.containsKey("preservedLastKnownLocationId"))
          d.put(Field.LAST_KNOWN_LOCATION, uuid(m.get("preservedLastKnownLocationId")));
        preserved(d, m);
      }
      case "LOAD_STATE_CHANGED" -> {
        if (m.containsKey("newLoadState"))
          d.put(Field.LOAD_STATE, m.get("newLoadState").toString());
      }
      case "MARKED_OOS", "RETURNED_TO_SERVICE", "DESIGNATED_CUSTOMER_CHANGED" -> {
        // Known Accepted facts do not affect the movement/load fields owned by this projection.
      }
      default -> {
        // A later writer we cannot interpret must not produce a false MATCH from earlier facts.
        d.clear();
      }
    }
  }

  private static void at(Map<Field, Object> d, UUID location) {
    d.put(Field.LOCATION_STATE, "AT_LOCATION");
    d.put(Field.CURRENT_LOCATION, location);
    d.put(Field.LAST_KNOWN_LOCATION, location);
  }

  private static void observation(Map<Field, Object> d, TrailerMovementEntity e) {
    d.put(Field.LAST_SEEN, e.occurredAt);
    if (e.driverId != null) d.put(Field.LAST_DRIVER, e.driverId);
  }

  private static void preserved(Map<Field, Object> d, Map<String, Object> m) {
    if (m.containsKey("preservedLastSeenAt"))
      d.put(
          Field.LAST_SEEN,
          m.get("preservedLastSeenAt") == null
              ? null
              : Instant.parse(m.get("preservedLastSeenAt").toString()));
    if (m.containsKey("preservedLastDriverId"))
      d.put(Field.LAST_DRIVER, uuid(m.get("preservedLastDriverId")));
  }

  private static UUID uuid(Object v) {
    return v == null ? null : UUID.fromString(v.toString());
  }
}
