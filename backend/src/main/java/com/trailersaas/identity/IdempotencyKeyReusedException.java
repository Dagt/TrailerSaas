package com.trailersaas.identity;

public final class IdempotencyKeyReusedException extends RuntimeException {
  public IdempotencyKeyReusedException() { super("The idempotency key was already used for another operation."); }
}
