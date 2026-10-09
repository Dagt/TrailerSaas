package com.trailersaas.trailers;

/** Safe bounded command failure, never database messages or hidden resource metadata. */
public final class CouplingCommandRejectedException extends RuntimeException {
  public enum Reason {
    MISSING,
    STATE,
    STALE,
    CONFLICT,
    TRAILER_COUPLED,
    TRACTOR_COUPLED,
    SUBSTITUTION,
    KEY_REUSED
  }

  private final Reason reason;

  public CouplingCommandRejectedException(Reason reason) {
    super("The physical command cannot be performed.");
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
