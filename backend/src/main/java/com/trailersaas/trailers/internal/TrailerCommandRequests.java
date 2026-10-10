package com.trailersaas.trailers.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.trailersaas.trailers.TrailerMovementProjection.LoadState;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Typed command bodies; explicit tree decoding prevents scalar coercion and mass assignment. */
final class TrailerCommandRequests {
  private TrailerCommandRequests() {}

  record LocationCorrection(long expectedVersion, UUID newLocationId, String reason) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static LocationCorrection read(JsonNode input) {
      fields(input, "expectedVersion", "newLocationId", "reason");
      return new LocationCorrection(
          version(input),
          id(input, "newLocationId"),
          TrailerCommandRequests.reason(input, "reason"));
    }
  }

  record MarkUnknown(long expectedVersion, String reason) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static MarkUnknown read(JsonNode input) {
      fields(input, "expectedVersion", "reason");
      return new MarkUnknown(version(input), TrailerCommandRequests.reason(input, "reason"));
    }
  }

  record LoadChange(long expectedVersion, LoadState loadState, String reason) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static LoadChange read(JsonNode input) {
      fields(input, "expectedVersion", "loadState", "reason");
      var state = TrailerCommandRequests.reason(input, "loadState");
      try {
        return new LoadChange(
            version(input),
            LoadState.valueOf(state),
            TrailerCommandRequests.reason(input, "reason"));
      } catch (IllegalArgumentException invalid) {
        throw TrailerFailure.invalid();
      }
    }
  }

  record OutOfService(long expectedVersion, String comment) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static OutOfService read(JsonNode input) {
      fields(input, "expectedVersion", "comment");
      return new OutOfService(version(input), TrailerCommandRequests.reason(input, "comment"));
    }
  }

  record ReturnToService(long expectedVersion, String reason) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static ReturnToService read(JsonNode input) {
      fields(input, "expectedVersion", "reason");
      return new ReturnToService(version(input), TrailerCommandRequests.reason(input, "reason"));
    }
  }

  record CustomerDesignation(long expectedVersion, UUID newCustomerId, String reason) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static CustomerDesignation read(JsonNode input) {
      fields(input, "expectedVersion", "newCustomerId", "reason");
      return new CustomerDesignation(
          version(input),
          id(input, "newCustomerId"),
          TrailerCommandRequests.reason(input, "reason"));
    }
  }

  private static void fields(JsonNode input, String... fields) {
    if (input == null || !input.isObject()) throw TrailerFailure.invalid();
    var allowed = Set.of(fields);
    if (input.propertyStream().anyMatch(p -> !allowed.contains(p.getKey())))
      throw TrailerFailure.invalid();
  }

  private static long version(JsonNode input) {
    var v = input.get("expectedVersion");
    if (v == null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 0)
      throw TrailerFailure.invalid();
    return v.longValue();
  }

  private static String reason(JsonNode input, String field) {
    var v = input.get(field);
    if (v == null || !v.isString() || v.stringValue().isBlank()) throw TrailerFailure.invalid();
    return v.stringValue();
  }

  private static UUID id(JsonNode input, String field) {
    var id = TrailerValidation.uuid(TrailerCommandRequests.reason(input, field));
    if (id == null) throw TrailerFailure.invalid();
    return id;
  }
}
