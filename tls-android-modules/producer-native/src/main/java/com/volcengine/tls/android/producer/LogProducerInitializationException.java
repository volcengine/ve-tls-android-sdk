package com.volcengine.tls.android.producer;

/** A native Producer initialization failure, before the triggering log was submitted. */
public final class LogProducerInitializationException extends IllegalStateException {
    public enum Reason {
        NATIVE_SETUP_FAILED,
        INVALID_CONFIG,
        WAL_LOCK_BUSY,
        WAL_LEASE_PENDING,
        WAL_OPEN_FAILED,
        WAL_RECOVERY_FAILED,
        WAL_OPEN_IO_ERROR
    }

    private final Reason reason;

    public LogProducerInitializationException(Reason reason) {
        super("native producer create failed: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** Whether this reason is eligible for a background initialization retry. */
    public boolean isRetryable() {
        return reason == Reason.WAL_LOCK_BUSY || reason == Reason.WAL_LEASE_PENDING
                || reason == Reason.WAL_OPEN_IO_ERROR;
    }
}
