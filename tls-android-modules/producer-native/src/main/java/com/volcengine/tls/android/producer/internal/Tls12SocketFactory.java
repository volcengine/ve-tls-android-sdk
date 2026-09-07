package com.volcengine.tls.android.producer.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Enables TLS 1.2 on legacy Android without replacing its trust configuration. */
final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    Tls12SocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return configure(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return configure(delegate.createSocket(socket, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return configure(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort) throws IOException {
        return configure(delegate.createSocket(host, port, localHost, localPort));
    }

    private static Socket configure(Socket socket) throws IOException {
        try {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                for (String protocol : ssl.getSupportedProtocols()) {
                    if ("TLSv1.2".equals(protocol)) {
                        // No fallback to obsolete protocol versions on API 16-19.
                        ssl.setEnabledProtocols(new String[] {"TLSv1.2"});
                        return socket;
                    }
                }
            }
            throw new SSLProtocolException("TLS 1.2 is unavailable on this socket provider");
        } catch (IOException | RuntimeException e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Preserve the original TLS failure, including on pre-19 custom builds.
                }
            }
            throw e;
        }
    }

    static final class Cache {
        private SSLSocketFactory delegate;
        private SSLSocketFactory wrapped;

        synchronized SSLSocketFactory forApi(SSLSocketFactory factory, int api) {
            if (api < 16 || api >= 20) {
                return factory;
            }
            // Keep factory identity stable so HTTP connection pooling can reuse TLS connections.
            if (delegate != factory || wrapped == null) {
                delegate = factory;
                wrapped = new Tls12SocketFactory(factory);
            }
            return wrapped;
        }
    }
}
