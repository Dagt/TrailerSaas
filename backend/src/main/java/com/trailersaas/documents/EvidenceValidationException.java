package com.trailersaas.documents;

/** Stable safe errors for future workflow controllers; never contains resource IDs or storage data. */
public final class EvidenceValidationException extends RuntimeException {
  public enum Code { EVIDENCE_REQUIRED, UPLOAD_NOT_COMPLETE, VALIDATION_FAILED, RESOURCE_NOT_FOUND, INVALID_STATE_TRANSITION, RESOURCE_CONFLICT }
  private final Code code;
  public EvidenceValidationException(Code code) { super(message(code)); this.code=code; }
  public Code code() { return code; }
  public int status() { return code==Code.VALIDATION_FAILED?400:code==Code.RESOURCE_NOT_FOUND?404:409; }
  private static String message(Code code) {
    return switch(code) {
      case EVIDENCE_REQUIRED -> "The required operational evidence is missing.";
      case UPLOAD_NOT_COMPLETE -> "The required document upload is not complete.";
      case VALIDATION_FAILED -> "The evidence request contains invalid values.";
      case RESOURCE_NOT_FOUND -> "The resource was not found.";
      case INVALID_STATE_TRANSITION -> "The resource cannot perform that action in its current state.";
      case RESOURCE_CONFLICT -> "The evidence cannot be recorded for this operation.";
    };
  }
}
