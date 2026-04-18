package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeHttpBridge;
import com.volcengine.tls.android.producer.internal.NativeHttpResponse;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NativeHttpBridgeTest {

    @Test
    public void execute_mapsTimeoutsHeadersTlsFlagsAndResponse() throws Exception {
        CapturingHttpsURLConnection connection = new CapturingHttpsURLConnection(
                new URL("https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id"));
        connection.responseCode = 204;
        connection.responseBody = new byte[] {9, 8, 7};
        connection.responseHeaders.put("x-request-id", Collections.singletonList("rid-1"));

        AtomicReference<URL> openedUrl = new AtomicReference<>();
        NativeHttpBridge bridge = new NativeHttpBridge(url -> {
            openedUrl.set(url);
            return connection;
        });

        NativeHttpResponse response = bridge.execute(new NativeHttpBridge.Request(
                "POST",
                "https://tls-cn-beijing.volces.com/PutLogs?TopicId=topic-id",
                "User-Agent: ignored-by-explicit-field\r\nx-tls-bodyrawsize: 10",
                new byte[] {1, 2, 3},
                1234,
                5678,
                0,
                0,
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
        assertNotNull(connection.sslSocketFactory);
        assertNotNull(connection.hostnameVerifier);

        assertEquals(204, response.getStatusCode());
        assertArrayEquals(new byte[] {9, 8, 7}, response.getBody());
        assertEquals("rid-1", response.getHeaders().get("x-request-id").get(0));
        assertEquals("rid-1", response.getRequestId());
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
}
