package com.volcengine.model.tls.producer;

public class Attempt {
    private boolean success;
    private String requestId;
    private String errorCode;
    private String errorMessage;
    private int httpCode = -1;

    public Attempt() {}
    public Attempt(boolean success, String requestId, String errorCode, String errorMessage) {
        this.success = success; this.requestId = requestId; this.errorCode = errorCode; this.errorMessage = errorMessage;
    }
    public Attempt(boolean success, String requestId, String errorCode, String errorMessage, int httpCode) {
        this(success, requestId, errorCode, errorMessage); this.httpCode = httpCode;
    }
    public boolean isSuccess() { return success; }
    public String getRequestId() { return requestId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public int getHttpCode() { return httpCode; }
}
