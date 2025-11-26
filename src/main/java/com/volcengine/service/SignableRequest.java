package com.volcengine.service;

import com.volcengine.model.Header;
import com.volcengine.model.NameValuePair;
import lombok.Data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class SignableRequest {
    private String method;
    private String host;
    private String path;
    private List<NameValuePair> queryParams = new ArrayList<>();
    private List<Header> headers = new ArrayList<>();
    private byte[] entity;

    public Header getFirstHeader(String name) {
        if (headers == null) return null;
        for (Header h : headers) {
            if (h.getName().equalsIgnoreCase(name)) {
                return h;
            }
        }
        return null;
    }

    public void setHeader(String name, String value) {
        if (headers == null) headers = new ArrayList<>();
        // remove existing header with same name
        Iterator<Header> it = headers.iterator();
        while (it.hasNext()) {
            Header h = it.next();
            if (h.getName().equalsIgnoreCase(name)) {
                it.remove();
            }
        }
        headers.add(new Header(name, value));
    }

    public List<Header> getAllHeaders() {
        return headers;
    }
}
