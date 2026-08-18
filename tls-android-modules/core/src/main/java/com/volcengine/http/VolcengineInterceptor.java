package com.volcengine.http;

import com.volcengine.auth.ISignerV4;
import com.volcengine.util.Const;
import com.volcengine.util.SDKVersion;
import com.volcengine.model.Credentials;
import com.volcengine.model.Header;
import com.volcengine.model.NameValuePair;
import com.volcengine.model.RequestParam;
import com.volcengine.model.SignRequest;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class VolcengineInterceptor implements Interceptor {
    public ISignerV4 signer;
    public com.volcengine.model.Credentials credentials;
    private final String userAgent;

    public VolcengineInterceptor(ISignerV4 signer, com.volcengine.model.Credentials credentials) {
        this(signer, credentials, SDKVersion.getAGENT());
    }

    public VolcengineInterceptor(ISignerV4 signer, com.volcengine.model.Credentials credentials, String userAgent) {
        this.signer = signer;
        this.credentials = credentials;
        this.userAgent = userAgent == null || userAgent.isEmpty() ? SDKVersion.getAGENT() : userAgent;
    }
    @Override public Response intercept(Chain chain) throws IOException {
        Request req = chain.request();
        RequestParam param = new RequestParam();
        param.setBody(getBytes(req));
        param.setHost(req.url().host());
        param.setPath(req.url().encodedPath());
        param.setMethod(req.method());
        param.setQueryList(convertQuery(req.url()));
        param.setHeaders(convertHeader(req.headers()));
        param.setIsSignUrl(false);
        param.setDate(new Date());

        SignRequest signRequest;
        try { signRequest = signer.getSignRequest(param, credentials); } catch (Exception e) { throw new IllegalStateException(e); }
        if (signRequest == null) { throw new IllegalArgumentException("Sign Error"); }
        Request.Builder newReq = req.newBuilder();
        newReq.addHeader(Const.XDate, signRequest.getXDate());
        if (signRequest.getXNotSignBody() != null) { newReq.addHeader(Const.XNotSignBody, signRequest.getXNotSignBody()); }
        newReq.header(Const.ContentType, signRequest.getContentType());
        newReq.addHeader(Const.XContentSha256, signRequest.getXContentSha256());
        newReq.addHeader(Const.Authorization, signRequest.getAuthorization());
        newReq.addHeader(Const.USERAGENT, userAgent);
        return chain.proceed(newReq.build());
    }
    private List<Header> convertHeader(Headers headers) { List<Header> list = new ArrayList<>(); for (String name : headers.names()) { for (String value : headers.values(name)) { list.add(new Header(name, value)); } } return list; }
    private List<NameValuePair> convertQuery(HttpUrl url) { Set<String> names = url.queryParameterNames(); ArrayList<NameValuePair> list = new ArrayList<NameValuePair>(names.size()); for (String name : names) { for (String value : url.queryParameterValues(name)) { list.add(new NameValuePair(name, value)); } } return list; }
    private byte[] getBytes(Request req) throws IOException { if (req.body() == null || req.body().contentLength() == 0) { return new byte[0]; } RequestBody body = req.body(); Buffer b = new Buffer(); body.writeTo(b); return b.readByteArray(); }
}
