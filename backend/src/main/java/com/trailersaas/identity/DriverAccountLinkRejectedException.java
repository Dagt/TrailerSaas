package com.trailersaas.identity;

/** Generic target rejection; deliberately exposes no User/membership/role details. */
public final class DriverAccountLinkRejectedException extends RuntimeException {
    public DriverAccountLinkRejectedException() { super("The account cannot be linked."); }
}
