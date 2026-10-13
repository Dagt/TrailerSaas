package com.trailersaas.yard.internal;

final class YardFailure extends RuntimeException {
  final int status;
  final String code;
  final YardDtos.Trailer currentTrailer;
  YardFailure(int status, String code, String detail) { this(status, code, detail, null); }
  YardFailure(int status, String code, String detail, YardDtos.Trailer current) {
    super(detail); this.status=status; this.code=code; this.currentTrailer=current;
  }
  static YardFailure invalid() { return new YardFailure(400,"VALIDATION_FAILED","The request contains invalid values."); }
  static YardFailure missing() { return new YardFailure(404,"RESOURCE_NOT_FOUND","The resource was not found."); }
  static YardFailure conflict() { return new YardFailure(409,"RESOURCE_CONFLICT","The Yard context changed. Refresh and review the move."); }
}
