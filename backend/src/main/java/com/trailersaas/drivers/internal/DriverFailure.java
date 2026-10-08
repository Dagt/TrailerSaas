package com.trailersaas.drivers.internal;

import java.sql.SQLException;

final class DriverFailure extends RuntimeException {
    final int status;
    final String code;
    private DriverFailure(int status, String code, String detail) { super(detail); this.status=status; this.code=code; }
    static DriverFailure invalid() { return new DriverFailure(400,"VALIDATION_FAILED","The request contains invalid values."); }
    static DriverFailure missing() { return new DriverFailure(404,"RESOURCE_NOT_FOUND","The resource was not found."); }
    static DriverFailure stale() { return new DriverFailure(409,"STALE_VERSION","The resource changed. Refresh before retrying."); }
    static DriverFailure conflict() { return new DriverFailure(409,"RESOURCE_CONFLICT","The requested change conflicts with existing data."); }
    static boolean expectedIntegrity(RuntimeException failure) {
        for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return "23505".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState()) || "23514".equals(sql.getSQLState());
            }
        }
        return false;
    }
}
