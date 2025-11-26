package com.volcengine.model;

import com.volcengine.util.Const;
import lombok.Data;

import java.util.List;
import java.util.Map;

import static com.volcengine.util.Const.*;


@Data
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
}



