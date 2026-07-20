package com.volcengine.model.tls.exception;

public class LogException extends Exception {
    private int httpCode;
    private String code;
    private String requestId;

    
    public LogException(int httpCode, String code, String message, String requestId) { super(message); this.httpCode = httpCode; this.code = code; this.requestId = requestId; }
    public LogException(String code, String message, String requestId) { super(message); this.code = code; this.requestId = requestId; }
    public int getHttpCode() { return httpCode; }
    public String getCode() { return code; }
    public String getRequestId() { return requestId; }
    public String getErrorCode() { return code; }
    public String getErrorMessage() { return getMessage(); }
}
