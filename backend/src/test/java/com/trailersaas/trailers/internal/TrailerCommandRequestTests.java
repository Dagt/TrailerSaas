package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;

class TrailerCommandRequestTests {
  final JsonMapper json = JsonMapper.builder().build();

  static Stream<Arguments> bodies() {
    var id = UUID.randomUUID().toString();
    return Stream.of(
        Arguments.of(
            TrailerCommandRequests.LocationCorrection.class,
            Map.of("expectedVersion", 0, "newLocationId", id, "reason", "Confirmed")),
        Arguments.of(
            TrailerCommandRequests.MarkUnknown.class,
            Map.of("expectedVersion", 0, "reason", "Missing")),
        Arguments.of(
            TrailerCommandRequests.LoadChange.class,
            Map.of("expectedVersion", 0, "loadState", "EMPTY", "reason", "Confirmed")),
        Arguments.of(
            TrailerCommandRequests.OutOfService.class,
            Map.of("expectedVersion", 0, "comment", "Damage")),
        Arguments.of(
            TrailerCommandRequests.ReturnToService.class,
            Map.of("expectedVersion", 0, "reason", "Inspected")),
        Arguments.of(
            TrailerCommandRequests.CustomerDesignation.class,
            Map.of("expectedVersion", 0, "newCustomerId", id, "reason", "Reassigned")));
  }

  @ParameterizedTest
  @MethodSource("bodies")
  void typedBodiesAcceptOnlyTheExactContract(Class<?> type, Map<String, Object> valid) {
    assertThat(json.readValue(json.writeValueAsString(valid), type)).isInstanceOf(type);
    for (String field :
        List.of(
            "actorType",
            "sourceType",
            "actorUserId",
            "occurredAt",
            "tenantId",
            "driverId",
            "documentIds",
            "observedLocationId")) {
      var input = new HashMap<>(valid);
      input.put(field, "spoofed");
      assertThatThrownBy(() -> json.readValue(json.writeValueAsString(input), type))
          .hasRootCauseInstanceOf(TrailerFailure.class);
    }
  }

  @ParameterizedTest
  @MethodSource("bodies")
  void strictVersionsAndRequiredFieldsCannotBeCoerced(Class<?> type, Map<String, Object> valid) {
    for (Object value : List.of(-1, 0.5, "0", true)) {
      var input = new HashMap<>(valid);
      input.put("expectedVersion", value);
      assertThatThrownBy(() -> json.readValue(json.writeValueAsString(input), type))
          .hasRootCauseInstanceOf(TrailerFailure.class);
    }
    for (String field : valid.keySet()) {
      var input = new HashMap<>(valid);
      input.remove(field);
      assertThatThrownBy(() -> json.readValue(json.writeValueAsString(input), type))
          .hasRootCauseInstanceOf(TrailerFailure.class);
      input.put(field, null);
      assertThatThrownBy(() -> json.readValue(json.writeValueAsString(input), type))
          .hasRootCauseInstanceOf(TrailerFailure.class);
      input.put(field, " ");
      assertThatThrownBy(() -> json.readValue(json.writeValueAsString(input), type))
          .hasRootCauseInstanceOf(TrailerFailure.class);
    }
  }
}
