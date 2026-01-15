package com.volcengine.service.tls;

import com.volcengine.model.ApiInfo;
import com.volcengine.model.NameValuePair;
import com.volcengine.model.ServiceInfo;
import com.volcengine.model.response.RawResponse;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.pb.PutLogRequest;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.PutLogsResponse;
import com.volcengine.util.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.volcengine.model.tls.Const.*;

public class MinimalTLSLogClientImpl implements TLSLogClient {
    private static final Logger LOG = LoggerFactory.getLogger(MinimalTLSLogClientImpl.class);
    private ClientConfig config;
    private final TLSHttpUtilLite httpRequest;

    public MinimalTLSLogClientImpl(ClientConfig config) {
        this.config = config;
        ServiceInfo serviceInfo = ClientConfig.initServiceInfo(config);
        this.httpRequest = new TLSHttpUtilLite(serviceInfo, TLSHttpUtilLite.API_INFO_LIST);
        this.httpRequest.setAccessKey(config.getAccessKeyId());
        this.httpRequest.setSecretKey(config.getAccessKeySecret());
        this.httpRequest.setSessionToken(config.getSecurityToken());
        this.httpRequest.setSocketTimeout(SOCKET_TIMEOUT_MS);
        this.httpRequest.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    }

    @Override
    public void configClient(ClientConfig config) {
        this.config = config;
        httpRequest.setServiceInfo(ClientConfig.initServiceInfo(config));
    }

    @Override
    public void resetAccessKeyToken(String accessKeyID, String accessKeySecret, String securityToken) {
        httpRequest.setAccessKey(accessKeyID);
        httpRequest.setSecretKey(accessKeySecret);
        httpRequest.setSessionToken(securityToken);
    }

    @Override
    public void setTimeout(int socketTimeout, int connectionTimeout) {
        httpRequest.setSocketTimeout(socketTimeout);
        httpRequest.setConnectionTimeout(connectionTimeout);
    }

    @Override
    public void destroy() {
        httpRequest.destroy();
    }

    @Override
    public PutLogsResponse putLogs(PutLogsRequest request) throws LogException {
        if (request == null || !request.CheckValidation()) {
            throw new LogException("InvalidArgument", "Invalid request, Please check it", null);
        }

        int logCnt = 0;
        long maxLogTime = Long.MIN_VALUE;
        long minLogTime = Long.MAX_VALUE;
        for (PutLogRequest.LogGroup logGroup : request.getLogGroupList().getLogGroupsList()) {
            List<PutLogRequest.Log> logs = logGroup.getLogsList();
            for (int i = 0; i < logs.size(); i++) {
                PutLogRequest.Log log = logs.get(i);
                long normalizedTime = log.getTime();
                maxLogTime = Math.max(maxLogTime, normalizedTime);
                minLogTime = Math.min(minLogTime, normalizedTime);
                logCnt++;
            }
        }

        if (logCnt == 0) {
            throw new LogException("InvalidArgument", "Invalid log num, Please check it", null);
        }

        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair(TOPIC_ID, request.getTopicId()));
        HashMap<String, String> headers = new HashMap<>();
        if (request.getHashKey() != null) { headers.put(X_TLS_HASHKEY, request.getHashKey()); }
        String compressType = request.getCompressType();
        if (compressType != null) {
            headers.put(X_TLS_COMPRESS_TYPE, compressType);
            headers.put(X_TLS_BODY_RAW_SIZE, String.valueOf(request.getLogGroupList().toByteArray().length));
        }

        headers.put(Log_Count_Header, String.valueOf(logCnt));
        headers.put(Earliest_Log_Time_Header, String.valueOf(minLogTime));
        headers.put(Latest_Log_Time_Header, String.valueOf(maxLogTime));

        byte[] rawBody = request.getLogGroupList().toByteArray();
        RawResponse rawResponse = httpRequest.proto(PUT_LOGS, params, headers, rawBody, compressType);
        return new PutLogsResponse(rawResponse.getHeaders());
    }

    // lite client does not implement putLogsV2 directly; use ProducerImpl for V2

    // minimal client: other APIs are not supported in lite
}
