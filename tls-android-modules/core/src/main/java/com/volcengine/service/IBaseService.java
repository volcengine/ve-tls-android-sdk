package com.volcengine.service;

import com.volcengine.model.NameValuePair;
import com.volcengine.model.ServiceInfo;
import com.volcengine.model.response.RawResponse;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.Map;

public interface IBaseService {
    RawResponse json(String api, List<NameValuePair> params, String body);
    String getAccessKey();
    void setAccessKey(String accessKey);
    String getSecretKey();
    void setSecretKey(String secretKey);
    String getSessionToken();
    void setSessionToken(String sessionToken);
    void setRegion(String region);
    String getRegion();
    void setHost(String host);
    void setScheme(String scheme);
    void setHttpClient(OkHttpClient httpClient);
    void setServiceInfo(ServiceInfo serviceInfo);
    void setSocketTimeout(int socketTimeout);
    void setConnectionTimeout(int connectionTimeout);
    RawResponse proto(String api, List<NameValuePair> params, Map<String, String> header, byte[] body, String compressType);
}
