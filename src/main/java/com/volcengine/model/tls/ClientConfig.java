package com.volcengine.model.tls;

import com.volcengine.model.Credentials;
import com.volcengine.model.Header;
import com.volcengine.model.ServiceInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;

import static com.volcengine.model.tls.Const.API_VERSION_V_0_2_0;
import static com.volcengine.model.tls.Const.TLS;

@Data
public class ClientConfig {
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
        this(endPoint, region, accessKeyId, accessKeySecret, securityToken, API_VERSION_V_0_2_0);
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
        String endPoint = config.getEndpoint().toLowerCase();
        String[] url = endPoint.split("\\/\\/");
        String schema = endPoint.startsWith(com.volcengine.util.Const.HTTPS) ?
                com.volcengine.util.Const.HTTPS : com.volcengine.util.Const.HTTP;
        return new ServiceInfo(
                new HashMap<String, Object>() {
                    {
                        put(com.volcengine.util.Const.CONNECTION_TIMEOUT,
                                config.getConnectionTimeout());
                        put(com.volcengine.util.Const.SOCKET_TIMEOUT,
                                config.getSocketTimeout());
                        put(com.volcengine.util.Const.Scheme, schema);
                        put(com.volcengine.util.Const.Host, url[1]);
                        put(com.volcengine.util.Const.Header, new ArrayList<Header>() {
                            {
                                add(new Header(com.volcengine.util.Const.ACCEPT,
                                        com.volcengine.util.Const.ACCEPT_ALL));
                                add(new Header(com.volcengine.util.Const.ACCEPT_ENCODING,
                                        com.volcengine.util.Const.GZIP_DEFLATE_BR));
                                add(new Header(Const.REGION, config.getRegion()));
                            }
                        });
                        put(com.volcengine.util.Const.Credentials, new Credentials(config.getRegion(), TLS));
                    }
                }
        );
    }

    public void resetAccessKeyToken(String accessKey, String secretKey, String securityToken) {
        setAccessKeyId(accessKey);
        setAccessKeySecret(secretKey);
        setSecurityToken(securityToken);
    }
}
