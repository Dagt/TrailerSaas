package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;

import com.trailersaas.trailers.TrailerCouplingTransactions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CouplingCanonicalRequestTests {
  @Test
  void logicalHookHashIsStableAndEverySemanticFieldChangesIt() {
    var trip = UUID.randomUUID();
    var trailer = UUID.randomUUID();
    var tractor = UUID.randomUUID();
    var driver = UUID.randomUUID();
    var at = Instant.parse("2026-10-09T20:00:00Z");
    var r = new Hook(trailer, tractor, driver, at, 7);
    var hash = DatabaseTrailerCouplingTransactions.hookHash(trip, r);
    assertThat(hash).matches("[0-9a-f]{64}");
    assertThat(
            DatabaseTrailerCouplingTransactions.hookHash(
                trip,
                new Hook(trailer, tractor, driver, Instant.parse("2026-10-09T15:00:00-05:00"), 7)))
        .isEqualTo(hash);
    assertThat(DatabaseTrailerCouplingTransactions.hookHash(UUID.randomUUID(), r))
        .isNotEqualTo(hash);
    for (var changed :
        new Hook[] {
          new Hook(UUID.randomUUID(), tractor, driver, at, 7),
          new Hook(trailer, UUID.randomUUID(), driver, at, 7),
          new Hook(trailer, tractor, UUID.randomUUID(), at, 7),
          new Hook(trailer, tractor, driver, at.plusSeconds(1), 7),
          new Hook(trailer, tractor, driver, at, 8)
        }) {
      assertThat(DatabaseTrailerCouplingTransactions.hookHash(trip, changed)).isNotEqualTo(hash);
    }
    assertThat(
            DatabaseTrailerCouplingTransactions.dropHash(
                trip, new Drop(trailer, tractor, driver, at)))
        .isNotEqualTo(hash);
  }

  @Test
  void dropHashBindsTripTrailerStopLocationAndOccurredTime() {
    var trip = UUID.randomUUID();
    var trailer = UUID.randomUUID();
    var stop = UUID.randomUUID();
    var location = UUID.randomUUID();
    var at = Instant.parse("2026-10-09T21:00:00Z");
    var r = new Drop(trailer, stop, location, at);
    var hash = DatabaseTrailerCouplingTransactions.dropHash(trip, r);
    assertThat(hash).matches("[0-9a-f]{64}");
    assertThat(DatabaseTrailerCouplingTransactions.dropHash(trip, r)).isEqualTo(hash);
    assertThat(DatabaseTrailerCouplingTransactions.dropHash(UUID.randomUUID(), r))
        .isNotEqualTo(hash);
    for (var changed :
        new Drop[] {
          new Drop(UUID.randomUUID(), stop, location, at),
          new Drop(trailer, UUID.randomUUID(), location, at),
          new Drop(trailer, stop, UUID.randomUUID(), at),
          new Drop(trailer, stop, location, at.plusNanos(1))
        }) {
      assertThat(DatabaseTrailerCouplingTransactions.dropHash(trip, changed)).isNotEqualTo(hash);
    }
  }
}
