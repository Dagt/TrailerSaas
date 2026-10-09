package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class TripCouplingValidationTests {
  Map<String, Object> hook() {
    return new LinkedHashMap<>(
        Map.of(
            "trailerId",
            UUID.randomUUID().toString(),
            "tractorId",
            UUID.randomUUID().toString(),
            "driverId",
            UUID.randomUUID().toString(),
            "occurredAt",
            "2026-10-09T20:00:00Z",
            "expectedTrailerVersion",
            0));
  }

  @Test
  void validStrictCommandsAndCanonicalUuidKey() {
    var r = hook();
    assertThat(TripCouplingValidation.hook(r).expectedTrailerVersion()).isZero();
    assertThat(TripCouplingValidation.key("00000000-0000-0000-0000-000000000001"))
        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    assertThat(
            TripCouplingValidation.drop(
                Map.of(
                    "trailerId",
                    UUID.randomUUID().toString(),
                    "stopId",
                    UUID.randomUUID().toString(),
                    "locationId",
                    UUID.randomUUID().toString(),
                    "occurredAt",
                    "2026-10-09T20:00:00Z")))
        .isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "not-uuid",
        "1-1-1-1-1",
        " 00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000001 "
      })
  void invalidIdempotencyKeyIsPublicValidationOnly(String key) {
    assertThatThrownBy(() -> TripCouplingValidation.key(key))
        .isInstanceOf(TripFailure.class)
        .satisfies(e -> assertThat(((TripFailure) e).code).isEqualTo("VALIDATION_FAILED"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"trailerId", "tractorId", "driverId", "occurredAt", "expectedTrailerVersion"})
  void everyHookFieldIsRequired(String field) {
    var r = hook();
    r.remove(field);
    assertThatThrownBy(() -> TripCouplingValidation.hook(r)).isInstanceOf(TripFailure.class);
  }

  @Test
  void onlyIntegerNonNegativeVersionAndStrictShapeAccepted() {
    for (var version : List.of(-1, 0.1, "0", true)) {
      var r = hook();
      r.put("expectedTrailerVersion", version);
      assertThatThrownBy(() -> TripCouplingValidation.hook(r)).isInstanceOf(TripFailure.class);
    }
    var r = hook();
    r.put("tenantId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> TripCouplingValidation.hook(r)).isInstanceOf(TripFailure.class);
  }
}
