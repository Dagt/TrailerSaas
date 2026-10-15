package com.trailersaas.notifications.internal;

final class AlertFailure extends RuntimeException {
 final int status; final String code;
 AlertFailure(int status,String code,String detail){super(detail);this.status=status;this.code=code;}
 static AlertFailure invalid(){return new AlertFailure(400,"VALIDATION_FAILED","The request contains invalid values.");}
 static AlertFailure missing(){return new AlertFailure(404,"RESOURCE_NOT_FOUND","The resource is not available.");}
 static AlertFailure stale(){return new AlertFailure(409,"STALE_VERSION","The Alert changed. Refresh and review it.");}
 static AlertFailure state(){return new AlertFailure(409,"INVALID_STATE_TRANSITION","The current condition does not allow this transition.");}
}
