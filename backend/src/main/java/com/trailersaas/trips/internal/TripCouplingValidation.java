package com.trailersaas.trips.internal;

import com.trailersaas.trailers.TrailerCouplingTransactions.*;
import java.time.Instant;
import java.util.*;

final class TripCouplingValidation {
  static UUID key(String header) {
    return TripValidation.uuid(header, false);
  }

  static Hook hook(Map<String, Object> input) {
    TripValidation.fields(
        input,
        Set.of("trailerId", "tractorId", "driverId", "occurredAt", "expectedTrailerVersion"));
    var version = input.get("expectedTrailerVersion");
    if (!(version instanceof Integer || version instanceof Long)
        || ((Number) version).longValue() < 0) throw TripFailure.invalid("expectedTrailerVersion");
    return new Hook(
        TripValidation.uuid(input.get("trailerId"), false),
        TripValidation.uuid(input.get("tractorId"), false),
        TripValidation.uuid(input.get("driverId"), false),
        occurred(input),
        ((Number) version).longValue());
  }

  static Drop drop(Map<String, Object> input) {
    TripValidation.fields(input, Set.of("trailerId", "stopId", "locationId", "occurredAt"));
    return new Drop(
        TripValidation.uuid(input.get("trailerId"), false),
        TripValidation.uuid(input.get("stopId"), false),
        TripValidation.uuid(input.get("locationId"), false),
        occurred(input));
  }

  private static Instant occurred(Map<String, Object> input) {
    var at = TripValidation.instant(input.get("occurredAt"));
    if (at == null) throw TripFailure.invalid("occurredAt");
    return at;
  }
}
