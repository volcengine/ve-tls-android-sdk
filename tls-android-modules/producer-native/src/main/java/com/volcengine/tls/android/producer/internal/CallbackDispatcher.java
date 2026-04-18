package com.volcengine.tls.android.producer.internal;

import android.os.Handler;
import android.os.Looper;

import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerResult;

import java.util.concurrent.Executor;

final class CallbackDispatcher {
    private final LogProducerCallback callback;
    private final boolean callbackFromSenderThread;
    private final Executor callbackExecutor;

    CallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread) {
        this(callback, callbackFromSenderThread, createMainThreadExecutor());
    }

    CallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread, Executor callbackExecutor) {
        this.callback = callback;
        this.callbackFromSenderThread = callbackFromSenderThread;
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
            long logBytes,
            long compressedBytes) {
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
                logBytes,
                compressedBytes);

        if (callbackFromSenderThread || callbackExecutor == null) {
            callback.onCompletion(result);
            return;
        }

        callbackExecutor.execute(() -> callback.onCompletion(result));
    }

    private static Executor createMainThreadExecutor() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            return null;
        }
        Handler handler = new Handler(mainLooper);
        return command -> {
            if (!handler.post(command)) {
                command.run();
            }
        };
    }
}
