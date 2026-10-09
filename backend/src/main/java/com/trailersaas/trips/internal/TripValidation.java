package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.orders.OrderDestinationSnapshots;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

final class TripValidation {
  static final Set<String> STATUSES =
      Set.of(
          "PLANNED", "ASSIGNED", "IN_TRANSIT", "AT_STOP", "CANCELLING", "COMPLETED", "CANCELLED");
  static final Set<String> TYPES =
      Set.of(
          "BASE_DEPARTURE",
          "PICKUP",
          "DELIVERY",
          "LIVE_LOAD",
          "LIVE_UNLOAD",
          "DROP_HOOK",
          "BASE_RETURN",
          "OTHER");

  static Create create(Map<String, Object> input, OrderDestinationSnapshots snapshots) {
    fields(input, Set.of("orderIds", "stops", "driverId", "tractorId", "plannedInitialTrailerId"));
    var orders = ids(input.get("orderIds"));
    if (orders.isEmpty()) {
      throw TripFailure.invalid("orderIds");
    }
    if (!(input.get("stops") instanceof List<?> rows) || rows.isEmpty()) {
      throw TripFailure.invalid("stops");
    }
    var stops = new ArrayList<StopInput>();
    var sequences = new HashSet<Integer>();
    var mapped = new HashSet<UUID>();
    for (var row : rows) {
      if (!(row instanceof Map<?, ?> stop)) {
        throw TripFailure.invalid("stops");
      }
      fields(
          stop,
          Set.of(
              "sequenceNumber",
              "stopType",
              "siteId",
              "locationId",
              "siteSnapshot",
              "scheduledAt",
              "orderIds",
              "instructions"));
      int sequence = integer(stop.get("sequenceNumber"));
      if (sequence < 1 || !sequences.add(sequence)) {
        throw TripFailure.invalid("sequenceNumber");
      }
      var type = text(stop.get("stopType"), false);
      if (!TYPES.contains(type)) {
        throw TripFailure.invalid("stopType");
      }
      var site = uuid(stop.get("siteId"), true);
      var location = uuid(stop.get("locationId"), true);
      Map<String, Object> snapshot;
      try {
        snapshot = snapshots.freeze(stop.get("siteSnapshot"));
      } catch (IllegalArgumentException e) {
        throw TripFailure.invalid("siteSnapshot");
      }
      if ((location != null && site == null) || (site == null && snapshot == null)) {
        throw TripFailure.invalid("stops");
      }
      var stopOrders = ids(stop.get("orderIds"));
      if (!orders.containsAll(stopOrders)) {
        throw TripFailure.invalid("orderIds");
      }
      mapped.addAll(stopOrders);
      stops.add(
          new StopInput(
              sequence,
              type,
              site,
              location,
              snapshot,
              instant(stop.get("scheduledAt")),
              stopOrders,
              text(stop.get("instructions"), true)));
    }
    if (!mapped.equals(new HashSet<>(orders))) {
      throw TripFailure.invalid("orderIds");
    }
    stops.sort(Comparator.comparingInt(StopInput::sequenceNumber));
    return new Create(
        orders,
        List.copyOf(stops),
        uuid(input.get("driverId"), true),
        uuid(input.get("tractorId"), true),
        uuid(input.get("plannedInitialTrailerId"), true));
  }

  static Assign assignment(Map<String, Object> input, String resourceField) {
    fields(input, Set.of("expectedVersion", resourceField, "reason"));
    var value = input.get("expectedVersion");
    if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
      throw TripFailure.invalid("expectedVersion");
    }
    return new Assign(
        ((Number) value).longValue(),
        uuid(input.get(resourceField), false),
        text(input.get("reason"), false));
  }

  static Substitution substitution(Map<String, Object> input) {
    fields(input, Set.of("expectedTrailerId", "requestedTrailerId", "decision", "reason"));
    var expected = uuid(input.get("expectedTrailerId"), false);
    var requested = uuid(input.get("requestedTrailerId"), false);
    var decision = text(input.get("decision"), false);
    if (expected.equals(requested) || !Set.of("AUTHORIZE", "REJECT").contains(decision)) {
      throw TripFailure.invalid("decision");
    }
    return new Substitution(expected, requested, decision, text(input.get("reason"), false));
  }

  static void fields(Map<?, ?> input, Set<String> allowed) {
    if (input == null || input.keySet().stream().anyMatch(k -> !allowed.contains(k))) {
      throw TripFailure.invalid("body");
    }
  }

  static String text(Object input, boolean nullable) {
    if (input == null && nullable) {
      return null;
    }
    if (!(input instanceof String s) || (!nullable && s.isBlank())) {
      throw TripFailure.invalid("body");
    }
    return s;
  }

  static UUID uuid(Object input, boolean nullable) {
    if (input == null && nullable) {
      return null;
    }
    var s = text(input, false);
    try {
      var id = UUID.fromString(s);
      if (!id.toString().equalsIgnoreCase(s)) {
        throw TripFailure.invalid("id");
      }
      return id;
    } catch (IllegalArgumentException e) {
      throw TripFailure.invalid("id");
    }
  }

  static int integer(Object value) {
    if (!(value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long)
        || ((Number) value).longValue() > Integer.MAX_VALUE
        || ((Number) value).longValue() < Integer.MIN_VALUE) {
      throw TripFailure.invalid("number");
    }
    return ((Number) value).intValue();
  }

  static List<UUID> ids(Object input) {
    if (!(input instanceof List<?> list)) {
      throw TripFailure.invalid("orderIds");
    }
    var ids = list.stream().map(v -> uuid(v, false)).toList();
    if (new HashSet<>(ids).size() != ids.size()) {
      throw TripFailure.invalid("orderIds");
    }
    return ids;
  }

  static Instant instant(Object input) {
    if (input == null) {
      return null;
    }
    try {
      return Instant.parse(text(input, false));
    } catch (DateTimeException e) {
      throw TripFailure.invalid("scheduledAt");
    }
  }

  record Position(Instant createdAt, UUID id) {
    String encode() {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Position decode(String input) {
      try {
        if (input.length() > 160) {
          throw TripFailure.invalid("cursor");
        }
        var p =
            new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (p.length != 2) {
          throw TripFailure.invalid("cursor");
        }
        var result = new Position(Instant.parse(p[0]), uuid(p[1], false));
        if (!result.encode().equals(input)) {
          throw TripFailure.invalid("cursor");
        }
        return result;
      } catch (IllegalArgumentException | DateTimeException e) {
        throw TripFailure.invalid("cursor");
      }
    }
  }

  record Filters(String status, UUID driverId, UUID customerId, Position cursor, int limit) {}

  static Filters filters(Map<String, String> input) {
    fields(input, Set.of("status", "driverId", "customerId", "cursor", "limit"));
    var status = input.get("status");
    if (status != null && !STATUSES.contains(status)) {
      throw TripFailure.invalid("status");
    }
    int limit = 50;
    if (input.containsKey("limit")) {
      try {
        limit = Integer.parseInt(input.get("limit"));
      } catch (NumberFormatException e) {
        throw TripFailure.invalid("limit");
      }
    }
    if (limit < 1 || limit > 200) {
      throw TripFailure.invalid("limit");
    }
    return new Filters(
        status,
        uuid(input.get("driverId"), true),
        uuid(input.get("customerId"), true),
        input.containsKey("cursor") ? Position.decode(input.get("cursor")) : null,
        limit);
  }
}
