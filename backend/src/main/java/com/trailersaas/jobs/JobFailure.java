package com.trailersaas.jobs;

/** Only these bounded codes reach last_error/logs; never persist exception messages. */
public final class JobFailure extends RuntimeException {
    public enum Code { UNKNOWN_JOB_TYPE, UNSUPPORTED_PAYLOAD_VERSION, INVALID_PAYLOAD, INVALID_SCOPE,
        HANDLER_RETRYABLE, HANDLER_PERMANENT, HANDLER_TIMEOUT, HANDLER_FAILURE, LEASE_EXPIRED, LEASE_EXHAUSTED }
    private final Code code;
    private final boolean retryable;
    public JobFailure(Code code, boolean retryable) { super(code.name()); this.code=code; this.retryable=retryable; }
    public Code code() { return code; }
    public boolean retryable() { return retryable; }
}
