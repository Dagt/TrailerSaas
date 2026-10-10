package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TrailerReadContractTests {
  @ParameterizedTest
  @ValueSource(strings = {"0", "201", "-1", "1.5", "01", "abc", "1000", ""})
  void rejectsInvalidLimits(String limit) {
    assertThatThrownBy(() -> TrailerTimelineQuery.parse(Map.of("limit", limit)))
        .isInstanceOf(TrailerFailure.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"!", "AA", "dGVzdA", "with-padding=", ""})
  void rejectsMalformedCursors(String cursor) {
    assertThatThrownBy(() -> TrailerTimelineQuery.parse(Map.of("cursor", cursor)))
        .isInstanceOf(TrailerFailure.class);
  }

  @Test
  void opaqueCursorRetainsExactTimestampAndTieBreakIdentity() {
    var position =
        new TrailerTimelineQuery.Position(
            Instant.parse("2026-10-01T00:00:00.123456Z"), UUID.randomUUID());
    assertThat(TrailerTimelineQuery.parse(Map.of("cursor", position.encode(), "limit", "200")))
        .isEqualTo(new TrailerTimelineQuery(200, position));
    assertThat(TrailerTimelineQuery.parse(Map.of()).limit()).isEqualTo(50);
    assertThatThrownBy(() -> TrailerTimelineQuery.parse(Map.of("page", "1")))
        .isInstanceOf(TrailerFailure.class);
  }

  @Test
  void readProjectionKeepsTheEntireCommandDtoFlatWithoutInternalFields() {
    var t = new TrailerEntity();
    t.initialize(UUID.randomUUID(), "TR-123");
    var base = TrailerDtos.Trailer.of(t);
    var item =
        new TrailerReadDtos.Item(
            base, null, null, null, "UNCOUPLED", null, null, null, null, null, null, null);
    var detail =
        new TrailerReadDtos.Detail(
            item, null, null, null, null, null, null, null, null, List.of(), false);
    var json = JsonMapper.builder().build();
    var body = json.readTree(json.writeValueAsString(detail));
    assertThat(body.get("id").asString()).isEqualTo(t.id.toString());
    assertThat(body.get("version").asLong()).isZero();
    assertThat(body.get("couplingState").asString()).isEqualTo("UNCOUPLED");
    assertThat(body.has("trailer")).isFalse();
    assertThat(body.has("projection")).isFalse();
    assertThat(body.has("tenantId")).isFalse();
    assertThat(body.has("normalizedNumber")).isFalse();
    var baseline = json.readTree(json.writeValueAsString(base));
    baseline.properties().forEach(e -> assertThat(body.get(e.getKey())).isEqualTo(e.getValue()));
  }
}
