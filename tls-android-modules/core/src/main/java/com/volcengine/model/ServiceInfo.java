package com.volcengine.model;

import com.volcengine.util.Const;

import java.util.List;
import java.util.Map;

import static com.volcengine.util.Const.*;

public class ServiceInfo {
    private int connectionTimeout;
    private int socketTimeout;
    private String scheme;
    private String host;
    private int port;
    private List<Header> header;
    private Credentials credentials;

    public ServiceInfo(Map<String, Object> params) {
        this.connectionTimeout = ((Integer) params.get(CONNECTION_TIMEOUT)) == null ? 0 : (Integer) params.get(CONNECTION_TIMEOUT);
        this.socketTimeout = ((Integer) params.get(SOCKET_TIMEOUT)) == null ? 0 : (Integer) params.get(SOCKET_TIMEOUT);
        this.scheme = ((String) params.get(Scheme)) == null ? "http" : ((String) params.get(Scheme));
        this.host = (String) params.get(Host);
        Integer p = (Integer) params.get(Const.Port);
        this.port = p == null ? 0 : p;
        this.header = (List<Header>) params.get(Const.Header);
        this.credentials = (Credentials) params.get(Const.Credentials);
    }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public int getSocketTimeout() { return socketTimeout; }
    public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public List<Header> getHeader() { return header; }
    public void setHeader(List<Header> header) { this.header = header; }
    public Credentials getCredentials() { return credentials; }
    public void setCredentials(Credentials credentials) { this.credentials = credentials; }
}
