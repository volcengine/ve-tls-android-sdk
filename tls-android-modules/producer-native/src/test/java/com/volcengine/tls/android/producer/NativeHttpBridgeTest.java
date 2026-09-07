package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.BuildConfig;
import com.volcengine.tls.android.producer.internal.NativeHttpBridge;
import com.volcengine.tls.android.producer.internal.NativeHttpResponse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeHttpBridgeTest {

    @Test
    public void malformedUriIsTerminalBeforeOpeningConnection() {
        NativeHttpBridge bridge = new NativeHttpBridge(url -> {
            throw new AssertionError("malformed URI must not open a connection");
        });
        for (String url : new String[] {"http://[invalid", "http://localhost/invalid path"}) {
            IOException failure = assertThrows(MalformedURLException.class,
                    () -> bridge.execute(new NativeHttpBridge.Request(
                            "POST", url, null, new byte[0], 1000, 1000,
                            1, 1, null, null, "test")));
            assertFalse(NativeHttpBridge.isRetryable(failure));
        }
    }

    @Test
    public void validIpv6AndEncodedUriRemainUnchanged() throws Exception {
        String endpoint = "https://[::1]/a%20b?x=a%2Fb&y=%E4%B8%AD";
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(new URL(endpoint));
        connection.responseCode = 200;
        AtomicReference<URL> opened = new AtomicReference<>();
        NativeHttpBridge bridge = new NativeHttpBridge(url -> {
            opened.set(url);
            return connection;
        });
        bridge.execute(new NativeHttpBridge.Request("POST", endpoint, null, new byte[0],
                1000, 1000, 1, 1, null, null, "test"));
        assertEquals(endpoint, opened.get().toString());
    }

    @Test
    public void emptyCaBundleIsNonRetryable() throws Exception {
        File emptyCa = File.createTempFile("tls-empty-test-ca-", ".pem");
        try {
            CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(new URL("https://localhost"));
            NativeHttpBridge bridge = new NativeHttpBridge(url -> connection);
            IOException failure = assertThrows(IOException.class, () -> bridge.execute(new NativeHttpBridge.Request(
                    "POST", "https://localhost", null, new byte[0], 1000, 1000,
                    1, 1, null, emptyCa.getAbsolutePath(), "test")));
            assertFalse(NativeHttpBridge.isRetryable(failure));
        } finally {
            assertTrue(emptyCa.delete());
        }
    }

    @Test
    public void execute_mapsTimeoutsHeadersAndResponse_withoutPermissiveTlsSideEffects() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        connection.responseCode = 204;
        connection.responseBody = new byte[] {9, 8, 7};
        connection.responseHeaders.put("x-request-id", Collections.singletonList("rid-1"));

        AtomicReference<URL> openedUrl = new AtomicReference<>();
        AtomicInteger permissiveFactoryCreations = new AtomicInteger();
        NativeHttpBridge bridge = new NativeHttpBridge(url -> {
            openedUrl.set(url);
            return connection;
        }, true, () -> {
            permissiveFactoryCreations.incrementAndGet();
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }, message -> {
            throw new AssertionError("unexpected warning: " + message);
        });

        NativeHttpResponse response = bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                "User-Agent: ignored-by-explicit-field\r\nx-tls-bodyrawsize: 10",
                new byte[] {1, 2, 3},
                1234,
                5678,
                1,
                1,
                null,
                null,
                "tls-producer"));

        assertEquals("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id", openedUrl.get().toString());
        assertEquals(1234, connection.connectTimeoutMs);
        assertEquals(5678, connection.readTimeoutMs);
        assertEquals("POST", connection.requestMethod);
        assertEquals("tls-producer", connection.requestProperties.get("User-Agent"));
        assertEquals("10", connection.requestProperties.get("x-tls-bodyrawsize"));
        assertTrue(connection.doOutput);
        assertArrayEquals(new byte[] {1, 2, 3}, connection.writtenBody.toByteArray());
        assertEquals(0, permissiveFactoryCreations.get());
        assertNull(connection.sslSocketFactory);
        assertNull(connection.hostnameVerifier);

        assertEquals(204, response.getStatusCode());
        assertArrayEquals(new byte[] {9, 8, 7}, response.getBody());
        assertEquals("rid-1", response.getHeaders().get("x-request-id").get(0));
        assertEquals("rid-1", response.getRequestId());
    }

    @Test
    public void execute_debugPermissiveTlsRequest_lazyInitializesFactoryAndWarns() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        connection.responseCode = 204;

        AtomicInteger permissiveFactoryCreations = new AtomicInteger();
        List<String> warnings = new ArrayList<>();
        NativeHttpBridge bridge = new NativeHttpBridge(
                url -> connection,
                true,
                () -> {
                    permissiveFactoryCreations.incrementAndGet();
                    return (SSLSocketFactory) SSLSocketFactory.getDefault();
                },
                warnings::add);

        NativeHttpResponse response = bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                null,
                new byte[] {1},
                1000,
                2000,
                0,
                0,
                null,
                null,
                "tls-producer"));

        assertEquals(204, response.getStatusCode());
        assertEquals(1, permissiveFactoryCreations.get());
        assertNotNull(connection.sslSocketFactory);
        assertNotNull(connection.hostnameVerifier);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("permissive TLS"));
    }

    @Test
    public void execute_releasePermissivePeerRequest_failsFast() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        AtomicInteger permissiveFactoryCreations = new AtomicInteger();
        List<String> warnings = new ArrayList<>();
        NativeHttpBridge bridge = new NativeHttpBridge(
                url -> connection,
                false,
                () -> {
                    permissiveFactoryCreations.incrementAndGet();
                    return (SSLSocketFactory) SSLSocketFactory.getDefault();
                },
                warnings::add);

        assertThrows(SecurityException.class, () -> bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                null,
                new byte[] {1},
                1000,
                2000,
                0,
                1,
                null,
                null,
                "tls-producer")));
        assertEquals(0, permissiveFactoryCreations.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void execute_releasePermissiveHostRequest_failsFast() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        List<String> warnings = new ArrayList<>();
        NativeHttpBridge bridge = new NativeHttpBridge(
                url -> connection,
                false,
                () -> (SSLSocketFactory) SSLSocketFactory.getDefault(),
                warnings::add);

        assertThrows(SecurityException.class, () -> bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                null,
                new byte[] {1},
                1000,
                2000,
                1,
                0,
                null,
                null,
                "tls-producer")));
        assertTrue(warnings.isEmpty());
        assertNull(connection.hostnameVerifier);
    }

    @Test
    public void execute_releasePermissiveHttpRequest_failsFast() throws Exception {
        CapturingHttpURLConnection connection = new CapturingHttpURLConnection(
                new URL("http://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        List<String> warnings = new ArrayList<>();
        NativeHttpBridge bridge = new NativeHttpBridge(
                url -> connection,
                false,
                () -> (SSLSocketFactory) SSLSocketFactory.getDefault(),
                warnings::add);

        assertThrows(SecurityException.class, () -> bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "http://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                null,
                new byte[] {1},
                1000,
                2000,
                0,
                1,
                null,
                null,
                "tls-producer")));
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void publicConstructor_respectsBuildVariantDebugGuard() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        connection.responseCode = 204;
        NativeHttpBridge bridge = new NativeHttpBridge(url -> connection);
        NativeHttpBridge.Request request = new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                null,
                new byte[] {1},
                1000,
                2000,
                0,
                1,
                null,
                null,
                "tls-producer");

        if (BuildConfig.DEBUG) {
            NativeHttpResponse response = bridge.execute(request);
            assertEquals(204, response.getStatusCode());
            assertNotNull(connection.sslSocketFactory);
        } else {
            assertThrows(SecurityException.class, () -> bridge.execute(request));
        }
    }

    private static final class CapturingHttpsURLConnection extends HttpsURLConnection {
        private final Map<String, String> requestProperties = new LinkedHashMap<>();
        private final Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        private final java.io.ByteArrayOutputStream writtenBody = new java.io.ByteArrayOutputStream();
        private int connectTimeoutMs;
        private int readTimeoutMs;
        private String requestMethod;
        private boolean doOutput;
        private SSLSocketFactory sslSocketFactory;
        private HostnameVerifier hostnameVerifier;
        private int responseCode;
        private byte[] responseBody = new byte[0];

        CapturingHttpsURLConnection(URL url) {
            super(url);
        }

        @Override
        public void setConnectTimeout(int timeout) {
            this.connectTimeoutMs = timeout;
        }

        @Override
        public void setReadTimeout(int timeout) {
            this.readTimeoutMs = timeout;
        }

        @Override
        public void setRequestMethod(String method) {
            this.requestMethod = method;
        }

        @Override
        public void setDoOutput(boolean dooutput) {
            this.doOutput = dooutput;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            this.requestProperties.put(key, value);
        }

        @Override
        public void setSSLSocketFactory(SSLSocketFactory sf) {
            this.sslSocketFactory = sf;
        }

        @Override
        public void setHostnameVerifier(HostnameVerifier v) {
            this.hostnameVerifier = v;
        }

        @Override
        public void connect() {
            // no-op
        }

        @Override
        public void disconnect() {
            // no-op
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(responseBody);
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return writtenBody;
        }

        @Override
        public InputStream getErrorStream() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return responseHeaders;
        }

        @Override
        public String getCipherSuite() {
            return "TLS_FAKE";
        }

        @Override
        public java.security.cert.Certificate[] getLocalCertificates() {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] getServerCertificates() {
            return null;
        }

        @Override
        public java.security.Principal getPeerPrincipal() {
            return null;
        }

        @Override
        public java.security.Principal getLocalPrincipal() {
            return null;
        }
    }

    private static final class CapturingHttpURLConnection extends java.net.HttpURLConnection {
        private final java.io.ByteArrayOutputStream writtenBody = new java.io.ByteArrayOutputStream();
        private int responseCode = 204;
        private byte[] responseBody = new byte[0];

        CapturingHttpURLConnection(URL url) {
            super(url);
        }

        @Override
        public void disconnect() {
            // no-op
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // no-op
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return writtenBody;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(responseBody);
        }

        @Override
        public InputStream getErrorStream() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }
    }
}
