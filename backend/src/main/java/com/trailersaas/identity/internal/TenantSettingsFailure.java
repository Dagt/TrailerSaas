package com.trailersaas.identity.internal;

final class TenantSettingsFailure extends RuntimeException {
    final int status; final String code; final String field;
    private TenantSettingsFailure(int status, String code, String field) {
        super(code); this.status = status; this.code = code; this.field = field;
    }
    static TenantSettingsFailure invalid(String field) { return new TenantSettingsFailure(400, "VALIDATION_FAILED", field); }
    static TenantSettingsFailure stale() { return new TenantSettingsFailure(409, "STALE_VERSION", null); }
    static TenantSettingsFailure missing() { return new TenantSettingsFailure(404, "RESOURCE_NOT_FOUND", null); }
}
