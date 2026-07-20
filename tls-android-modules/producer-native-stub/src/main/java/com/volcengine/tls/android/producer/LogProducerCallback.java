package com.volcengine.tls.android.producer;

@FunctionalInterface
public interface LogProducerCallback {
    void onCompletion(LogProducerResult result);
}
