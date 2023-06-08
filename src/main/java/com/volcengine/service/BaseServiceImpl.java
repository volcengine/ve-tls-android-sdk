package com.volcengine.service;

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
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Proxy;
import java.util.*;

import static com.volcengine.model.tls.Const.LZ4;

public abstract class BaseServiceImpl implements IBaseService {
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    public static final MediaType MEDIA_TYPE_PROTOBUF = MediaType.parse(Const.APPLICATION_X_PROTOBUF);

    private static final Logger LOG = LoggerFactory.getLogger(BaseServiceImpl.class);
    private String VERSION;

    protected ServiceInfo serviceInfo;
    protected Map<String, ApiInfo> apiInfoList;
    private OkHttpClient httpClient;
    private ISignerV4 ISigner;
    private int socketTimeout;
    private int connectionTimeout;

    private BaseServiceImpl() {
    }

    public BaseServiceImpl(ServiceInfo info, Proxy proxy, Map<String, ApiInfo> apiInfoList) {
        this.serviceInfo = info;
        this.apiInfoList = apiInfoList;
        this.ISigner = new SignerV4Impl();
        VolcengineInterceptor volcengineInterceptor = new VolcengineInterceptor(this.ISigner, serviceInfo.getCredentials());

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

    @Override
    public RawResponse json(String api, List<NameValuePair> params, String body) {
        ApiInfo apiInfo = apiInfoList.get(api);
        if (apiInfo == null) {
            return new RawResponse(null, SdkError.ENOAPI.getNumber(), new Exception(SdkError.getErrorDesc(SdkError.ENOAPI)));
        }
        Request.Builder requestBuilder = prepareRequestBuilder(api, params);
        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, body);

        String method = apiInfo.getMethod();
        if (method == Const.GET) {
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
                if (body != null) {
                    msg = body.string();
                }
                return new RawResponse(null, SdkError.EHTTP.getNumber(), new Exception(msg), headers, statusCode);
            }
            if (body != null) {
                bytes = body.bytes();
            }
            return new RawResponse(bytes, SdkError.SUCCESS.getNumber(), null, headers, statusCode);
        } catch (Exception e) {
            e.printStackTrace();
            return new RawResponse(null, SdkError.EHTTP.getNumber(), new Exception(SdkError.getErrorDesc(SdkError.EHTTP)));
        } finally {
            if (response != null) {
                response.close();
            }
        }
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
    public String getAccessKey() {
        return serviceInfo.getCredentials().getAccessKeyID();
    }

    @Override
    public void setAccessKey(String accessKey) {
        serviceInfo.getCredentials().setAccessKeyID(accessKey);
    }

    @Override
    public String getSecretKey() {
        return serviceInfo.getCredentials().getSecretAccessKey();
    }

    @Override
    public void setSecretKey(String secretKey) {
        serviceInfo.getCredentials().setSecretAccessKey(secretKey);
    }

    @Override
    public String getSessionToken() {
        return serviceInfo.getCredentials().getSessionToken();
    }

    @Override
    public void setSessionToken(String sessionToken) {
        serviceInfo.getCredentials().setSessionToken(sessionToken);
    }

    @Override
    public void setRegion(String region) {
        serviceInfo.getCredentials().setRegion(region);
    }


    @Override
    public String getRegion() {
        return serviceInfo.getCredentials().getRegion();
    }

    @Override
    public void setHost(String host) {
        serviceInfo.setHost(host);
    }

    @Override
    public void setScheme(String scheme) {
        serviceInfo.setScheme(scheme);
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public void setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    @Override
    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    public Map<String, ApiInfo> getApiInfoList() {
        return apiInfoList;
    }

    public ISignerV4 getISigner() {
        return ISigner;
    }

    @Override
    public void setSocketTimeout(int socketTimeout) {
        OkHttpClientFactory.setSocketTimeout(this.httpClient.newBuilder(), socketTimeout);
    }

    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        OkHttpClientFactory.setConnectionTimeout(this.httpClient.newBuilder(), connectionTimeout);
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
        if (compressType != null && compressType.equalsIgnoreCase(LZ4)) {
            compressedData = EncodeUtil.lz4Compress(body);
        }

        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_PROTOBUF, compressedData);
        requestBuilder.header(Const.CONTENT_TYPE, requestBody.contentType().toString());
        requestBuilder.post(requestBody);
        return makeRequest(api, requestBuilder.build());
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
        urlBuilder.encodedPath(apiInfo.getPath());
        for (NameValuePair pair : mergedNV) {
            urlBuilder.addQueryParameter(pair.getName(), pair.getValue());
        }

        requestBuilder.url(urlBuilder.build());
        return requestBuilder;
    }
}
