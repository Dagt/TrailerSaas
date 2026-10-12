package com.trailersaas.trips;

/** Safe public boundary for synchronous operational integrations. */
public final class DriverOperationRejectedException extends RuntimeException {
  private final int status;
  private final String code;
  public DriverOperationRejectedException(int status,String code) {
    super("The Driver operation could not be completed.");this.status=status;this.code=code;
  }
  public int status(){return status;}
  public String code(){return code;}
}
