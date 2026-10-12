package com.trailersaas.inspections.internal;

final class ShiftFailure extends RuntimeException {
  final int status;
  final String code;
  private ShiftFailure(int status,String code) {super("The Driver operation could not be completed.");this.status=status;this.code=code;}
  static ShiftFailure invalid() {return new ShiftFailure(400,"VALIDATION_FAILED");}
  static ShiftFailure missing() {return new ShiftFailure(404,"RESOURCE_NOT_FOUND");}
  static ShiftFailure conflict() {return new ShiftFailure(409,"RESOURCE_CONFLICT");}
  static ShiftFailure state() {return new ShiftFailure(409,"INVALID_STATE_TRANSITION");}
}
