package com.trailersaas.tractors.internal;

import java.sql.SQLException;

final class TractorFailure extends RuntimeException {
    final int status;
    final String code;
    private TractorFailure(int status, String code, String detail) { super(detail); this.status=status; this.code=code; }
    static TractorFailure invalid() { return new TractorFailure(400,"VALIDATION_FAILED","The request contains invalid values."); }
    static TractorFailure missing() { return new TractorFailure(404,"RESOURCE_NOT_FOUND","The resource was not found."); }
    static TractorFailure stale() { return new TractorFailure(409,"STALE_VERSION","The resource changed. Refresh before retrying."); }
    static TractorFailure conflict() { return new TractorFailure(409,"RESOURCE_CONFLICT","The requested change conflicts with existing data."); }
    static boolean expectedIntegrity(RuntimeException failure) {
        for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return "23505".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState()) || "23514".equals(sql.getSQLState());
            }
        }
        return false;
    }
}
