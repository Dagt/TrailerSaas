package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import static org.assertj.core.api.Assertions.*;

import com.trailersaas.trailers.TrailerMovementRejectedException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MovementRulesTests {
  final UUID a = UUID.randomUUID(),
      b = UUID.randomUUID(),
      driver = UUID.randomUUID(),
      user = UUID.randomUUID();
  final Instant at = Instant.parse("2026-10-01T12:00:00Z"), old = at.minusSeconds(600);
  final Target target = new Target(UUID.randomUUID(), 0);

  Context context() {
    return new Context(
        at,
        new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, user),
        driver,
        null,
        null,
        null,
        null);
  }

  MovementRules.State state(LocationState l) {
    return new MovementRules.State(
        l, l == LocationState.AT_LOCATION ? a : null, a, old, driver, LoadState.UNKNOWN);
  }

  static Stream<Arguments> matrix() {
    return Arrays.stream(LocationState.values())
        .flatMap(
            s ->
                Stream.of("DEPART", "ARRIVE", "YARD", "CORRECT", "UNKNOWN", "RECOVER")
                    .map(op -> Arguments.of(s, op)));
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void completeLocationTransitionMatrix(LocationState source, String op) {
    var s = state(source);
    var c = context();
    boolean valid =
        switch (op) {
          case "DEPART", "YARD" -> source == LocationState.AT_LOCATION;
          case "ARRIVE" -> source == LocationState.IN_TRANSIT;
          case "UNKNOWN" -> source != LocationState.LOCATION_UNKNOWN;
          case "RECOVER" -> source == LocationState.LOCATION_UNKNOWN;
          default -> true;
        };
    java.util.function.Supplier<MovementRules.Change> action =
        () ->
            switch (op) {
              case "DEPART" -> MovementRules.depart(s, c);
              case "ARRIVE" ->
                  MovementRules.arrive(s, new Arrival(target, c, b, ArrivalSource.MANUAL));
              case "YARD" -> MovementRules.yard(s, new YardMove(target, c, a, b));
              case "CORRECT" ->
                  MovementRules.correct(
                      s, new Correction(target, c, b, "Verified location", false));
              case "UNKNOWN" ->
                  MovementRules.unknown(s, new MarkUnknown(target, c, "Cannot locate"));
              default -> MovementRules.recover(s, new Recovery(target, c, b));
            };
    if (!valid) {
      assertThatThrownBy(action::get)
          .isInstanceOf(TrailerMovementRejectedException.class)
          .hasFieldOrPropertyWithValue(
              "reason", TrailerMovementRejectedException.Reason.INVALID_STATE_TRANSITION);
      return;
    }
    var result = action.get().state();
    MovementRules.invariant(result);
    assertThat(result.loadState()).isEqualTo(LoadState.UNKNOWN);
    assertThat(result.lastDriverId()).isEqualTo(driver);
    assertThat(result.lastKnownLocationId())
        .isEqualTo(Set.of("DEPART", "UNKNOWN").contains(op) ? a : b);
    assertThat(result.lastSeenAt()).isEqualTo(Set.of("CORRECT", "UNKNOWN").contains(op) ? old : at);
  }

  static Stream<Arguments> loads() {
    return Arrays.stream(LoadState.values())
        .flatMap(a -> Arrays.stream(LoadState.values()).map(b -> Arguments.of(a, b)));
  }

  @ParameterizedTest
  @MethodSource("loads")
  void sixLoadTransitionsAndSameStateConflicts(LoadState from, LoadState to) {
    var s = new MovementRules.State(LocationState.AT_LOCATION, a, a, old, driver, from);
    var c = new LoadChange(target, context(), to, "Load confirmed");
    if (from == to) {
      assertThatThrownBy(() -> MovementRules.load(s, c))
          .isInstanceOf(TrailerMovementRejectedException.class);
      return;
    }
    var r = MovementRules.load(s, c);
    assertThat(r.state())
        .isEqualTo(new MovementRules.State(s.locationState(), a, a, old, driver, to));
    assertThat(r.metadata())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("previousLoadState", from.name(), "newLoadState", to.name()));
  }

  static Stream<Arguments> provenance() {
    return Arrays.stream(ActorType.values())
        .flatMap(a -> Arrays.stream(SourceType.values()).map(s -> Arguments.of(a, s)));
  }

  @ParameterizedTest
  @MethodSource("provenance")
  void actorAndCausalSourceAreSeparateClosedCatalogs(ActorType actor, SourceType source) {
    var c =
        new Context(
            at,
            new Provenance(actor, source, actor == ActorType.HUMAN_USER ? user : null),
            null,
            null,
            null,
            null,
            null);
    boolean valid =
        actor == ActorType.HUMAN_USER
                && (source == SourceType.USER_ACTION || source == SourceType.MOBILE_AUTOMATION)
            || actor == ActorType.SYSTEM && source == SourceType.SYSTEM_PROCESS
            || actor == ActorType.INTEGRATION && source == SourceType.EXTERNAL_INTEGRATION;
    if (valid) assertThatCode(() -> MovementRules.validate(target, c)).doesNotThrowAnyException();
    else
      assertThatThrownBy(() -> MovementRules.validate(target, c))
          .isInstanceOf(TrailerMovementRejectedException.class);
  }

  @Test
  void physicalObservationIsExplicitAndMissingDriverPreservesLastDriver() {
    var c = new Context(at, context().provenance(), null, null, null, null, null);
    var r =
        MovementRules.correct(
            state(LocationState.LOCATION_UNKNOWN), new Correction(target, c, b, "Observed", true));
    assertThat(r.state().lastSeenAt()).isEqualTo(at);
    assertThat(r.state().lastDriverId()).isEqualTo(driver);
    var mark =
        MovementRules.unknown(
            state(LocationState.AT_LOCATION), new MarkUnknown(target, context(), "Lost"));
    assertThat(mark.metadata())
        .containsEntry("preservedLastSeenAt", old.toString())
        .containsEntry("preservedLastDriverId", driver)
        .containsEntry("preservedLastKnownLocationId", a);
  }

  @Test
  void invalidInputsCannotBecomeFacts() {
    assertThatThrownBy(
            () ->
                MovementRules.validate(
                    target,
                    new Context(
                        at,
                        new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, null),
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(() -> MovementRules.validate(new Target(target.trailerId(), -1), context()))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(
            () ->
                MovementRules.correct(
                    state(LocationState.AT_LOCATION),
                    new Correction(target, context(), b, " ", false)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(
            () ->
                MovementRules.unknown(
                    state(LocationState.AT_LOCATION), new MarkUnknown(target, context(), null)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(
            () ->
                MovementRules.load(
                    state(LocationState.AT_LOCATION),
                    new LoadChange(target, context(), LoadState.EMPTY, null)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(
            () ->
                MovementRules.yard(
                    state(LocationState.AT_LOCATION), new YardMove(target, context(), b, a)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    assertThatThrownBy(
            () ->
                MovementRules.invariant(
                    new MovementRules.State(
                        LocationState.IN_TRANSIT, a, a, old, driver, LoadState.UNKNOWN)))
        .isInstanceOf(TrailerMovementRejectedException.class);
  }

  @Test
  void geofenceNeedsHumanConfirmationAndAutomationProvenance() {
    assertThatThrownBy(
            () ->
                MovementRules.arrive(
                    state(LocationState.IN_TRANSIT),
                    new Arrival(target, context(), b, ArrivalSource.GEOFENCE_CONFIRMED)))
        .isInstanceOf(TrailerMovementRejectedException.class);
    var c =
        new Context(
            at,
            new Provenance(ActorType.HUMAN_USER, SourceType.MOBILE_AUTOMATION, user),
            null,
            null,
            null,
            null,
            null);
    assertThat(
            MovementRules.arrive(
                    state(LocationState.IN_TRANSIT),
                    new Arrival(target, c, b, ArrivalSource.GEOFENCE_CONFIRMED))
                .metadata())
        .containsEntry("arrivalSource", "GEOFENCE_CONFIRMED");
  }

  @Test
  void hookIsObservationNotDepartureAndDropDoesNotChangeLoad() {
    var s = state(LocationState.AT_LOCATION);
    assertThat(MovementRules.coupling(s, context(), null, false).state())
        .isEqualTo(
            new MovementRules.State(
                LocationState.AT_LOCATION, a, a, at, driver, LoadState.UNKNOWN));
    assertThat(MovementRules.coupling(s, context(), b, true).state())
        .isEqualTo(
            new MovementRules.State(
                LocationState.AT_LOCATION, b, b, at, driver, LoadState.UNKNOWN));
  }
}
