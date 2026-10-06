package com.trailersaas.identity.internal;

final class AuthorizationDeniedException extends RuntimeException {
    enum Reason {
        TENANT_ACCESS_DENIED("Access to the requested Tenant is denied."),
        CAPABILITY_DENIED("The requested action is not permitted."),
        CUSTOMER_SCOPE_DENIED("The requested action is outside the permitted Customer scope.");

        private final String detail;

        Reason(String detail) { this.detail = detail; }
        String detail() { return detail; }
    }

    private final Reason reason;

    AuthorizationDeniedException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    Reason reason() { return reason; }
}
