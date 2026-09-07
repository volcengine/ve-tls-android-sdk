package com.volcengine.tls.android.producer.internal;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Tls12SocketFactoryTest {
    private static final String TLS_V1_2 = "TLSv1.2";

    @Test
    public void legacyCacheWrapsAndReusesTheSameDelegate() {
        Tls12SocketFactory.Cache cache = new Tls12SocketFactory.Cache();
        FakeSSLSocketFactory delegate = new FakeSSLSocketFactory();

        SSLSocketFactory wrapped = cache.forApi(delegate, 16);
        assertNotSame(delegate, wrapped);
        for (int api = 17; api <= 19; api++) {
            assertSame(wrapped, cache.forApi(delegate, api));
        }

        FakeSSLSocketFactory otherDelegate = new FakeSSLSocketFactory();
        SSLSocketFactory otherWrapped = cache.forApi(otherDelegate, 19);
        assertNotSame(otherDelegate, otherWrapped);
        assertNotSame(wrapped, otherWrapped);
    }

    @Test
    public void cacheLeavesApiZeroAndModernFactoriesUntouched() {
        Tls12SocketFactory.Cache cache = new Tls12SocketFactory.Cache();
        FakeSSLSocketFactory delegate = new FakeSSLSocketFactory();

        assertSame(delegate, cache.forApi(delegate, 0));
        assertSame(delegate, cache.forApi(delegate, 20));
        assertSame(delegate, cache.forApi(delegate, 34));
    }

    @Test
    public void cacheShapeIsStaticFinalAndForApiIsSynchronized() throws Exception {
        assertTrue(Modifier.isFinal(Tls12SocketFactory.class.getModifiers()));
        assertTrue(Modifier.isStatic(Tls12SocketFactory.Cache.class.getModifiers()));
        assertTrue(Modifier.isFinal(Tls12SocketFactory.Cache.class.getModifiers()));

        Method forApi = Tls12SocketFactory.Cache.class.getDeclaredMethod(
                "forApi", SSLSocketFactory.class, int.class);
        assertTrue(Modifier.isSynchronized(forApi.getModifiers()));
        assertEquals(SSLSocketFactory.class, forApi.getReturnType());
    }

    @Test
    public void allCreateSocketOverloadsPreserveArgumentsAndConfigureTls12() throws Exception {
        FakeSSLSocketFactory delegate = new FakeSSLSocketFactory();
        Tls12SocketFactory factory = new Tls12SocketFactory(delegate);
        Socket layeredSocket = new Socket();
        InetAddress remoteAddress = InetAddress.getByAddress(
                "remote.example", new byte[] {127, 0, 0, 1});
        InetAddress localAddress = InetAddress.getByAddress(
                "local.example", new byte[] {127, 0, 0, 2});

        factory.createSocket();
        factory.createSocket(layeredSocket, "logs.example", 443, true);
        factory.createSocket("logs.example", 8443);
        factory.createSocket("logs.example", 9443, localAddress, 1234);
        factory.createSocket(remoteAddress, 10443);
        factory.createSocket(remoteAddress, 11443, localAddress, 2345);

        assertEquals(6, delegate.calls.size());

        SocketCall noArg = delegate.calls.get(0);
        assertEquals("noarg", noArg.operation);
        assertNull(noArg.layeredSocket);
        assertNull(noArg.host);
        assertNull(noArg.address);

        SocketCall layered = delegate.calls.get(1);
        assertEquals("layered", layered.operation);
        assertSame(layeredSocket, layered.layeredSocket);
        assertEquals("logs.example", layered.host);
        assertEquals(443, layered.port);
        assertTrue(layered.autoClose);

        SocketCall stringSocket = delegate.calls.get(2);
        assertEquals("string", stringSocket.operation);
        assertEquals("logs.example", stringSocket.host);
        assertEquals(8443, stringSocket.port);

        SocketCall stringLocalSocket = delegate.calls.get(3);
        assertEquals("string-local", stringLocalSocket.operation);
        assertEquals("logs.example", stringLocalSocket.host);
        assertEquals(9443, stringLocalSocket.port);
        assertSame(localAddress, stringLocalSocket.localAddress);
        assertEquals(1234, stringLocalSocket.localPort);

        SocketCall addressSocket = delegate.calls.get(4);
        assertEquals("address", addressSocket.operation);
        assertSame(remoteAddress, addressSocket.address);
        assertEquals(10443, addressSocket.port);

        SocketCall addressLocalSocket = delegate.calls.get(5);
        assertEquals("address-local", addressLocalSocket.operation);
        assertSame(remoteAddress, addressLocalSocket.address);
        assertEquals(11443, addressLocalSocket.port);
        assertSame(localAddress, addressLocalSocket.localAddress);
        assertEquals(2345, addressLocalSocket.localPort);

        for (SocketCall call : delegate.calls) {
            assertTrue(call.returnedSocket instanceof FakeSSLSocket);
            FakeSSLSocket socket = (FakeSSLSocket) call.returnedSocket;
            assertArrayEquals(new String[] {TLS_V1_2}, socket.enabledProtocols);
            assertEquals(1, socket.setEnabledProtocolsCalls);
        }
    }

    @Test
    public void cipherSuiteGettersDelegateWithoutChangingFactoryValues() {
        FakeSSLSocketFactory delegate = new FakeSSLSocketFactory();
        Tls12SocketFactory factory = new Tls12SocketFactory(delegate);

        assertSame(delegate.defaultCipherSuites, factory.getDefaultCipherSuites());
        assertSame(delegate.supportedCipherSuites, factory.getSupportedCipherSuites());
        assertEquals(1, delegate.defaultCipherSuiteCalls);
        assertEquals(1, delegate.supportedCipherSuiteCalls);
    }

    @Test
    public void unsupportedTls12ClosesSocketAndThrowsProtocolException() throws Exception {
        FakeSSLSocket socket = new FakeSSLSocket(new String[] {"TLSv1", "TLSv1.3"});
        Tls12SocketFactory factory = new Tls12SocketFactory(new FakeSSLSocketFactory(socket));

        assertThrowsProtocolException(factory);

        assertTrue(socket.closeCalled);
        assertEquals(0, socket.setEnabledProtocolsCalls);
    }

    @Test
    public void nonSslSocketClosesAndThrowsProtocolException() throws Exception {
        FakePlainSocket socket = new FakePlainSocket();
        Tls12SocketFactory factory = new Tls12SocketFactory(new FakeSSLSocketFactory(socket));

        assertThrowsProtocolException(factory);

        assertTrue(socket.closeCalled);
    }

    @Test
    public void protocolFailureIsPreservedWhenSocketCloseAlsoFails() throws Exception {
        IOException closeFailure = new IOException("close failed");
        FakeSSLSocket socket = new FakeSSLSocket(
                new String[] {"TLSv1", "TLSv1.3"}, closeFailure);
        Tls12SocketFactory factory = new Tls12SocketFactory(new FakeSSLSocketFactory(socket));

        SSLProtocolException failure = assertThrowsProtocolException(factory);

        assertEquals("TLS 1.2 is unavailable on this socket provider", failure.getMessage());
        assertTrue(socket.closeCalled);
    }

    private static SSLProtocolException assertThrowsProtocolException(Tls12SocketFactory factory)
            throws Exception {
        SSLProtocolException failure = org.junit.Assert.assertThrows(
                SSLProtocolException.class, factory::createSocket);
        assertFalse(failure.getMessage().isEmpty());
        return failure;
    }

    private static final class FakeSSLSocketFactory extends SSLSocketFactory {
        private final Socket fixedSocket;
        private final List<SocketCall> calls = new ArrayList<>();
        private final String[] defaultCipherSuites = {"TLS_FAKE_DEFAULT"};
        private final String[] supportedCipherSuites = {"TLS_FAKE_SUPPORTED"};
        private int defaultCipherSuiteCalls;
        private int supportedCipherSuiteCalls;

        FakeSSLSocketFactory() {
            this(null);
        }

        FakeSSLSocketFactory(Socket fixedSocket) {
            this.fixedSocket = fixedSocket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            defaultCipherSuiteCalls++;
            return defaultCipherSuites;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            supportedCipherSuiteCalls++;
            return supportedCipherSuites;
        }

        @Override
        public Socket createSocket() throws IOException {
            return record("noarg", null, null, null, 0, null, 0, false);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            return record("layered", socket, host, null, port, null, 0, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return record("string", null, host, null, port, null, 0, false);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return record("string-local", null, host, null, port, localHost, localPort, false);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return record("address", null, null, host, port, null, 0, false);
        }

        @Override
        public Socket createSocket(
                InetAddress host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return record("address-local", null, null, host, port, localHost, localPort, false);
        }

        private Socket record(
                String operation,
                Socket layeredSocket,
                String host,
                InetAddress address,
                int port,
                InetAddress localAddress,
                int localPort,
                boolean autoClose) {
            Socket returnedSocket = fixedSocket == null
                    ? new FakeSSLSocket(new String[] {TLS_V1_2})
                    : fixedSocket;
            calls.add(new SocketCall(
                    operation,
                    layeredSocket,
                    host,
                    address,
                    port,
                    localAddress,
                    localPort,
                    autoClose,
                    returnedSocket));
            return returnedSocket;
        }
    }

    private static final class SocketCall {
        private final String operation;
        private final Socket layeredSocket;
        private final String host;
        private final InetAddress address;
        private final int port;
        private final InetAddress localAddress;
        private final int localPort;
        private final boolean autoClose;
        private final Socket returnedSocket;

        SocketCall(
                String operation,
                Socket layeredSocket,
                String host,
                InetAddress address,
                int port,
                InetAddress localAddress,
                int localPort,
                boolean autoClose,
                Socket returnedSocket) {
            this.operation = operation;
            this.layeredSocket = layeredSocket;
            this.host = host;
            this.address = address;
            this.port = port;
            this.localAddress = localAddress;
            this.localPort = localPort;
            this.autoClose = autoClose;
            this.returnedSocket = returnedSocket;
        }
    }

    private static final class FakeSSLSocket extends SSLSocket {
        private final String[] supportedProtocols;
        private final IOException closeFailure;
        private String[] enabledProtocols = new String[0];
        private int setEnabledProtocolsCalls;
        private boolean closeCalled;
        private boolean useClientMode;
        private boolean needClientAuth;
        private boolean wantClientAuth;
        private boolean enableSessionCreation;

        FakeSSLSocket(String[] supportedProtocols) {
            this(supportedProtocols, null);
        }

        FakeSSLSocket(String[] supportedProtocols, IOException closeFailure) {
            this.supportedProtocols = supportedProtocols.clone();
            this.closeFailure = closeFailure;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[] {"TLS_FAKE_SUPPORTED"};
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return supportedProtocols;
        }

        @Override
        public String[] getEnabledProtocols() {
            return enabledProtocols;
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            setEnabledProtocolsCalls++;
            enabledProtocols = protocols.clone();
        }

        @Override
        public SSLSession getSession() {
            return null;
        }

        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        }

        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        }

        @Override
        public void startHandshake() throws IOException {
        }

        @Override
        public void setUseClientMode(boolean mode) {
            useClientMode = mode;
        }

        @Override
        public boolean getUseClientMode() {
            return useClientMode;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            needClientAuth = need;
        }

        @Override
        public boolean getNeedClientAuth() {
            return needClientAuth;
        }

        @Override
        public void setWantClientAuth(boolean want) {
            wantClientAuth = want;
        }

        @Override
        public boolean getWantClientAuth() {
            return wantClientAuth;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            enableSessionCreation = flag;
        }

        @Override
        public boolean getEnableSessionCreation() {
            return enableSessionCreation;
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class FakePlainSocket extends Socket {
        private boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
        }
    }
}
