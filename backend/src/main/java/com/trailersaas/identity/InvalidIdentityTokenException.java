package com.trailersaas.identity;

/** Deliberately carries neither provider diagnostics nor an exception cause. */
public final class InvalidIdentityTokenException extends RuntimeException {
    public InvalidIdentityTokenException() { super("A valid authentication token is required."); }
}
