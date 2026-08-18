package com.volcengine.service;

import android.util.Base64;

import com.volcengine.auth.ISignerV4;
import com.volcengine.auth.impl.SignerV4Impl;
import com.volcengine.error.SdkError;
import com.volcengine.http.DynamicTimeoutInterceptor;
import com.volcengine.http.OkHttpClientFactory;
import com.volcengine.http.VolcengineInterceptor;
import com.volcengine.model.ApiInfo;
import com.volcengine.model.Header;
import com.volcengine.model.NameValuePair;
import com.volcengine.model.ServiceInfo;
import com.volcengine.model.response.RawResponse;
import com.volcengine.util.Const;
import com.volcengine.util.EncodeUtil;
import com.volcengine.util.SDKVersion;
import com.volcengine.util.TlsLogger;
import com.volcengine.util.TlsLoggerFactory;
import okhttp3.*;

import java.net.Proxy;
import java.util.*;

import static com.volcengine.model.tls.Const.LZ4;

public abstract class BaseServiceImpl implements IBaseService {
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    public static final MediaType MEDIA_TYPE_PROTOBUF = MediaType.parse(Const.APPLICATION_X_PROTOBUF);

    private static final TlsLogger LOG = TlsLoggerFactory.getLogger(BaseServiceImpl.class);
    private String VERSION;

    protected ServiceInfo serviceInfo;
    protected Map<String, ApiInfo> apiInfoList;
    private OkHttpClient httpClient;
    private ISignerV4 ISigner;
    private int socketTimeout;
    private int connectionTimeout;

    private BaseServiceImpl() {}

    public BaseServiceImpl(ServiceInfo info, Proxy proxy, Map<String, ApiInfo> apiInfoList) {
        this(info, proxy, apiInfoList, SDKVersion.getAGENT());
    }

    protected BaseServiceImpl(ServiceInfo info, Proxy proxy, Map<String, ApiInfo> apiInfoList, String userAgent) {
        this.serviceInfo = info;
        this.apiInfoList = apiInfoList;
        this.ISigner = new SignerV4Impl();
        VolcengineInterceptor volcengineInterceptor = new VolcengineInterceptor(this.ISigner, serviceInfo.getCredentials(), userAgent);

        DynamicTimeoutInterceptor.DynamicTimeoutConfig defaultTimeout = new DynamicTimeoutInterceptor.DynamicTimeoutConfig(info.getConnectionTimeout(), info.getSocketTimeout());
        Map<String, DynamicTimeoutInterceptor.DynamicTimeoutConfig> apiTimeoutMap = new HashMap<String, DynamicTimeoutInterceptor.DynamicTimeoutConfig>();
        for (Map.Entry<String, ApiInfo> entry : apiInfoList.entrySet()) {
            ApiInfo apiInfo = entry.getValue();

            if (apiInfo.getConnectionTimeout() == 0 && apiInfo.getSocketTimeout() == 0) {
                continue;
            }
            if (apiInfo.getConnectionTimeout() == defaultTimeout.getConnectTimeout() && apiInfo.getSocketTimeout() == defaultTimeout.getReadTimeout()) {
                continue;
            }
            apiTimeoutMap.put(entry.getKey(), new DynamicTimeoutInterceptor.DynamicTimeoutConfig(apiInfo.getConnectionTimeout(), apiInfo.getSocketTimeout()));
        }

        DynamicTimeoutInterceptor dynamicTimeoutInterceptor = new DynamicTimeoutInterceptor(defaultTimeout, apiTimeoutMap);
        if (proxy == null) {
            this.httpClient = OkHttpClientFactory.create(serviceInfo.getConnectionTimeout(), serviceInfo.getSocketTimeout(), volcengineInterceptor, dynamicTimeoutInterceptor);
        } else {
            this.httpClient = OkHttpClientFactory.create(serviceInfo.getConnectionTimeout(), serviceInfo.getSocketTimeout(), proxy, volcengineInterceptor, dynamicTimeoutInterceptor);
        }
    }

    public BaseServiceImpl(ServiceInfo info, Map<String, ApiInfo> apiInfoList) {
        this(info, null, apiInfoList);
    }

    protected BaseServiceImpl(ServiceInfo info, Map<String, ApiInfo> apiInfoList, String userAgent) {
        this(info, null, apiInfoList, userAgent);
    }

    @Override
    public RawResponse json(String api, List<NameValuePair> params, String body) {
        ApiInfo apiInfo = apiInfoList.get(api);
        if (apiInfo == null) {
            return new RawResponse(null, SdkError.ENOAPI.getNumber(), new Exception(SdkError.getErrorDesc(SdkError.ENOAPI)));
        }
        Request.Builder requestBuilder = prepareRequestBuilder(api, params);
        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, body);

        String method = apiInfo.getMethod();

        if (Const.GET.equals(method)) {
            requestBody = null;
            requestBuilder.header(Const.ContentType, Const.APPLICATION_JSON);
        } else {
            requestBuilder.header(Const.ContentType, requestBody.contentType().toString());
        }
        requestBuilder.method(method, requestBody);
        return makeRequest(api, requestBuilder.build());
    }

    private RawResponse makeRequest(String api, Request request) {
        OkHttpClient client;
        Response response = null;
        long start = System.currentTimeMillis();
        try {
            if (getHttpClient() != null) {
                client = getHttpClient();
            } else {
                return new RawResponse(null, SdkError.UNKNOWN.getNumber(), new IllegalStateException(""));
            }
            Call call = client.newCall(request);
            response = call.execute();
            ResponseBody body = response.body();
            byte[] bytes = null;
            int statusCode = response.code();
            Headers headers = response.headers();
            if (statusCode >= 300) {
                String msg = "";
                byte[] errBytes = null;
                if (body != null) {
                    errBytes = body.bytes();
                    msg = formatErrorBody(errBytes);
                }
                return new RawResponse(errBytes, SdkError.EHTTP.getNumber(), new Exception(msg), headers, statusCode);
            }
            if (body != null) {
                bytes = body.bytes();
            }
            return new RawResponse(bytes, SdkError.SUCCESS.getNumber(), null, headers, statusCode);
        } catch (Exception e) {
            return new RawResponse(null, SdkError.EHTTP.getNumber(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private String formatErrorBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) { return ""; }
        int limit = Math.min(bytes.length, 4096);
        byte[] head = Arrays.copyOf(bytes, limit);
        String s = new String(head, Const.UTF_8);
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 0x20 && c != 0x7f)) { printable++; }
        }
        if (s.length() > 0 && ((double) printable / (double) s.length()) >= 0.85) {
            return bytes.length > limit ? (s + "\n...(truncated)") : s;
        }
        String b64 = Base64.encodeToString(head, Base64.NO_WRAP);
        return "base64:" + b64 + (bytes.length > limit ? "...(truncated)" : "");
    }

    private boolean isDebugHeadersEnabled() {
        String v = System.getProperty("tls.debugHeaders");
        if (v == null || v.isEmpty()) { return false; }
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    private void logProtoHeaders(String api, Request request) {
        if (!isDebugHeadersEnabled()) { return; }
        try {
            Headers h = request.headers();
            StringBuilder sb = new StringBuilder();
            appendHeader(sb, h, com.volcengine.model.tls.Const.X_TLS_COMPRESS_TYPE);
            appendHeader(sb, h, com.volcengine.model.tls.Const.X_TLS_BODY_RAW_SIZE);
            appendHeader(sb, h, com.volcengine.model.tls.Const.X_TLS_HASHKEY);
            appendHeader(sb, h, com.volcengine.model.tls.Const.Log_Count_Header);
            appendHeader(sb, h, com.volcengine.model.tls.Const.Earliest_Log_Time_Header);
            appendHeader(sb, h, com.volcengine.model.tls.Const.Latest_Log_Time_Header);
            appendHeader(sb, h, Const.CONTENT_TYPE);
            String line = "TLS request api=" + api + " url=" + request.url() + " " + sb.toString().trim();
            LOG.debug(line);
        } catch (Exception ignored) {
        }
    }

    private void appendHeader(StringBuilder sb, Headers h, String name) {
        if (sb == null || h == null || name == null) { return; }
        String v = h.get(name);
        if (v == null) { return; }
        sb.append(name).append('=').append(v).append(' ');
    }

    private Collection<Header> mergeHeader(List<Header> header1, List<Header> header2) {
        Set<Header> set = new HashSet<>();
        if (header1 != null) {
            set.addAll(header1);
        }
        if (header2 != null) {
            set.addAll(header2);
        }
        return set;
    }

    private List<NameValuePair> mergeQuery(List<NameValuePair> query1, List<NameValuePair> query2) {
        List<NameValuePair> res = new ArrayList<NameValuePair>();
        if (query1 != null) {
            res.addAll(query1);
        }
        if (query2 != null) {
            res.addAll(query2);
        }
        return res;
    }

    private int getConnectionTimeout(int serviceTimeout, int apiTimeout) {
        int timeout = 5000;
        if (serviceTimeout != 0) {
            timeout = serviceTimeout;
        }
        if (apiTimeout != 0) {
            timeout = apiTimeout;
        }
        if (connectionTimeout != 0) {
            timeout = connectionTimeout;
        }
        return timeout;
    }

    private int getSocketTimeout(int serviceTimeout, int apiTimeout) {
        int timeout = 5000;
        if (serviceTimeout != 0) {
            timeout = serviceTimeout;
        }
        if (apiTimeout != 0) {
            timeout = apiTimeout;
        }
        if (socketTimeout != 0) {
            timeout = socketTimeout;
        }
        return timeout;
    }

    @Override
    public String getAccessKey() { return serviceInfo.getCredentials().getAccessKeyID(); }
    @Override
    public void setAccessKey(String accessKey) { serviceInfo.getCredentials().setAccessKeyID(accessKey); }
    @Override
    public String getSecretKey() { return serviceInfo.getCredentials().getSecretAccessKey(); }
    @Override
    public void setSecretKey(String secretKey) { serviceInfo.getCredentials().setSecretAccessKey(secretKey); }
    @Override
    public String getSessionToken() { return serviceInfo.getCredentials().getSessionToken(); }
    @Override
    public void setSessionToken(String sessionToken) { serviceInfo.getCredentials().setSessionToken(sessionToken); }
    @Override
    public void setRegion(String region) { serviceInfo.getCredentials().setRegion(region); }
    @Override
    public String getRegion() { return serviceInfo.getCredentials().getRegion(); }
    @Override
    public void setHost(String host) { serviceInfo.setHost(host); }
    @Override
    public void setScheme(String scheme) { serviceInfo.setScheme(scheme); }

    public OkHttpClient getHttpClient() { return httpClient; }
    @Override
    public void setHttpClient(OkHttpClient httpClient) { this.httpClient = httpClient; }
    public ServiceInfo getServiceInfo() { return serviceInfo; }

    public void destroy() {
        if (this.httpClient != null) {
            try { this.httpClient.dispatcher().executorService().shutdown(); } catch (Exception ignored) {}
            try { this.httpClient.connectionPool().evictAll(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void setServiceInfo(ServiceInfo serviceInfo) { this.serviceInfo = serviceInfo; }
    public Map<String, ApiInfo> getApiInfoList() { return apiInfoList; }
    public ISignerV4 getISigner() { return ISigner; }

    @Override
    public void setSocketTimeout(int socketTimeout) {
        this.httpClient = OkHttpClientFactory.setSocketTimeout(this.httpClient.newBuilder(), socketTimeout);
    }
    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        this.httpClient = OkHttpClientFactory.setConnectionTimeout(this.httpClient.newBuilder(), connectionTimeout);
    }

    @Override
    public RawResponse proto(String api, List<NameValuePair> params, Map<String, String> header, byte[] body, String compressType) {
        ApiInfo apiInfo = apiInfoList.get(api);
        if (apiInfo == null) {
            return new RawResponse(null, SdkError.ENOAPI.getNumber(), new Exception(SdkError.getErrorDesc(SdkError.ENOAPI)));
        }

        Request.Builder requestBuilder = prepareRequestBuilder(api, params);

        if (header != null && header.size() > 0) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        byte[] compressedData = body.clone();
        String finalCompressType = compressType;
        if (compressType != null) {
            if (compressType.equalsIgnoreCase(LZ4)) {
                java.util.concurrent.FutureTask<byte[]> task = new java.util.concurrent.FutureTask<>(new java.util.concurrent.Callable<byte[]>() {
                    @Override public byte[] call() { return EncodeUtil.lz4Compress(body); }
                });
                Thread t = new Thread(task, "compress-worker");
                t.start();
                try {
                    compressedData = task.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception timeoutOrError) {
                    finalCompressType = com.volcengine.util.Const.ZLIB;
                    compressedData = EncodeUtil.compressLog(body, finalCompressType);
                }
            } else if (compressType.equalsIgnoreCase(com.volcengine.util.Const.ZLIB)) {
                compressedData = EncodeUtil.compressLog(body, compressType);
            }
        }
        if (header != null && finalCompressType != null) {
            header.put(com.volcengine.model.tls.Const.X_TLS_COMPRESS_TYPE, finalCompressType);
            header.put(com.volcengine.model.tls.Const.X_TLS_BODY_RAW_SIZE, String.valueOf(body.length));
        }
        if (finalCompressType != null) {
            requestBuilder.header(com.volcengine.model.tls.Const.X_TLS_COMPRESS_TYPE, finalCompressType);
            requestBuilder.header(com.volcengine.model.tls.Const.X_TLS_BODY_RAW_SIZE, String.valueOf(body.length));
        }

        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_PROTOBUF, compressedData);
        requestBuilder.header(Const.CONTENT_TYPE, requestBody.contentType().toString());
        requestBuilder.post(requestBody);
        Request req = requestBuilder.build();
        logProtoHeaders(api, req);
        return makeRequest(api, req);
    }

    private Request.Builder prepareRequestBuilder(String api, List<NameValuePair> params) {
        Request.Builder requestBuilder = new Request.Builder();
        return prepareRequestBuilder(requestBuilder, api, params);
    }

    private Request.Builder prepareRequestBuilder(Request.Builder requestBuilder, String api, List<NameValuePair> params) {
        ApiInfo apiInfo = apiInfoList.get(api);

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();

        Collection<Header> mergedH = mergeHeader(serviceInfo.getHeader(), apiInfo.getHeader());
        for (Header header : mergedH) {
            requestBuilder.addHeader(header.getName(), header.getValue());
        }
        List<NameValuePair> mergedNV = mergeQuery(params, apiInfo.getQuery());

        urlBuilder.scheme(serviceInfo.getScheme());
        urlBuilder.host(serviceInfo.getHost());
        if (serviceInfo.getPort() > 0) {
            urlBuilder.port(serviceInfo.getPort());
        }
        urlBuilder.encodedPath(apiInfo.getPath());
        for (NameValuePair pair : mergedNV) {
            urlBuilder.addQueryParameter(pair.getName(), pair.getValue());
        }

        requestBuilder.url(urlBuilder.build());
        return requestBuilder;
    }
}
