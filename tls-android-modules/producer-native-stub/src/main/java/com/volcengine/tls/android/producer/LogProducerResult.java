package com.volcengine.tls.android.producer;

public final class LogProducerResult {
    public enum Code {
        OK, INVALID, DROP_ERROR, PERSISTENT_ERROR, CLOSED, TIMEOUT, AUTH_ERROR, NETWORK_ERROR, SERVER_ERROR, UNKNOWN_ERROR
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

    public LogProducerResult(Code code, String requestId, String errorCode, String errorMessage, int httpCode, int transportKind, int transportCode, long logBytes, long compressedBytes) {
        this.code = code;
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpCode = httpCode;
        this.transportKind = transportKind;
        this.transportCode = transportCode;
        this.logBytes = logBytes;
        this.compressedBytes = compressedBytes;
    }

    public Code getCode() { return code; }
    public String getRequestId() { return requestId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getHttpCode() { return httpCode; }
    public int getTransportKind() { return transportKind; }
    public int getTransportCode() { return transportCode; }
    public long getLogBytes() { return logBytes; }
    public long getCompressedBytes() { return compressedBytes; }
    public boolean isSuccess() { return code == Code.OK; }
}
