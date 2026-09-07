package com.volcengine.tls.android.producer.internal;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.UnknownServiceException;
import java.security.GeneralSecurityException;
import java.util.IdentityHashMap;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

/** Classifies Java transport failures for the native producer retry decision. */
public final class TransportRetryPolicy {
    private static final int MAX_CAUSE_DEPTH = 64;

    private TransportRetryPolicy() {
    }

    public static boolean isRetryable(Throwable failure) {
        if (failure == null) {
            return false;
        }

        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<>();
        Throwable current = failure;
        boolean sawRetryableIo = false;
        for (int depth = 0; current != null; depth++) {
            if (depth >= MAX_CAUSE_DEPTH) {
                return false;
            }
            if (visited.put(current, Boolean.TRUE) != null) {
                break;
            }

            if (isTerminal(current)) {
                return false;
            }
            if (current instanceof IOException) {
                // This includes the transient socket, DNS, EOF, and timeout types.
                sawRetryableIo = true;
            }

            try {
                current = current.getCause();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return sawRetryableIo;
    }

    private static boolean isTerminal(Throwable failure) {
        if (failure instanceof SSLHandshakeException
                || failure instanceof SSLPeerUnverifiedException
                || failure instanceof SSLProtocolException
                || failure instanceof GeneralSecurityException
                || failure instanceof MalformedURLException
                || failure instanceof ProtocolException
                || failure instanceof UnknownServiceException
                || failure instanceof FileNotFoundException
                || failure instanceof InterruptedException
                || failure instanceof RuntimeException
                || failure instanceof Error) {
            return true;
        }
        return failure instanceof InterruptedIOException
                && Thread.currentThread().isInterrupted();
    }
}
