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
import com.volcengine.error.SdkError;
import com.volcengine.util.Const;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.volcengine.model.tls.Const.*;

public class MinimalTLSLogClientImpl implements TLSLogClient {
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
        int httpCode = rawResponse.getHttpCode();
        if (rawResponse.getCode() != SdkError.SUCCESS.getNumber() || httpCode < 200 || httpCode >= 300) {
            String reqId = rawResponse.getFirstHeader(X_TLS_REQUESTID);
            String msg = rawResponse.getException() == null ? "" : String.valueOf(rawResponse.getException().getMessage());
            byte[] data = rawResponse.getData();
            byte[] decoded = tryDecodeErrorBody(data);
            String errCode = extractJsonString(decoded, "ErrorCode");
            if (errCode == null) { errCode = extractJsonString(decoded, "errorCode"); }
            String errMsg = extractJsonString(decoded, "ErrorMessage");
            if (errMsg == null) { errMsg = extractJsonString(decoded, "errorMessage"); }
            if (errCode != null || errMsg != null) {
                throw new LogException(httpCode, errCode == null ? "HTTPError" : errCode, errMsg == null ? msg : errMsg, reqId);
            }
            throw new LogException(httpCode, "HTTPError", msg, reqId);
        }
        String reqId = rawResponse.getFirstHeader(X_TLS_REQUESTID);
        if (reqId == null || reqId.trim().isEmpty()) {
            throw new LogException(httpCode, "ResponseValidationError",
                    "PutLogs response missing X-Tls-Requestid", null);
        }
        return new PutLogsResponse(rawResponse.getHeaders(), httpCode);
    }

    private byte[] tryDecodeErrorBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) { return null; }
        int limit = Math.min(bytes.length, 4096);
        byte[] head = java.util.Arrays.copyOf(bytes, limit);
        if (head.length >= 2 && (head[0] == (byte) 0x1f) && (head[1] == (byte) 0x8b)) {
            try {
                java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(head);
                java.util.zip.GZIPInputStream gin = new java.util.zip.GZIPInputStream(in);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = gin.read(buf)) > 0 && out.size() < 4096) {
                    out.write(buf, 0, n);
                }
                gin.close();
                return out.toByteArray();
            } catch (Exception ignored) {
            }
        }
        return head;
    }

    private String extractJsonString(byte[] bytes, String key) {
        if (bytes == null || bytes.length == 0 || key == null || key.isEmpty()) { return null; }
        byte[] k = ('"' + key + '"').getBytes(Const.UTF_8);
        int idx = indexOf(bytes, k, 0);
        if (idx < 0) { return null; }
        int colon = indexOf(bytes, new byte[]{':'}, idx + k.length);
        if (colon < 0) { return null; }
        int i = colon + 1;
        while (i < bytes.length && isWhitespace(bytes[i])) { i++; }
        if (i >= bytes.length || bytes[i] != '"') { return null; }
        i++;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < bytes.length) {
            byte b = bytes[i++];
            if (esc) {
                sb.append((char) (b & 0xff));
                esc = false;
                continue;
            }
            if (b == '\\') { esc = true; continue; }
            if (b == '"') { break; }
            sb.append((char) (b & 0xff));
        }
        String v = sb.toString();
        return v.isEmpty() ? null : v;
    }

    private int indexOf(byte[] src, byte[] target, int from) {
        if (src == null || target == null || target.length == 0) { return -1; }
        outer:
        for (int i = Math.max(from, 0); i <= src.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (src[i + j] != target[j]) { continue outer; }
            }
            return i;
        }
        return -1;
    }

    private boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    // lite client does not implement putLogsV2 directly; use ProducerImpl for V2

    // minimal client: other APIs are not supported in lite
}
