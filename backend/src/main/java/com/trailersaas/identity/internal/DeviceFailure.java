package com.trailersaas.identity.internal;

final class DeviceFailure extends RuntimeException {final int status;DeviceFailure(int status){super("Device registration could not be completed.");this.status=status;}}
