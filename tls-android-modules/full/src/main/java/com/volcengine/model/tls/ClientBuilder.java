package com.volcengine.model.tls;

import com.volcengine.model.ServiceInfo;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.service.tls.TLSHttpUtil;
import com.volcengine.service.tls.TLSLogClient;
import com.volcengine.service.tls.TLSLogClientImpl;

import static com.volcengine.model.tls.Const.SOCKET_TIMEOUT_MS;
import static com.volcengine.model.tls.Const.CONNECTION_TIMEOUT_MS;


public class ClientBuilder {
    public static final String HTTP = "http";

    public ClientBuilder() {
    }

    public static TLSLogClient newClient(ClientConfig config) throws LogException {
        if (config == null) {
            throw new LogException("", "client config null error", null);
        }
        if (config.getRegion() == null) {
            throw new LogException("", "client config region null error", null);
        }
        if (config.getAccessKeySecret() == null) {
            throw new LogException("", "client config accessKeySecret null error", null);
        }
        if (config.getAccessKeyId() == null) {
            throw new LogException("", "client config accessKeyId null error", null);
        }
        if (config.getEndpoint() == null) {
            throw new LogException("", "client config endpoint null error", null);
        }
        if (!config.getEndpoint().toLowerCase().startsWith(HTTP)) {
            throw new LogException("", "client config endpoint should start with http:// or https://", null);
        }

        if (config.apiVersion == null) {
            throw new LogException("", "client config api version null error", null);
        }
        //init config for service
        ServiceInfo serviceInfo = ClientConfig.initServiceInfo(config);
        TLSHttpUtil tlsHttpUtil = new TLSHttpUtil(serviceInfo, TLSHttpUtil.API_INFO_LIST);
        tlsHttpUtil.setAccessKey(config.getAccessKeyId());
        tlsHttpUtil.setSecretKey(config.getAccessKeySecret());
        tlsHttpUtil.setSessionToken(config.getSecurityToken());
        tlsHttpUtil.setSocketTimeout(SOCKET_TIMEOUT_MS);
        tlsHttpUtil.setConnectionTimeout(CONNECTION_TIMEOUT_MS);

        return new TLSLogClientImpl(tlsHttpUtil, config);
    }

}
