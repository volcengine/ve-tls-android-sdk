package com.volcengine.tls.android.producer.internal;

import android.os.Handler;
import android.os.Looper;

import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerResult;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class CallbackDispatcher {
    private final LogProducerCallback callback;
    private final boolean callbackFromSenderThread;
    private final Executor callbackExecutor;
    private final AtomicInteger dispatchFailureCount = new AtomicInteger();
    private final AtomicReference<String> lastDispatchFailureMessage = new AtomicReference<>();

    CallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread) {
        this(callback, callbackFromSenderThread, createMainThreadExecutor());
    }

    CallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread, Executor callbackExecutor) {
        this.callback = callback;
        this.callbackFromSenderThread = callbackFromSenderThread;
        if (callback != null && !callbackFromSenderThread && callbackExecutor == null) {
            throw new IllegalStateException("main-thread callback mode unavailable");
        }
        this.callbackExecutor = callbackFromSenderThread ? null : callbackExecutor;
    }

    void dispatch(
            int nativeResult,
            int httpCode,
            String requestId,
            String errorCode,
            String errorMessage,
            int transportKind,
            int transportCode,
            boolean retryable,
            long logBytes,
            long compressedBytes,
            long startId,
            long endId) {
        if (callback == null) {
            return;
        }

        LogProducerResult result = ResultMapper.map(
                nativeResult,
                httpCode,
                requestId,
                errorCode,
                errorMessage,
                transportKind,
                transportCode,
                retryable,
                logBytes,
                compressedBytes,
                startId,
                endId);

        if (callbackFromSenderThread) {
            callback.onCompletion(result);
            return;
        }

        if (callbackExecutor == null) {
            reportDispatchFailure("main-thread callback executor unavailable", null, result);
            return;
        }

        try {
            callbackExecutor.execute(() -> callback.onCompletion(result));
        } catch (RuntimeException e) {
            reportDispatchFailure("main-thread callback dispatch rejected", e, result);
        }
    }

    int getDispatchFailureCount() {
        return dispatchFailureCount.get();
    }

    String getLastDispatchFailureMessage() {
        return lastDispatchFailureMessage.get();
    }

    private static Executor createMainThreadExecutor() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            return null;
        }
        Handler handler = new Handler(mainLooper);
        return command -> {
            if (!handler.post(command)) {
                throw new RejectedExecutionException("main looper rejected callback dispatch");
            }
        };
    }

    private void reportDispatchFailure(String message, Throwable error, LogProducerResult result) {
        dispatchFailureCount.incrementAndGet();
        lastDispatchFailureMessage.set(message);
        System.err.println("CallbackDispatcher failure: " + message + " result=" + result.getFailureSummary());
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
