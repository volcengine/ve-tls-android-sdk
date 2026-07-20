package com.volcengine.model;

import com.volcengine.util.Const;

import java.util.List;
import java.util.Map;

public class ApiInfo {

    private String method;
    private String path;
    private List<NameValuePair> query;
    private List<NameValuePair> form;
    private int connectionTimeout;
    private int socketTimeout;
    private List<Header> header;

    public ApiInfo(Map<String, Object> params) {
        this.method = (String) params.get(Const.Method);
        this.path = (String) params.get(Const.Path);
        this.query = (List<NameValuePair>) params.get(Const.Query);
        this.form = (List<NameValuePair>) params.get(Const.Form);
        this.connectionTimeout = params.get(Const.CONNECTION_TIMEOUT) == null ? 0 : (Integer) params.get(Const.CONNECTION_TIMEOUT);
        this.socketTimeout = params.get(Const.SOCKET_TIMEOUT) == null ? 0 : (Integer) params.get(Const.SOCKET_TIMEOUT);
        this.header = (List<Header>) params.get(Const.Header);
    }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public List<NameValuePair> getQuery() { return query; }
    public void setQuery(List<NameValuePair> query) { this.query = query; }
    public List<NameValuePair> getForm() { return form; }
    public void setForm(List<NameValuePair> form) { this.form = form; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public int getSocketTimeout() { return socketTimeout; }
    public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }
    public List<Header> getHeader() { return header; }
    public void setHeader(List<Header> header) { this.header = header; }
}
