package com.trailersaas.orders;

/** Safe module-boundary rejection with no persistence details. */
public final class OrderPlanningRejectedException extends RuntimeException {
  public enum Reason {
    MISSING,
    STATE,
    CONFLICT
  }

  private final Reason reason;

  public OrderPlanningRejectedException(Reason reason) {
    super("Order planning rejected");
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
