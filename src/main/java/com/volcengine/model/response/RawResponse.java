package com.volcengine.model.response;

import com.volcengine.model.Header;
import lombok.Data;
import okhttp3.Headers;

import java.util.Set;

@Data
public class RawResponse {

    private byte[] data;
    private int code;
    private Exception exception;
    //response header and origin http response code
    private Header[] headers;
    private int httpCode;

    public RawResponse() {

    }

    public RawResponse(byte[] data, int code, Exception e) {
        this.data = data;
        this.code = code;
        this.exception = e;
    }

    public RawResponse(byte[] data, int code, Exception exception, Headers headers) {
        this.data = data;
        this.code = code;
        this.exception = exception;
        this.setHeaders(headers);
    }

    public RawResponse(byte[] data, int code, Exception exception, Headers headers, int httpCode) {
        this.data = data;
        this.code = code;
        this.exception = exception;
        this.setHeaders(headers);
        this.httpCode = httpCode;
    }

    public String getFirstHeader(String key) {
        if (key != null && headers != null) {
            for (Header header : headers) {
                if (header.getName().equalsIgnoreCase(key)) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    public void setHeaders(Headers headers) {
        if (headers == null || headers.size() == 0) {
            return;
        }
        this.headers = new Header[headers.size()];
        Set<String> names = headers.names();
        int index = 0;
        for (String name : names) {
            this.headers[index] = new Header(name, headers.get(name));
            index++;
        }
    }
}
