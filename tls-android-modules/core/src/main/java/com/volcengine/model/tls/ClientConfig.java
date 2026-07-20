package com.volcengine.model.tls;

import com.volcengine.model.Credentials;
import com.volcengine.model.Header;
import com.volcengine.model.ServiceInfo;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientConfig {
    public static final String DEFAULT_API_VERSION = "0.3.0";
    public static final String DEFAULT_SERVICE = "TLS";
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10 * 1000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 50 * 1000;
    public static final int DEFAULT_RETRY_COUNT = 5;
    String endpoint;
    String accessKeyId;
    String accessKeySecret;
    String securityToken;
    String region;
    String apiVersion;
    int retryCount;
    int socketTimeout;
    int connectionTimeout;

    public ClientConfig(String endPoint, String region, String accessKeyId, String accessKeySecret,
                        String securityToken) {
        this(endPoint, region, accessKeyId, accessKeySecret, securityToken, DEFAULT_API_VERSION);
    }

    public ClientConfig(String endPoint, String region, String accessKeyId, String accessKeySecret) {
        this(endPoint, region, accessKeyId, accessKeySecret, null);
    }

    public ClientConfig(String endPoint, String region, String accessKeyId, String accessKeySecret,
                        String securityToken, String apiVersion) {
        this.endpoint = endPoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.securityToken = securityToken;
        this.region = region;
        this.apiVersion = apiVersion;
        this.retryCount = DEFAULT_RETRY_COUNT;
        this.socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    }

    public static ServiceInfo initServiceInfo(ClientConfig config) {
        String endPoint = config.getEndpoint();
        String schema = endPoint.toLowerCase().startsWith(com.volcengine.util.Const.HTTPS) ?
                com.volcengine.util.Const.HTTPS : com.volcengine.util.Const.HTTP;
        String hostOnly = null;
        int port = 0;
        try {
            java.net.URI uri = java.net.URI.create(endPoint);
            hostOnly = uri.getHost();
            int p = uri.getPort();
            port = p < 0 ? 0 : p;
        } catch (Exception ignore) {
            String[] url = endPoint.split("\\/\\/");
            String h = url.length > 1 ? url[1] : endPoint;
            int idx = h.indexOf('/');
            if (idx >= 0) { h = h.substring(0, idx); }
            int colon = h.indexOf(':');
            if (colon >= 0) {
                try { port = Integer.parseInt(h.substring(colon + 1)); } catch (Exception ignored) { port = 0; }
                hostOnly = h.substring(0, colon);
            } else { hostOnly = h; }
        }
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put(com.volcengine.util.Const.CONNECTION_TIMEOUT, config.getConnectionTimeout());
        params.put(com.volcengine.util.Const.SOCKET_TIMEOUT, config.getSocketTimeout());
        params.put(com.volcengine.util.Const.Scheme, schema);
        params.put(com.volcengine.util.Const.Host, hostOnly);
        params.put(com.volcengine.util.Const.Port, port);
        java.util.List<Header> headers = new java.util.ArrayList<>();
        headers.add(new Header(com.volcengine.util.Const.ACCEPT, com.volcengine.util.Const.ACCEPT_ALL));
        headers.add(new Header(com.volcengine.util.Const.ACCEPT_ENCODING, com.volcengine.util.Const.GZIP_DEFLATE_BR));
        headers.add(new Header(com.volcengine.util.Const.REGION, config.getRegion()));
        params.put(com.volcengine.util.Const.Header, headers);
        params.put(com.volcengine.util.Const.Credentials, new Credentials(config.getRegion(), DEFAULT_SERVICE));
        return new ServiceInfo(params);
    }

    public void resetAccessKeyToken(String accessKey, String secretKey, String securityToken) {
        setAccessKeyId(accessKey);
        setAccessKeySecret(secretKey);
        setSecurityToken(securityToken);
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getSecurityToken() { return securityToken; }
    public void setSecurityToken(String securityToken) { this.securityToken = securityToken; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getSocketTimeout() { return socketTimeout; }
    public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
}
