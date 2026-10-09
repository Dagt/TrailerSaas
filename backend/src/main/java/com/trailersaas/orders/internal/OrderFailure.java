package com.trailersaas.orders.internal;

import java.sql.SQLException;
import java.util.Set;

final class OrderFailure extends RuntimeException {
    final int status;
    final String code;
    final String field;
    private OrderFailure(int status, String code, String detail, String field) {
        super(detail); this.status = status; this.code = code; this.field = field;
    }
    static OrderFailure invalid(String field) { return new OrderFailure(400, "VALIDATION_FAILED", "The request contains invalid values.", field); }
    static OrderFailure missing() { return new OrderFailure(404, "RESOURCE_NOT_FOUND", "The resource was not found.", null); }
    static OrderFailure conflict() { return new OrderFailure(409, "RESOURCE_CONFLICT", "The requested change conflicts with existing data.", null); }
    static OrderFailure scope() { return new OrderFailure(403, "CUSTOMER_SCOPE_DENIED", "The requested action is outside the permitted Customer scope.", null); }
    static OrderFailure stale() { return new OrderFailure(409, "STALE_VERSION", "The resource changed since it was read.", null); }
    static boolean expectedIntegrity(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { return Set.of("23503", "23505", "23514").contains(sql.getSQLState() == null ? "" : sql.getSQLState()); }
        }
        return false;
    }
}
