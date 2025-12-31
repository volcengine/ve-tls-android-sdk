package com.volcengine.model;

import java.util.Date;
import java.util.List;

public class RequestParam {
    private Boolean isSignUrl;
    private byte[] body;
    private String method;
    private Date date;
    private String path;
    private String host;
    private List<NameValuePair> queryList;
    private List<Header> headers;

    public Boolean getIsSignUrl() { return isSignUrl; }
    public void setIsSignUrl(Boolean isSignUrl) { this.isSignUrl = isSignUrl; }
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public List<NameValuePair> getQueryList() { return queryList; }
    public void setQueryList(List<NameValuePair> queryList) { this.queryList = queryList; }
    public List<Header> getHeaders() { return headers; }
    public void setHeaders(List<Header> headers) { this.headers = headers; }
}
