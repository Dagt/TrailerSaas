package com.trailersaas.customers.internal;

final class MasterDataFailure extends RuntimeException {
    final String code;
    final int status;
    final String detail;
    final String field;
    private MasterDataFailure(String code, int status, String detail, String field) {
        super(code);
        this.code = code; this.status = status; this.detail = detail; this.field = field;
    }
    static MasterDataFailure invalid(String field) {
        return new MasterDataFailure("VALIDATION_FAILED", 400, "The request contains invalid fields.", field);
    }
    static MasterDataFailure tenantDenied() {
        return new MasterDataFailure("TENANT_ACCESS_DENIED", 403, "Access to the requested Tenant is denied.", null);
    }
    static MasterDataFailure missing() {
        return new MasterDataFailure("RESOURCE_NOT_FOUND", 404, "The requested resource was not found.", null);
    }
    static MasterDataFailure conflict() {
        return new MasterDataFailure("RESOURCE_CONFLICT", 409, "The request conflicts with existing master data.", null);
    }
    static MasterDataFailure stale() {
        return new MasterDataFailure("STALE_VERSION", 409, "The resource has changed. Reload it before updating.", null);
    }
}
