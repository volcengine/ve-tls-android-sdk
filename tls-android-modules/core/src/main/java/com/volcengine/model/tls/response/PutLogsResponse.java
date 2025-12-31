package com.volcengine.model.tls.response;

import com.volcengine.model.Header;
import com.volcengine.model.tls.Const;

public class PutLogsResponse {
    private Header[] headers;
    public PutLogsResponse() {}
    public PutLogsResponse(Header[] headers) { this.headers = headers; }
    public Header[] getHeaders() { return headers; }
    public String getRequestId() {
        if (headers == null) return null;
        for (Header h : headers) {
            if (Const.X_TLS_REQUESTID.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return null;
    }
}
