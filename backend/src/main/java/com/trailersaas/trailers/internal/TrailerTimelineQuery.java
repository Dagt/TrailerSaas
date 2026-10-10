package com.trailersaas.trailers.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

record TrailerTimelineQuery(int limit, Position position) {
  record Position(Instant occurredAt, UUID id) {
    String encode() {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(("1|" + occurredAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
  }

  static TrailerTimelineQuery parse(Map<String, String> input) {
    TrailerValidation.fields(input, Set.of("cursor", "limit"));
    int limit = 50;
    if (input.containsKey("limit")) {
      String value = input.get("limit");
      if (!value.matches("[1-9][0-9]{0,2}")) throw TrailerFailure.invalid();
      limit = Integer.parseInt(value);
      if (limit > 200) throw TrailerFailure.invalid();
    }
    Position position = null;
    if (input.containsKey("cursor")) {
      String value = input.get("cursor");
      if (value.length() > 200 || !value.matches("[A-Za-z0-9_-]+")) throw TrailerFailure.invalid();
      try {
        String[] parts =
            new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (parts.length != 3 || !parts[0].equals("1")) throw TrailerFailure.invalid();
        position = new Position(Instant.parse(parts[1]), TrailerValidation.uuid(parts[2]));
        if (!position.encode().equals(value)) throw TrailerFailure.invalid();
      } catch (IllegalArgumentException | java.time.DateTimeException failure) {
        throw TrailerFailure.invalid();
      }
    }
    return new TrailerTimelineQuery(limit, position);
  }
}
