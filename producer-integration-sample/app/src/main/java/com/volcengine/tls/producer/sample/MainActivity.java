package com.volcengine.tls.producer.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.tls.android.producer.LogProducerResult;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final String TAG = "TLS-VERIFY";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::runVerification, "tls-verify-runner").start();
    }

    private void runVerification() {
        long begin = System.currentTimeMillis();
        VerificationConfig config;
        try {
            config = VerificationConfig.fromMap(intentMap(getIntent()), buildDefaults());
        } catch (Throwable t) {
            publishReport(VerificationReport.syncFailure("config", 0, String.valueOf(t), System.currentTimeMillis() - begin));
            finishOnMainThread();
            return;
        }

        VerificationReport report;
        switch (config.getScenario()) {
            case BURST:
            case SEND_ONCE:
                report = runSendScenario(config, begin);
                break;
            case UPDATE_ENDPOINT_SMOKE:
                report = runUpdateEndpointScenario(config, begin);
                break;
            default:
                report = VerificationReport.syncFailure(config.getScenario().getWireName(), 0, "unsupported scenario", System.currentTimeMillis() - begin);
                break;
        }
        publishReport(report);
        finishOnMainThread();
    }

    private VerificationReport runSendScenario(VerificationConfig verificationConfig, long begin) {
        AtomicInteger callbackSuccess = new AtomicInteger();
        AtomicInteger callbackFailure = new AtomicInteger();
        AtomicReference<String> lastFailure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(verificationConfig.getSendCount());
        LogProducerClient client = null;
        int attempted = 0;
        try {
            client = new LogProducerClient(verificationConfig.toProducerConfig(), result -> handleCallback(result, callbackSuccess, callbackFailure, lastFailure, latch));
            for (int i = 0; i < verificationConfig.getSendCount(); i++) {
                client.addLog(buildLog(i), i == verificationConfig.getSendCount() - 1 ? 1 : 0);
                attempted++;
            }
            boolean timeout = !latch.await(verificationConfig.getCallbackTimeoutMs(), TimeUnit.MILLISECONDS);
            return VerificationReport.fromExecution(
                    verificationConfig.getScenario().getWireName(),
                    attempted,
                    callbackSuccess.get(),
                    callbackFailure.get(),
                    timeout,
                    lastFailure.get(),
                    System.currentTimeMillis() - begin);
        } catch (Throwable t) {
            return VerificationReport.syncFailure(verificationConfig.getScenario().getWireName(), attempted, String.valueOf(t), System.currentTimeMillis() - begin);
        } finally {
            destroyQuietly(client);
        }
    }

    private VerificationReport runUpdateEndpointScenario(VerificationConfig verificationConfig, long begin) {
        AtomicInteger callbackSuccess = new AtomicInteger();
        AtomicInteger callbackFailure = new AtomicInteger();
        AtomicReference<String> lastFailure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        LogProducerClient client = null;
        int attempted = 0;
        try {
            client = new LogProducerClient(verificationConfig.toProducerConfig(), result -> handleCallback(result, callbackSuccess, callbackFailure, lastFailure, latch));
            client.addLog(buildLog(0), 1);
            attempted++;
            client.updateEndpoint(
                    verificationConfig.getUpdateEndpointOrDefault(),
                    verificationConfig.getUpdateRegionOrDefault(),
                    verificationConfig.getUpdateTopicIdOrDefault());
            client.addLog(buildLog(1), 1);
            attempted++;
            boolean timeout = !latch.await(verificationConfig.getCallbackTimeoutMs(), TimeUnit.MILLISECONDS);
            return VerificationReport.fromExecution(
                    verificationConfig.getScenario().getWireName(),
                    attempted,
                    callbackSuccess.get(),
                    callbackFailure.get(),
                    timeout,
                    lastFailure.get(),
                    System.currentTimeMillis() - begin);
        } catch (Throwable t) {
            return VerificationReport.syncFailure(verificationConfig.getScenario().getWireName(), attempted, String.valueOf(t), System.currentTimeMillis() - begin);
        } finally {
            destroyQuietly(client);
        }
    }

    private void handleCallback(LogProducerResult result, AtomicInteger callbackSuccess, AtomicInteger callbackFailure, AtomicReference<String> lastFailure, CountDownLatch latch) {
        if (result != null && result.isSuccess()) {
            callbackSuccess.incrementAndGet();
        } else {
            callbackFailure.incrementAndGet();
            lastFailure.set(result == null ? "callback result=null" : result.getFailureSummary());
        }
        latch.countDown();
    }

    private com.volcengine.tls.android.producer.Log buildLog(int index) {
        Map<String, String> kv = new HashMap<>();
        kv.put("index", String.valueOf(index));
        kv.put("device_ts", String.valueOf(System.currentTimeMillis()));
        kv.put("message", "consumer verification payload " + index);
        return new com.volcengine.tls.android.producer.Log().putContents(kv).setLogTime(System.currentTimeMillis());
    }

    private void publishReport(VerificationReport report) {
        String json = report.toJson();
        String path = writeReport(json);
        Log.i(TAG, "VERDICT " + report.getVerdict() + " report=" + path + " payload=" + json);
    }

    private String writeReport(String body) {
        try {
            File dir = getExternalFilesDir("verification");
            if (dir == null) {
                dir = getFilesDir();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("mkdir failed: " + dir);
            }
            File target = new File(dir, "verification_" + System.currentTimeMillis() + ".json");
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return target.getAbsolutePath();
        } catch (Throwable t) {
            Log.e(TAG, "writeReport failed", t);
            return "write_failed:" + t;
        }
    }

    private void destroyQuietly(LogProducerClient client) {
        if (client == null) {
            return;
        }
        try {
            client.destroyLogProducer();
        } catch (Throwable t) {
            Log.w(TAG, "destroy failed", t);
        }
    }

    private void finishOnMainThread() {
        mainHandler.post(this::finish);
    }

    private static Map<String, String> buildDefaults() {
        Map<String, String> values = new HashMap<>();
        values.put("endpoint", BuildConfig.TLS_ENDPOINT);
        values.put("region", BuildConfig.TLS_REGION);
        values.put("topicId", BuildConfig.TLS_TOPIC_ID);
        values.put("ak", BuildConfig.TLS_AK);
        values.put("sk", BuildConfig.TLS_SK);
        values.put("token", BuildConfig.TLS_TOKEN);
        values.put("compress", "lz4");
        values.put("scenario", "send-once");
        return values;
    }

    private static Map<String, String> intentMap(Intent intent) {
        Map<String, String> values = new HashMap<>();
        if (intent == null || intent.getExtras() == null) {
            return values;
        }
        for (String key : intent.getExtras().keySet()) {
            Object value = intent.getExtras().get(key);
            if (value != null) {
                values.put(key, String.valueOf(value));
            }
        }
        return values;
    }
}
