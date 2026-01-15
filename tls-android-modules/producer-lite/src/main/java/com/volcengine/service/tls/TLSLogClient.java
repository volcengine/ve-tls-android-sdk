package com.volcengine.service.tls;

import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;

public interface TLSLogClient {
    void configClient(ClientConfig config);
    void resetAccessKeyToken(String accessKeyID, String accessKeySecret, String securityToken);
    void setTimeout(int socketTimeout, int connectionTimeout);
    void destroy();

    PutLogsResponse putLogs(PutLogsRequest request) throws LogException;
}
