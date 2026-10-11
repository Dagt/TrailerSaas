package com.trailersaas.trailers;

import java.time.Instant;
import java.util.UUID;

/** Caller holds Trip, Driver and Stop parents. Physical methods join that transaction. */
public interface TrailerTripExecution {
  record Coupled(
      UUID id,
      UUID trailerId,
      String operationalNumber,
      UUID tractorId,
      UUID driverId,
      Instant hookedAt,
      long trailerVersion,
      UUID locationId) {}

  Coupled current(UUID tenant, UUID trip);

  void depart(
      UUID tenant,
      UUID trip,
      UUID driver,
      UUID tractor,
      UUID expectedTrailer,
      TrailerMovementProjection.Context context);

  void arrive(
      UUID tenant,
      UUID trip,
      UUID driver,
      UUID tractor,
      UUID location,
      TrailerMovementProjection.Context context);

  boolean serviceSatisfied(
      UUID tenant, UUID trip, UUID stop, String stopType, Instant serviceStartedAt);
}
