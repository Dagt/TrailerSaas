package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripAssignmentValidationTests {
  @ParameterizedTest
  @ValueSource(strings = {"driverId", "tractorId", "trailerId"})
  void strictAssignmentRequiresNonnegativeIntegralVersionUuidAndNonblankReason(String field) {
    var b =
        new HashMap<String, Object>(
            Map.of(
                "expectedVersion",
                Long.MAX_VALUE,
                field,
                UUID.randomUUID().toString(),
                "reason",
                "Dispatch"));
    assertThat(TripValidation.assignment(b, field).expectedVersion()).isEqualTo(Long.MAX_VALUE);
    for (var v : List.of(-1, 0.2, "0", true)) {
      b.put("expectedVersion", v);
      assertThatThrownBy(() -> TripValidation.assignment(b, field)).isInstanceOf(TripFailure.class);
    }
    b.put("expectedVersion", 0);
    b.put("reason", " ");
    assertThatThrownBy(() -> TripValidation.assignment(b, field)).isInstanceOf(TripFailure.class);
    b.put("reason", "Dispatch");
    b.put("actorUserId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> TripValidation.assignment(b, field)).isInstanceOf(TripFailure.class);
  }

  @Test
  void substitutionContractRejectsInventedVersionAndUnknownDecision() {
    var b =
        new HashMap<String, Object>(
            Map.of(
                "expectedTrailerId",
                UUID.randomUUID().toString(),
                "requestedTrailerId",
                UUID.randomUUID().toString(),
                "decision",
                "AUTHORIZE",
                "reason",
                "Dispatch"));
    assertThat(TripValidation.substitution(b).decision()).isEqualTo("AUTHORIZE");
    b.put("expectedVersion", 0);
    assertThatThrownBy(() -> TripValidation.substitution(b)).isInstanceOf(TripFailure.class);
    b.remove("expectedVersion");
    b.put("decision", "APPROVE");
    assertThatThrownBy(() -> TripValidation.substitution(b)).isInstanceOf(TripFailure.class);
    b.put("decision", "REJECT");
    b.put("requestedTrailerId", b.get("expectedTrailerId"));
    assertThatThrownBy(() -> TripValidation.substitution(b)).isInstanceOf(TripFailure.class);
  }
}
