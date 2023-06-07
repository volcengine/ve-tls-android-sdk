package com.volcengine.http;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import com.volcengine.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

public class OkHttpClientFactory {

    private static class ClientHolder {
        private static final OkHttpClient INSTANCE;

        static {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(ClientConfiguration.DEFAULT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                    .readTimeout(ClientConfiguration.DEFAULT_SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

            String hostname = System.getProperty("volc.proxy.hostname");
            if (StringUtils.isNotBlank(hostname)) {
                int port = 80;
                String portP = System.getProperty("volc.proxy.port");
                if (StringUtils.isNotBlank(portP)) {
                    port = Integer.parseInt(portP);
                }
                SocketAddress addr = new InetSocketAddress(hostname, port);
                Proxy proxy = new Proxy(Proxy.Type.HTTP, addr);
                builder.proxy(proxy);
            }
            INSTANCE = builder.build();
        }
    }

    public static OkHttpClient create(ClientConfiguration conf, Proxy proxy, Interceptor... interceptors) {
        OkHttpClient.Builder builder = ClientHolder.INSTANCE.newBuilder();
        builder.proxy(proxy)
                .connectTimeout(conf.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(conf.getSocketTimeout(), TimeUnit.MILLISECONDS);
        for (Interceptor interceptor : interceptors) {
            builder.addInterceptor(interceptor);
        }

        return builder.build();
    }

    public static OkHttpClient create(int connectionTimeout, int socketTimeout, Proxy proxy, Interceptor... interceptors) {
        OkHttpClient.Builder builder = ClientHolder.INSTANCE.newBuilder().proxy(proxy);
        return create(builder, connectionTimeout, socketTimeout, interceptors);
    }


    public static OkHttpClient create(int connectionTimeout, int socketTimeout, Interceptor... interceptors) {
        OkHttpClient.Builder builder = ClientHolder.INSTANCE.newBuilder();
        return create(builder, connectionTimeout, socketTimeout, interceptors);
    }

    private static OkHttpClient create(OkHttpClient.Builder builder, int connectionTimeout, int socketTimeout, Interceptor... interceptors) {
        for (Interceptor interceptor : interceptors) {
            builder.addInterceptor(interceptor);
        }
        builder.readTimeout(socketTimeout, TimeUnit.MILLISECONDS);
        builder.connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS);
        return builder.build();
    }

    public static OkHttpClient create() {
        return ClientHolder.INSTANCE;
    }

    public static OkHttpClient setConnectionTimeout(OkHttpClient.Builder builder, int connectionTimeout) {
        return builder.connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient setSocketTimeout(OkHttpClient.Builder builder, int socketTimeout) {
        return builder.readTimeout(socketTimeout, TimeUnit.MILLISECONDS).build();
    }

}
