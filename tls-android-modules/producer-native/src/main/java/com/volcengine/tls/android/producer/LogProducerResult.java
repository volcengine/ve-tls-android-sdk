package com.volcengine.tls.android.producer;

public final class LogProducerResult {
    public enum Code {
        OK,
        INVALID,
        DROP_ERROR,
        PERSISTENT_ERROR,
        CLOSED,
        TIMEOUT,
        AUTH_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR,
        UNKNOWN_ERROR
    }

    public enum FailureKind {
        NONE,
        VALIDATION,
        PERSISTENCE,
        LIFECYCLE,
        TIMEOUT,
        AUTH,
        HTTP,
        TRANSPORT,
        UNKNOWN
    }

    private final Code code;
    private final String requestId;
    private final String errorCode;
    private final String errorMessage;
    private final int httpCode;
    private final int transportKind;
    private final int transportCode;
    private final long logBytes;
    private final long compressedBytes;
    private final boolean retryable;
    private final long startId;
    private final long endId;

    public LogProducerResult(Code code, String requestId, String errorCode, String errorMessage, int httpCode, int transportKind, int transportCode, long logBytes, long compressedBytes) {
        this(code, requestId, errorCode, errorMessage, httpCode, transportKind,
                transportCode, logBytes, compressedBytes, false, 0, 0);
    }

    public LogProducerResult(
            Code code,
            String requestId,
            String errorCode,
            String errorMessage,
            int httpCode,
            int transportKind,
            int transportCode,
            long logBytes,
            long compressedBytes,
            boolean retryable,
            long startId,
            long endId) {
        this.code = code;
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpCode = httpCode;
        this.transportKind = transportKind;
        this.transportCode = transportCode;
        this.logBytes = logBytes;
        this.compressedBytes = compressedBytes;
        this.retryable = retryable;
        this.startId = startId;
        this.endId = endId;
    }

    public Code getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public int getTransportKind() {
        return transportKind;
    }

    public int getTransportCode() {
        return transportCode;
    }

    public long getLogBytes() {
        return logBytes;
    }

    public long getCompressedBytes() {
        return compressedBytes;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long getStartId() {
        return startId;
    }

    public long getEndId() {
        return endId;
    }

    public boolean hasLogIdRange() {
        return startId > 0 && endId >= startId;
    }

    public boolean isSuccess() {
        return code == Code.OK;
    }

    public boolean hasHttpFailure() {
        return httpCode > 0 && httpCode != 200;
    }

    public boolean hasTransportFailure() {
        return transportKind != 0 || transportCode != 0;
    }

    public String getBestErrorMessage() {
        if (hasText(errorMessage)) {
            return errorMessage;
        }
        return errorCode;
    }

    public FailureKind getFailureKind() {
        switch (code) {
            case OK:
                return FailureKind.NONE;
            case INVALID:
                return FailureKind.VALIDATION;
            case PERSISTENT_ERROR:
                return FailureKind.PERSISTENCE;
            case CLOSED:
                return FailureKind.LIFECYCLE;
            case TIMEOUT:
                return FailureKind.TIMEOUT;
            case AUTH_ERROR:
                return FailureKind.AUTH;
            case NETWORK_ERROR:
                return FailureKind.TRANSPORT;
            case SERVER_ERROR:
                return FailureKind.HTTP;
            default:
                return FailureKind.UNKNOWN;
        }
    }

    public String getFailureSummary() {
        if (isSuccess()) {
            return "kind=NONE code=OK";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("kind=").append(getFailureKind()).append(" code=").append(code);
        if (hasHttpFailure()) {
            sb.append(" http=").append(httpCode);
        }
        if (hasTransportFailure()) {
            sb.append(" transport=").append(transportKind).append('/').append(transportCode);
        }
        if (hasText(errorCode)) {
            sb.append(" errorCode=").append(errorCode);
        }
        String bestMessage = getBestErrorMessage();
        if (hasText(bestMessage) && !bestMessage.equals(errorCode)) {
            sb.append(" message=").append(bestMessage);
        }
        if (hasText(requestId)) {
            sb.append(" reqId=").append(requestId);
        }
        return sb.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
