package com.volcengine.http;

import com.volcengine.auth.ISignerV4;
import com.volcengine.util.Const;
import com.volcengine.util.SDKVersion;
import com.volcengine.model.*;
import com.volcengine.model.Credentials;
import okhttp3.*;
import okio.Buffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class VolcengineInterceptor implements Interceptor {

    public ISignerV4 signer;

    public Credentials credentials;

    public VolcengineInterceptor(ISignerV4 signer, Credentials credentials) {
        this.signer = signer;
        this.credentials = credentials;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request req = chain.request();
        RequestParam.RequestParamBuilder param = RequestParam.builder();
        param.body(getBytes(req));
        param.host(req.url().host());
        param.path(req.url().encodedPath());
        param.method(req.method());
        param.queryList(convertQuery(req.url()));
        param.headers(convertHeader(req.headers()));
        param.isSignUrl(false);
        param.date(new Date());

        SignRequest signRequest;
        try {
            signRequest = signer.getSignRequest(param.build(), credentials);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (signRequest == null) {
            throw new IllegalArgumentException("Sign Error");
        }
        Request.Builder newReq = req.newBuilder();
        newReq.addHeader(Const.XDate, signRequest.getXDate());
        if (signRequest.getXNotSignBody() != null) {
            newReq.addHeader(Const.XNotSignBody, signRequest.getXNotSignBody());
        }
        newReq.header(Const.ContentType, signRequest.getContentType());
        newReq.addHeader(Const.XContentSha256, signRequest.getXContentSha256());
        newReq.addHeader(Const.Authorization, signRequest.getAuthorization());
        newReq.addHeader(Const.USERAGENT, SDKVersion.getAGENT());
        return chain.proceed(newReq.build());
    }

    private List<Header> convertHeader(Headers headers) {
        List<Header> list = new ArrayList<>();
        for (String name : headers.names()) {
            for (String value : headers.values(name)) {
                list.add(new Header(name, value));
            }
        }
        return list;
    }

    private List<NameValuePair> convertQuery(HttpUrl url) {
        Set<String> names = url.queryParameterNames();
        ArrayList<NameValuePair> list = new ArrayList<NameValuePair>(names.size());
        for (String name : names) {
            for (String value : url.queryParameterValues(name)) {
                list.add(new NameValuePair(name, value));
            }
        }
        return list;
    }

    private byte[] getBytes(Request req) throws IOException {
        if (req.body() == null || req.body().contentLength() == 0) {
            return new byte[0];
        }
        RequestBody body = req.body();
        Buffer b = new Buffer();
        body.writeTo(b);
        return b.readByteArray();
    }
}
