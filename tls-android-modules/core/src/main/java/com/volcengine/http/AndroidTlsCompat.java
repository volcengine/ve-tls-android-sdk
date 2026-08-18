package com.volcengine.http;

import android.os.Build;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/** Enables the TLS 1.2/SNI behavior missing from Android 4.x platform JSSE. */
final class AndroidTlsCompat {
    private static final int MODERN_PROVIDER_API = 21;
    private static final String[] TLS_PROTOCOLS = new String[] {
            "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1"
    };

    private static volatile Provider legacyProvider;

    private AndroidTlsCompat() {
    }

    static void configure(OkHttpClient.Builder builder) {
        if (builder == null || !usesLegacyProvider()) {
            return;
        }
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            X509TrustManager trustManager = findTrustManager(trustManagerFactory.getTrustManagers());

            SSLContext context = SSLContext.getInstance("TLS", getLegacyProvider());
            context.init(null, new TrustManager[] {trustManager}, new SecureRandom());
            builder.sslSocketFactory(new TlsCompatibleSocketFactory(context.getSocketFactory()), trustManager);
        } catch (GeneralSecurityException | UnsatisfiedLinkError e) {
            throw new IllegalStateException("failed to initialize Android legacy TLS", e);
        }
    }

    private static boolean usesLegacyProvider() {
        int sdk = Build.VERSION.SDK_INT;
        return sdk >= Build.VERSION_CODES.JELLY_BEAN && sdk < MODERN_PROVIDER_API;
    }

    private static X509TrustManager findTrustManager(TrustManager[] trustManagers) {
        if (trustManagers != null) {
            for (TrustManager trustManager : trustManagers) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
        }
        throw new IllegalStateException("default X509TrustManager is unavailable");
    }

    private static Provider getLegacyProvider() {
        Provider current = legacyProvider;
        if (current != null) {
            return current;
        }
        synchronized (AndroidTlsCompat.class) {
            current = legacyProvider;
            if (current == null) {
                if (!Conscrypt.isAvailable()) {
                    throw new IllegalStateException("bundled Conscrypt native library is unavailable");
                }
                current = Conscrypt.newProvider();
                legacyProvider = current;
            }
            return current;
        }
    }

    private static final class TlsCompatibleSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        TlsCompatibleSocketFactory(SSLSocketFactory delegate) {
            if (delegate == null) {
                throw new NullPointerException("delegate == null");
            }
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
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(socket, host, port, autoClose), host);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port), host);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, localHost, localPort), host);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port), null);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return configure(delegate.createSocket(address, port, localAddress, localPort), null);
        }

        private Socket configure(Socket socket, String host) {
            if (!(socket instanceof SSLSocket)) {
                return socket;
            }
            SSLSocket sslSocket = (SSLSocket) socket;
            String[] protocols = selectProtocols(sslSocket.getSupportedProtocols());
            if (protocols.length > 0) {
                sslSocket.setEnabledProtocols(protocols);
            }
            configureServerName(sslSocket, host);
            return sslSocket;
        }

        private static String[] selectProtocols(String[] supportedProtocols) {
            java.util.ArrayList<String> selected = new java.util.ArrayList<String>();
            if (supportedProtocols != null) {
                for (String preferred : TLS_PROTOCOLS) {
                    for (String supported : supportedProtocols) {
                        if (preferred.equals(supported)) {
                            selected.add(preferred);
                            break;
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN && selected.contains("TLSv1.2")) {
                return new String[] {"TLSv1.2"};
            }
            return selected.toArray(new String[selected.size()]);
        }

        private static void configureServerName(SSLSocket sslSocket, String host) {
            if (host == null || host.length() == 0) {
                return;
            }
            if (Conscrypt.isConscrypt(sslSocket)) {
                Conscrypt.setHostname(sslSocket, host);
                return;
            }
            try {
                Method setHostname = findServerNameSetter(sslSocket.getClass());
                setHostname.setAccessible(true);
                setHostname.invoke(sslSocket, host);
            } catch (Exception ignored) {
                // Some platform implementations configure SNI without exposing a setter.
            }
        }

        private static Method findServerNameSetter(Class<?> socketClass) throws NoSuchMethodException {
            try {
                return socketClass.getMethod("setHostname", String.class);
            } catch (NoSuchMethodException ignored) {
                Class<?> current = socketClass;
                while (current != null) {
                    try {
                        return current.getDeclaredMethod("setHostname", String.class);
                    } catch (NoSuchMethodException ignoredInSuperclass) {
                        current = current.getSuperclass();
                    }
                }
                throw new NoSuchMethodException("setHostname");
            }
        }
    }
}
