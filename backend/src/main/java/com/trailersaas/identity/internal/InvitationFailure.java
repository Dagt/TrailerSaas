package com.trailersaas.identity.internal;

final class InvitationFailure extends RuntimeException {
    final int status;
    final String code;
    private InvitationFailure(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
    static InvitationFailure invalid() { return new InvitationFailure(400, "VALIDATION_FAILED", "The request is invalid."); }
    static InvitationFailure conflict() { return new InvitationFailure(409, "RESOURCE_CONFLICT", "The request conflicts with the current resource state."); }
    static InvitationFailure missing() { return new InvitationFailure(404, "RESOURCE_NOT_FOUND", "The resource was not found."); }
    static InvitationFailure notAcceptable() { return new InvitationFailure(409, "INVITATION_NOT_ACCEPTABLE", "The invitation cannot be accepted."); }
}
