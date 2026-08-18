package com.volcengine.model.tls.response;

import com.volcengine.model.Header;
import com.volcengine.model.tls.Const;

public class PutLogsResponse {
    private Header[] headers;
    private int httpCode = -1;
    public PutLogsResponse() {}
    public PutLogsResponse(Header[] headers) { this(headers, -1); }
    public PutLogsResponse(Header[] headers, int httpCode) {
        this.headers = headers;
        this.httpCode = httpCode;
    }
    public Header[] getHeaders() { return headers; }
    public int getHttpCode() { return httpCode; }
    public String getRequestId() {
        if (headers == null) return null;
        for (Header h : headers) {
            if (Const.X_TLS_REQUESTID.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return null;
    }
}
