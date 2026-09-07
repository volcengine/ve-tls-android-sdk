package com.volcengine.tls.android.producer.internal;

import org.junit.Test;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportRetryPolicyTest {

    @Test
    public void transientIoFailuresAreRetryable() {
        assertTrue(TransportRetryPolicy.isRetryable(new SocketTimeoutException("timeout")));
        assertTrue(TransportRetryPolicy.isRetryable(new SocketException("socket")));
        assertTrue(TransportRetryPolicy.isRetryable(new UnknownHostException("host")));
        assertTrue(TransportRetryPolicy.isRetryable(new EOFException("eof")));
        assertTrue(TransportRetryPolicy.isRetryable(new InterruptedIOException("interrupted io")));
        assertTrue(TransportRetryPolicy.isRetryable(new IOException("certificate validation text")));
    }

    @Test
    public void explicitInterruptIsTerminal() {
        InterruptedIOException wrappedInterrupt = new InterruptedIOException("cancelled");
        wrappedInterrupt.initCause(new InterruptedException("cancelled"));

        assertFalse(TransportRetryPolicy.isRetryable(new InterruptedException("cancelled")));
        assertFalse(TransportRetryPolicy.isRetryable(wrappedInterrupt));
    }

    @Test
    public void interruptedIoIsTerminalWhenThreadIsInterrupted() {
        boolean wasInterrupted = Thread.currentThread().isInterrupted();
        Thread.interrupted();
        try {
            Thread.currentThread().interrupt();
            assertFalse(TransportRetryPolicy.isRetryable(new InterruptedIOException("cancelled")));
        } finally {
            Thread.interrupted();
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    public void certificateAndProtocolFailuresAreTerminal() {
        SSLHandshakeException handshake = new SSLHandshakeException("temporary timeout");
        handshake.initCause(new CertificateException("certificate"));

        assertFalse(TransportRetryPolicy.isRetryable(handshake));
        assertFalse(TransportRetryPolicy.isRetryable(new SSLPeerUnverifiedException("peer")));
        assertFalse(TransportRetryPolicy.isRetryable(new SSLProtocolException("protocol mismatch")));
        assertFalse(TransportRetryPolicy.isRetryable(new CertificateException("certificate")));
        assertFalse(TransportRetryPolicy.isRetryable(new MalformedURLException("bad url")));
        assertFalse(TransportRetryPolicy.isRetryable(new ProtocolException("protocol")));
        assertFalse(TransportRetryPolicy.isRetryable(new FileNotFoundException("local ca")));
    }

    @Test
    public void runtimeFailuresAndNullAreTerminal() {
        assertFalse(TransportRetryPolicy.isRetryable(null));
        assertFalse(TransportRetryPolicy.isRetryable(new Exception("not io")));
        assertFalse(TransportRetryPolicy.isRetryable(new IllegalArgumentException("bad argument")));
        assertFalse(TransportRetryPolicy.isRetryable(new SecurityException("denied")));
        assertFalse(TransportRetryPolicy.isRetryable(new RuntimeException("runtime")));
        assertFalse(TransportRetryPolicy.isRetryable(new AssertionError("error")));
    }

    @Test
    public void localTlsConfigurationAndNetworkPolicyFailuresAreTerminal() {
        assertFalse(TransportRetryPolicy.isRetryable(new UnknownServiceException("cleartext denied")));
        assertFalse(TransportRetryPolicy.isRetryable(new IOException(new KeyStoreException("key store"))));
    }

    @Test
    public void terminalCauseOverridesOuterRetryableIo() {
        IOException wrappedCertificate = new IOException(new CertificateException("certificate"));
        IOException wrappedHandshake = new IOException(new SSLHandshakeException("handshake"));
        IOException wrappedProtocol = new IOException(new ProtocolException("protocol"));

        assertFalse(TransportRetryPolicy.isRetryable(wrappedCertificate));
        assertFalse(TransportRetryPolicy.isRetryable(wrappedHandshake));
        assertFalse(TransportRetryPolicy.isRetryable(wrappedProtocol));
    }

    @Test
    public void transientCauseMakesAnUnknownWrapperRetryable() {
        assertTrue(TransportRetryPolicy.isRetryable(
                new Exception("wrapper", new SocketException("socket"))));
    }

    @Test
    public void causeCycleTerminatesAndRetainsKnownRetryableClassification() {
        IOException first = new IOException("first");
        IOException second = new IOException("second");
        first.initCause(second);
        second.initCause(first);

        assertTrue(TransportRetryPolicy.isRetryable(first));
    }

    @Test
    public void deepCauseChainFailsClosedAtBound() {
        Throwable root = new IOException("root");
        Throwable current = root;
        for (int i = 0; i < 64; i++) {
            Throwable next = new IOException("next");
            current.initCause(next);
            current = next;
        }

        assertFalse(TransportRetryPolicy.isRetryable(root));
    }
}
