package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class NativeHttpBridge {
    private static final HostnameVerifier PERMISSIVE_HOSTNAME_VERIFIER = (hostname, session) -> true;

    private final ConnectionFactory connectionFactory;
    private final boolean debugBuild;
    private final SocketFactorySupplier permissiveSocketFactorySupplier;
    private final WarningReporter warningReporter;
    private volatile SSLSocketFactory permissiveSocketFactory;

    public NativeHttpBridge() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    public NativeHttpBridge(ConnectionFactory connectionFactory) {
        this(
                connectionFactory,
                BuildConfig.DEBUG,
                NativeHttpBridge::createPermissiveSocketFactory,
                NativeHttpBridge::reportWarning);
    }

    public NativeHttpBridge(
            ConnectionFactory connectionFactory,
            boolean debugBuild,
            SocketFactorySupplier permissiveSocketFactorySupplier,
            WarningReporter warningReporter) {
        this.connectionFactory = connectionFactory;
        this.debugBuild = debugBuild;
        this.permissiveSocketFactorySupplier = permissiveSocketFactorySupplier;
        this.warningReporter = warningReporter;
    }

    public NativeHttpResponse execute(Request request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request == null");
        }

        HttpURLConnection connection = openConnection(request);
        try {
            configureConnection(connection, request);
            writeBodyIfPresent(connection, request.getBody());

            int statusCode = connection.getResponseCode();
            byte[] responseBody = readResponseBody(connection, statusCode);
            Map<String, List<String>> headers = connection.getHeaderFields();
            return new NativeHttpResponse(statusCode, headers, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(Request request) throws IOException {
        URL url = new URL(request.getUrl());
        Proxy proxy = request.getProxy();
        return proxy == null ? connectionFactory.open(url) : connectionFactory.open(url, proxy);
    }

    private void configureConnection(HttpURLConnection connection, Request request) throws IOException {
        connection.setConnectTimeout(request.getConnectTimeoutMs());
        connection.setReadTimeout(request.getRequestTimeoutMs());
        connection.setRequestMethod(request.getMethod());
        connection.setDoInput(true);
        if (request.getBody().length > 0) {
            connection.setDoOutput(true);
        }

        applyHeaders(connection, request.getHeaders(), request.getUserAgent());
        applyTlsOptions(connection, request);
    }

    private void applyHeaders(HttpURLConnection connection, String headers, String userAgent) {
        if (headers != null && !headers.trim().isEmpty()) {
            String[] lines = headers.split("\\r?\\n");
            for (String line : lines) {
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (!key.isEmpty()) {
                    connection.setRequestProperty(key, value);
                }
            }
        }

        if (userAgent != null && !userAgent.isEmpty()) {
            connection.setRequestProperty("User-Agent", userAgent);
        }
    }

    private void applyTlsOptions(HttpURLConnection connection, Request request) throws IOException {
        boolean permissivePeer = request.getTlsVerifyPeer() == 0;
        boolean permissiveHost = request.getTlsVerifyHost() == 0;
        if (permissivePeer || permissiveHost) {
            enforcePermissiveTlsAllowed(request);
        }
        if (!(connection instanceof HttpsURLConnection)) {
            return;
        }

        HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
        if (permissivePeer || permissiveHost) {
            warningReporter.report("permissive TLS activated for " + request.getUrl()
                    + " tlsVerifyPeer=" + request.getTlsVerifyPeer()
                    + " tlsVerifyHost=" + request.getTlsVerifyHost());
        }
        if (permissivePeer) {
            httpsConnection.setSSLSocketFactory(getPermissiveSocketFactory());
        } else if (request.getCaCertPath() != null && !request.getCaCertPath().trim().isEmpty()) {
            httpsConnection.setSSLSocketFactory(createSocketFactoryFromCaCert(request.getCaCertPath()));
        }

        if (permissiveHost) {
            httpsConnection.setHostnameVerifier(PERMISSIVE_HOSTNAME_VERIFIER);
        }
    }

    private void enforcePermissiveTlsAllowed(Request request) {
        if (!debugBuild) {
            throw new SecurityException("permissive TLS is debug-only: " + request.getUrl());
        }
    }

    private SSLSocketFactory getPermissiveSocketFactory() {
        SSLSocketFactory current = permissiveSocketFactory;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (permissiveSocketFactory == null) {
                permissiveSocketFactory = permissiveSocketFactorySupplier.get();
            }
            return permissiveSocketFactory;
        }
    }

    private void writeBodyIfPresent(HttpURLConnection connection, byte[] body) throws IOException {
        if (body == null || body.length == 0) {
            return;
        }

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body);
        }
    }

    private byte[] readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (inputStream == null) {
            return new byte[0];
        }

        try (InputStream stream = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static SSLSocketFactory createPermissiveSocketFactory() {
        try {
            TrustManager[] trustManagers = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            // no-op
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            // no-op
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("failed to create permissive SSL socket factory", e);
        }
    }

    private static void reportWarning(String message) {
        System.err.println("NativeHttpBridge WARN: " + message);
    }

    private static SSLSocketFactory createSocketFactoryFromCaCert(String caCertPath) throws IOException {
        try (InputStream inputStream = new FileInputStream(caCertPath)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            java.util.Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(inputStream);

            if (certificates.isEmpty()) {
                throw new IOException("no certificates found in " + caCertPath);
            }

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                keyStore.setCertificateEntry("ca-" + index++, certificate);
            }

            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), new SecureRandom());
            return context.getSocketFactory();
        } catch (CertificateException | KeyStoreException | java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
            throw new IOException("failed to create SSL socket factory from CA cert path: " + caCertPath, e);
        }
    }

    @FunctionalInterface
    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;

        default HttpURLConnection open(URL url, Proxy proxy) throws IOException {
            if (proxy == null) {
                return open(url);
            }
            return (HttpURLConnection) url.openConnection(proxy);
        }
    }

    @FunctionalInterface
    public interface SocketFactorySupplier {
        SSLSocketFactory get();
    }

    @FunctionalInterface
    public interface WarningReporter {
        void report(String message);
    }

    public static final class Request {
        private final String method;
        private final String url;
        private final String headers;
        private final byte[] body;
        private final int connectTimeoutMs;
        private final int requestTimeoutMs;
        private final int tlsVerifyPeer;
        private final int tlsVerifyHost;
        private final Proxy proxy;
        private final String caCertPath;
        private final String userAgent;

        public Request(
                String method,
                String url,
                String headers,
                byte[] body,
                int connectTimeoutMs,
                int requestTimeoutMs,
                int tlsVerifyPeer,
                int tlsVerifyHost,
                Proxy proxy,
                String caCertPath,
                String userAgent) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body == null ? new byte[0] : body.clone();
            this.connectTimeoutMs = connectTimeoutMs;
            this.requestTimeoutMs = requestTimeoutMs;
            this.tlsVerifyPeer = tlsVerifyPeer;
            this.tlsVerifyHost = tlsVerifyHost;
            this.proxy = proxy;
            this.caCertPath = caCertPath;
            this.userAgent = userAgent;
        }

        public String getMethod() {
            return method;
        }

        public String getUrl() {
            return url;
        }

        public String getHeaders() {
            return headers;
        }

        public byte[] getBody() {
            return body.clone();
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public int getTlsVerifyPeer() {
            return tlsVerifyPeer;
        }

        public int getTlsVerifyHost() {
            return tlsVerifyHost;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public String getCaCertPath() {
            return caCertPath;
        }

        public String getUserAgent() {
            return userAgent;
        }
    }
}
