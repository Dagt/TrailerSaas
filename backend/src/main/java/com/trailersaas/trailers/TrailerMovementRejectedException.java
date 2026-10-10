package com.trailersaas.trailers;

/** Stable, value-free domain rejection for future command adapters; never includes SQL or IDs. */
public final class TrailerMovementRejectedException extends RuntimeException {
  public enum Reason {
    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    STALE_VERSION,
    INVALID_STATE_TRANSITION,
    RESOURCE_CONFLICT
  }

  private final Reason reason;

  public TrailerMovementRejectedException(Reason reason) {
    super("The Trailer movement cannot be applied.");
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
