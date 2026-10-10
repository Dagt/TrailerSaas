package com.trailersaas.trips.internal;

import java.time.Instant;
import java.util.*;

final class TripExecutionRequests {
  enum Action {
    FIRST_DEPARTURE,
    ARRIVAL,
    SERVICE,
    COMPLETE,
    DEPARTURE
  }

  record Command(
      Instant occurredAt,
      UUID fromStopId,
      UUID nextStopId,
      String source,
      Map<String, Object> coordinates,
      String notes) {}

  static Command parse(Action action, Map<String, Object> body) {
    TripValidation.fields(
        body,
        switch (action) {
          case FIRST_DEPARTURE -> Set.of("occurredAt", "fromStopId", "nextStopId");
          case ARRIVAL -> Set.of("occurredAt", "source", "coordinates");
          case SERVICE -> Set.of("occurredAt");
          case COMPLETE -> Set.of("occurredAt", "notes");
          case DEPARTURE -> Set.of("occurredAt", "nextStopId");
        });
    var at = TripValidation.instant(body.get("occurredAt"));
    if (at == null) throw TripFailure.invalid("occurredAt");
    String source = null;
    Map<String, Object> coordinates = null;
    if (action == Action.ARRIVAL) {
      source = TripValidation.text(body.get("source"), false);
      // #47 owns confirmed geofence assistance; do not accept a fabricated signal in #44.
      if (!"MANUAL".equals(source)) throw TripFailure.invalid("source");
      if (body.get("coordinates") != null) {
        if (!(body.get("coordinates") instanceof Map<?, ?> raw))
          throw TripFailure.invalid("coordinates");
        coordinates = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
          if (!(entry.getKey() instanceof String key)) throw TripFailure.invalid("coordinates");
          coordinates.put(key, entry.getValue());
        }
        TripValidation.fields(coordinates, Set.of("latitude", "longitude", "accuracyM"));
        coordinate(coordinates, "latitude", -90, 90);
        coordinate(coordinates, "longitude", -180, 180);
        coordinate(coordinates, "accuracyM", 0, Double.MAX_VALUE);
        // Hash the meaning, not JSON property order or equivalent numeric encodings.
        var canonical = new LinkedHashMap<String, Object>();
        for (var field : List.of("latitude", "longitude", "accuracyM"))
          canonical.put(field, ((Number) coordinates.get(field)).doubleValue());
        coordinates = canonical;
      }
    }
    return new Command(
        at,
        TripValidation.uuid(body.get("fromStopId"), true),
        TripValidation.uuid(
            body.get("nextStopId"), action != Action.FIRST_DEPARTURE && action != Action.DEPARTURE),
        source,
        coordinates,
        TripValidation.text(body.get("notes"), true));
  }

  private static void coordinate(Map<String, Object> m, String field, double min, double max) {
    if (!(m.get(field) instanceof Number n)
        || !Double.isFinite(n.doubleValue())
        || n.doubleValue() < min
        || n.doubleValue() > max) throw TripFailure.invalid(field);
  }
}
