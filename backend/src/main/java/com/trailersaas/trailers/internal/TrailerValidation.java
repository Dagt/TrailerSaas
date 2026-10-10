package com.trailersaas.trailers.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TrailerValidation {
  static final Set<String> LOCATIONS = Set.of("AT_LOCATION", "IN_TRANSIT", "LOCATION_UNKNOWN");
  static final Set<String> LOADS = Set.of("EMPTY", "LOADED", "UNKNOWN");
  static final Set<String> SERVICES = Set.of("IN_SERVICE", "OUT_OF_SERVICE");
  static final Set<String> PURPOSES =
      Set.of("DELIVERY", "SHUTTLE", "WIP_STORAGE", "TOOLING_STORAGE", "OTHER");

  private TrailerValidation() {}

  static void fields(Map<String, ?> fields, Set<String> allowed) {
    if (fields == null || fields.keySet().stream().anyMatch(k -> !allowed.contains(k))) {
      throw TrailerFailure.invalid();
    }
  }

  static long version(Map<String, Object> fields, Set<String> allowed) {
    fields(fields, allowed);
    Object value = fields.get("expectedVersion");
    if (!(value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long)
        || ((Number) value).longValue() < 0) {
      throw TrailerFailure.invalid();
    }
    return ((Number) value).longValue();
  }

  static String text(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof String s)) {
      throw TrailerFailure.invalid();
    }
    return s;
  }

  static UUID uuid(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof String s)) {
      throw TrailerFailure.invalid();
    }
    try {
      var uuid = UUID.fromString(s);
      if (!uuid.toString().equalsIgnoreCase(s)) {
        throw TrailerFailure.invalid();
      }
      return uuid;
    } catch (IllegalArgumentException invalid) {
      throw TrailerFailure.invalid();
    }
  }

  static String catalog(Object value, Set<String> values, boolean nullable) {
    if (value == null && nullable) {
      return null;
    }
    if (!(value instanceof String s) || !values.contains(s)) {
      throw TrailerFailure.invalid();
    }
    return s;
  }

  static boolean active(Object value) {
    if (!(value instanceof Boolean b)) {
      throw TrailerFailure.invalid();
    }
    return b;
  }

  static String number(Object value) {
    if (!(value instanceof String s)
        || s.isEmpty()
        || s.codePoints().anyMatch(Character::isISOControl)
        || s.chars().allMatch(c -> c == ' ')) {
      throw TrailerFailure.invalid();
    }
    return s;
  }

  static String normalized(Object value) {
    String original = number(value);
    int start = 0, end = original.length();
    while (start < end && original.charAt(start) == ' ') {
      start++;
    }
    while (end > start && original.charAt(end - 1) == ' ') {
      end--;
    }
    var key = new StringBuilder();
    for (int i = start; i < end; i++) {
      char c = original.charAt(i);
      if (c == ' ' && !key.isEmpty() && key.charAt(key.length() - 1) == ' ') {
        continue;
      }
      key.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
    }
    return key.toString();
  }

  static Map<String, Object> filters(Map<String, String> input) {
    fields(
        input,
        Set.of(
            "operationalNumber",
            "designatedCustomerId",
            "locationState",
            "currentLocationId",
            "loadState",
            "serviceabilityState",
            "active",
            "coupled"));
    var result = new java.util.HashMap<String, Object>();
    input.forEach(
        (key, value) ->
            result.put(
                key,
                switch (key) {
                  case "operationalNumber" -> normalized(value);
                  case "designatedCustomerId", "currentLocationId" -> uuid(value);
                  case "locationState" -> catalog(value, LOCATIONS, false);
                  case "loadState" -> catalog(value, LOADS, false);
                  case "serviceabilityState" -> catalog(value, SERVICES, false);
                  case "active", "coupled" -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                      throw TrailerFailure.invalid();
                    }
                    yield Boolean.valueOf(value);
                  }
                  default -> throw TrailerFailure.invalid();
                }));
    return result;
  }

  static void noParameters(Map<String, String> input) {
    if (!input.isEmpty()) {
      throw TrailerFailure.invalid();
    }
  }
}
