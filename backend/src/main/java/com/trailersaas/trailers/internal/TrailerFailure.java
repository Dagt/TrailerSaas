package com.trailersaas.trailers.internal;
import java.sql.SQLException;
final class TrailerFailure extends RuntimeException {
    final int status; final String code;
    private TrailerFailure(int status,String code,String detail) { super(detail);this.status=status;this.code=code; }
    static TrailerFailure invalid() { return new TrailerFailure(400,"VALIDATION_FAILED","The request contains invalid values."); }
    static TrailerFailure missing() { return new TrailerFailure(404,"RESOURCE_NOT_FOUND","The resource was not found."); }
    static TrailerFailure stale() { return new TrailerFailure(409,"STALE_VERSION","The resource changed. Refresh before retrying."); }
    static TrailerFailure conflict() { return new TrailerFailure(409,"RESOURCE_CONFLICT","The requested change conflicts with existing data."); }
    static TrailerFailure scope() { return new TrailerFailure(403,"CUSTOMER_SCOPE_DENIED","The requested action is outside the permitted Customer scope."); }
    static boolean expectedIntegrity(RuntimeException failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if(cause instanceof SQLException sql) { return SetHolder.STATES.contains(sql.getSQLState()==null?"":sql.getSQLState()); }
        }
        return false;
    }
    private static final class SetHolder { static final java.util.Set<String> STATES=java.util.Set.of("23505","23503","23514"); }
}
