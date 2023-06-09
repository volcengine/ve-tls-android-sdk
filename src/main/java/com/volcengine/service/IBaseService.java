package com.volcengine.service;

import com.volcengine.model.NameValuePair;
import com.volcengine.model.response.RawResponse;
import com.volcengine.model.sts2.Policy;
import com.volcengine.model.sts2.SecurityToken2;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.Map;

/**
 * The interface Service.
 */
public interface IBaseService {

    /**
     * Gets access key.
     *
     * @return the access key
     */
    String getAccessKey();

    /**
     * Sets access key.
     *
     * @param accessKey the access key
     */
    void setAccessKey(String accessKey);

    /**
     * Gets secret key.
     *
     * @return the secret key
     */
    String getSecretKey();

    /**
     * Sets secret key.
     *
     * @param secretKey the secret key
     */
    void setSecretKey(String secretKey);

    /**
     * Gets session token.
     *
     * @return the session token
     */
    String getSessionToken();

    /**
     * Sets session token.
     *
     * @param sessionToken the session token
     */
    void setSessionToken(String sessionToken);

    /**
     * Sets region.
     *
     * @param region the region
     */
    void setRegion(String region);

    /**
     * Gets region.
     *
     * @return the region
     */
    String getRegion();

    /**
     * Sets host.
     *
     * @param host the host
     */
    void setHost(String host);

    /**
     * Sets scheme.
     *
     * @param scheme the scheme
     */
    void setScheme(String scheme);

    /**
     * Sets http client.
     *
     * @param httpClient the http client
     */
    void setHttpClient(OkHttpClient httpClient);

    /**
     * Sets service info.
     *
     * @param serviceInfo the service info
     */
    void setServiceInfo(com.volcengine.model.ServiceInfo serviceInfo);

    /**
     * Sets socket timeout.
     *
     * @param socketTimeout the socket timeout
     */
    void setSocketTimeout(int socketTimeout);

    /**
     * Sets connection timeout.
     *
     * @param connectionTimeout the connection timeout
     */
    void setConnectionTimeout(int connectionTimeout);

    /**
     * Json raw response.
     *
     * @param api    the api
     * @param params the params
     * @param body   the body
     * @return the raw response
     * @throws Exception the exception
     */
    RawResponse json(String api, List<NameValuePair> params, String body) throws Exception;


    /**
     * @param api          interface path like /PutLogs
     * @param params       query params
     * @param header       some special header for protobuf
     * @param body         request body
     * @param compressType default not compress,support lz4
     * @return
     */
    RawResponse proto(String api, List<NameValuePair> params, Map<String, String> header, byte[] body, String compressType);

}
