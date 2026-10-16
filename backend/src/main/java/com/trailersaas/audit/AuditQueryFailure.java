package com.trailersaas.audit;

/** Safe read failures; writer/storage failures intentionally propagate and roll back the owner. */
public final class AuditQueryFailure extends RuntimeException {
    public final int status;
    public final String code;
    private AuditQueryFailure(int status,String code) { super(code); this.status=status;this.code=code; }
    public static AuditQueryFailure invalid() { return new AuditQueryFailure(400,"VALIDATION_FAILED"); }
    public static AuditQueryFailure missing() { return new AuditQueryFailure(404,"RESOURCE_NOT_FOUND"); }
}
