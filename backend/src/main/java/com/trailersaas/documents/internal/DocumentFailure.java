package com.trailersaas.documents.internal;

final class DocumentFailure extends RuntimeException {
  final int status;
  final String code;
  DocumentFailure(int status,String code,String message) { super(message); this.status=status; this.code=code; }
  static DocumentFailure invalid() { return new DocumentFailure(400,"VALIDATION_FAILED","The document request contains invalid values."); }
  static DocumentFailure missing() { return new DocumentFailure(404,"RESOURCE_NOT_FOUND","The resource was not found."); }
  static DocumentFailure conflict(String code) { return new DocumentFailure(409,code,"The document cannot complete that operation."); }
  static DocumentFailure storage() { return new DocumentFailure(503,"OBJECT_STORAGE_UNAVAILABLE","Document storage is temporarily unavailable."); }
}
