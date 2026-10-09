package com.trailersaas.trips.internal;

import java.sql.SQLException;
import java.util.Set;

final class TripFailure extends RuntimeException {
  final int status;
  final String code;
  final String field;

  private TripFailure(int status, String code, String detail, String field) {
    super(detail);
    this.status = status;
    this.code = code;
    this.field = field;
  }

  static TripFailure invalid(String field) {
    return new TripFailure(400, "VALIDATION_FAILED", "The request contains invalid values.", field);
  }

  static TripFailure missing() {
    return new TripFailure(404, "RESOURCE_NOT_FOUND", "The resource was not found.", null);
  }

  static TripFailure conflict() {
    return new TripFailure(
        409, "RESOURCE_CONFLICT", "The requested change conflicts with existing data.", null);
  }

  static TripFailure state() {
    return new TripFailure(
        409,
        "INVALID_STATE_TRANSITION",
        "The Order cannot perform that action in its current state.",
        null);
  }

  static TripFailure tripState() {
    return new TripFailure(
        409,
        "INVALID_STATE_TRANSITION",
        "The Trip cannot perform that action in its current state.",
        null);
  }

  static TripFailure stale() {
    return new TripFailure(
        409, "STALE_VERSION", "The resource changed. Reload it before trying again.", null);
  }

  static boolean integrity(Throwable failure) {
    for (var c = failure; c != null; c = c.getCause()) {
      if (c instanceof SQLException sql) {
        return Set.of("23503", "23505", "23514")
            .contains(sql.getSQLState() == null ? "" : sql.getSQLState());
      }
    }
    return false;
  }
}
