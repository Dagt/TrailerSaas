package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import static com.trailersaas.trailers.TrailerMovementRejectedException.Reason.*;

import com.trailersaas.trailers.TrailerMovementRejectedException;
import java.time.Instant;
import java.util.*;

/**
 * Pure transition rules shared by the transaction writer; metadata is built here, never supplied.
 */
final class MovementRules {
  record State(
      LocationState locationState,
      UUID currentLocationId,
      UUID lastKnownLocationId,
      Instant lastSeenAt,
      UUID lastDriverId,
      LoadState loadState) {}

  record Change(
      State state, String type, UUID locationId, String reason, Map<String, Object> metadata) {}

  static void validate(Target t, Context c) {
    require(t != null && t.trailerId() != null && t.expectedVersion() >= 0);
    require(c != null && c.occurredAt() != null && c.provenance() != null);
    var p = c.provenance();
    require(p.actorType() != null && p.sourceType() != null);
    boolean valid =
        switch (p.actorType()) {
          case HUMAN_USER ->
              p.actorUserId() != null
                  && Set.of(SourceType.USER_ACTION, SourceType.MOBILE_AUTOMATION)
                      .contains(p.sourceType());
          case SYSTEM -> p.actorUserId() == null && p.sourceType() == SourceType.SYSTEM_PROCESS;
          case INTEGRATION ->
              p.actorUserId() == null && p.sourceType() == SourceType.EXTERNAL_INTEGRATION;
        };
    require(valid);
    require(c.tripStopId() == null || c.tripId() != null);
  }

  static void invariant(State s) {
    state(
        s.locationState() != null
            && s.loadState() != null
            && (s.locationState() == LocationState.AT_LOCATION
                ? s.currentLocationId() != null
                : s.currentLocationId() == null));
  }

  static Change depart(State s, Context c) {
    invariant(s);
    state(s.locationState() == LocationState.AT_LOCATION);
    return change(
        s,
        c,
        "DEPARTED",
        LocationState.IN_TRANSIT,
        null,
        s.currentLocationId(),
        true,
        s.currentLocationId(),
        null,
        metadata("previousLocationId", s.currentLocationId()));
  }

  static Change arrive(State s, Arrival c) {
    invariant(s);
    state(s.locationState() == LocationState.IN_TRANSIT);
    require(c.locationId() != null);
    var m = metadata("destinationLocationId", c.locationId());
    if (c.arrivalSource() != null) {
      if (c.arrivalSource() == ArrivalSource.GEOFENCE_CONFIRMED)
        require(
            c.context().provenance().actorType() == ActorType.HUMAN_USER
                && c.context().provenance().sourceType() == SourceType.MOBILE_AUTOMATION);
      m.put("arrivalSource", c.arrivalSource().name());
    }
    return change(
        s,
        c.context(),
        "ARRIVED",
        LocationState.AT_LOCATION,
        c.locationId(),
        c.locationId(),
        true,
        c.locationId(),
        null,
        m);
  }

  static Change yard(State s, YardMove c) {
    invariant(s);
    state(s.locationState() == LocationState.AT_LOCATION);
    require(c.fromLocationId() != null && c.toLocationId() != null);
    if (!s.currentLocationId().equals(c.fromLocationId())
        || c.fromLocationId().equals(c.toLocationId())) throw fail(RESOURCE_CONFLICT);
    var m = metadata("previousLocationId", s.currentLocationId());
    m.put("newLocationId", c.toLocationId());
    return change(
        s,
        c.context(),
        "YARD_MOVED",
        LocationState.AT_LOCATION,
        c.toLocationId(),
        c.toLocationId(),
        true,
        c.toLocationId(),
        null,
        m);
  }

  static Change correct(State s, Correction c) {
    invariant(s);
    require(c.locationId() != null);
    reason(c.reason());
    var m = previous(s);
    m.put("previousLastKnownLocationId", s.lastKnownLocationId());
    m.put("newLocationId", c.locationId());
    m.put("physicalObservation", c.physicalObservation());
    if (!c.physicalObservation()) {
      m.put("preservedLastSeenAt", s.lastSeenAt());
      m.put("preservedLastDriverId", s.lastDriverId());
    }
    return change(
        s,
        c.context(),
        "LOCATION_CORRECTED",
        LocationState.AT_LOCATION,
        c.locationId(),
        c.locationId(),
        c.physicalObservation(),
        c.locationId(),
        c.reason(),
        m);
  }

  static Change unknown(State s, MarkUnknown c) {
    invariant(s);
    state(s.locationState() != LocationState.LOCATION_UNKNOWN);
    reason(c.reason());
    var m = previous(s);
    m.put("preservedLastKnownLocationId", s.lastKnownLocationId());
    m.put("preservedLastSeenAt", s.lastSeenAt());
    m.put("preservedLastDriverId", s.lastDriverId());
    return change(
        s,
        c.context(),
        "LOCATION_MARKED_UNKNOWN",
        LocationState.LOCATION_UNKNOWN,
        null,
        s.lastKnownLocationId(),
        false,
        null,
        c.reason(),
        m);
  }

  static Change recover(State s, Recovery c) {
    invariant(s);
    state(s.locationState() == LocationState.LOCATION_UNKNOWN);
    require(c.locationId() != null);
    var m = metadata("previousLocationState", s.locationState().name());
    m.put("recoveredLocationId", c.locationId());
    return change(
        s,
        c.context(),
        "LOCATION_RECOVERED",
        LocationState.AT_LOCATION,
        c.locationId(),
        c.locationId(),
        true,
        c.locationId(),
        null,
        m);
  }

  static Change load(State s, LoadChange c) {
    invariant(s);
    require(c.loadState() != null);
    if (c.context().provenance().sourceType() == SourceType.USER_ACTION) reason(c.reason());
    if (c.reason() != null) reason(c.reason());
    if (s.loadState() == c.loadState()) throw fail(RESOURCE_CONFLICT);
    var m = metadata("previousLoadState", s.loadState().name());
    m.put("newLoadState", c.loadState().name());
    return new Change(
        new State(
            s.locationState(),
            s.currentLocationId(),
            s.lastKnownLocationId(),
            s.lastSeenAt(),
            s.lastDriverId(),
            c.loadState()),
        "LOAD_STATE_CHANGED",
        null,
        c.reason(),
        m);
  }

  static Change coupling(State s, Context c, UUID location, boolean drop) {
    invariant(s);
    if (drop) require(location != null);
    return change(
        s,
        c,
        drop ? "DROPPED" : "HOOKED",
        drop ? LocationState.AT_LOCATION : s.locationState(),
        drop ? location : s.currentLocationId(),
        drop ? location : s.lastKnownLocationId(),
        true,
        drop ? location : s.currentLocationId(),
        null,
        null);
  }

  private static Change change(
      State s,
      Context c,
      String type,
      LocationState locationState,
      UUID current,
      UUID known,
      boolean observe,
      UUID eventLocation,
      String reason,
      Map<String, Object> metadata) {
    var after =
        new State(
            locationState,
            current,
            known,
            observe ? c.occurredAt() : s.lastSeenAt(),
            observe && c.driverId() != null ? c.driverId() : s.lastDriverId(),
            s.loadState());
    invariant(after);
    return new Change(after, type, eventLocation, reason, metadata);
  }

  private static LinkedHashMap<String, Object> previous(State s) {
    var m = metadata("previousLocationState", s.locationState().name());
    m.put("previousLocationId", s.currentLocationId());
    return m;
  }

  private static LinkedHashMap<String, Object> metadata(String k, Object v) {
    var m = new LinkedHashMap<String, Object>();
    m.put(k, v);
    return m;
  }

  static void require(boolean b) {
    if (!b) throw fail(VALIDATION_FAILED);
  }

  private static void reason(String r) {
    require(r != null && !r.isBlank());
  }

  private static void state(boolean b) {
    if (!b) throw fail(INVALID_STATE_TRANSITION);
  }

  static TrailerMovementRejectedException fail(TrailerMovementRejectedException.Reason r) {
    return new TrailerMovementRejectedException(r);
  }
}
