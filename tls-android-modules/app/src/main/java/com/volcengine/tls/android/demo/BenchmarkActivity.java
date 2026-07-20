package com.volcengine.tls.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.tls.android.producer.LogProducerResult;

public class BenchmarkActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView logView;
    private volatile boolean running;
    private LogProducerClient client;
    private LogProducerConfig.CompressType compressType = LogProducerConfig.CompressType.LZ4;
    private final AtomicLong index = new AtomicLong(0);
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<Long>());
    private final AtomicLong acceptedLogs = new AtomicLong(0);
    private final AtomicLong rejectedLogs = new AtomicLong(0);
    private final AtomicLong callbackSuccess = new AtomicLong(0);
    private final AtomicLong callbackFailure = new AtomicLong(0);
    private long beginTs;
    private volatile String lastError;
    private final AtomicLong failureLogCount = new AtomicLong(0);
    private static String maskSecret(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= 8) return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        Button startLz4 = new Button(this);
        startLz4.setText("Start LZ4 Benchmark");
        startLz4.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { start("lz4"); } });
        Button startNone = new Button(this);
        startNone.setText("Start NONE Benchmark");
        startNone.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { start("none"); } });
        Button stopBtn = new Button(this);
        stopBtn.setText("Stop & Export");
        stopBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopAndExport(); } });
        Button configBtn = new Button(this);
        configBtn.setText("Open Config");
        configBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openConfigPage(); }
        });
        Button formalBtn = new Button(this);
        formalBtn.setText("Open Formal Benchmark");
        formalBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFormalBenchmarkPage(); }
        });
        logView = new TextView(this);
        logView.setText("Ready");
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(2000);
        layout.addView(startLz4);
        layout.addView(startNone);
        layout.addView(stopBtn);
        layout.addView(configBtn);
        layout.addView(formalBtn);
        layout.addView(logView);
        setContentView(layout);
    }

    private void start(String ct) {
        if (running) return;
        System.setProperty("tls.debugHeaders", "true");
        append("Debug tls.debugHeaders=true");
        compressType = parseCompressType(ct);
        index.set(0);
        acceptedLogs.set(0);
        rejectedLogs.set(0);
        callbackSuccess.set(0);
        callbackFailure.set(0);
        latencies.clear();
        lastError = null;
        failureLogCount.set(0);
        beginTs = System.currentTimeMillis();
        try {
            if (client == null) {
                java.util.Properties props = ConfigLoader.load(this);
                String endPoint = ConfigLoader.get(props, "endPoint");
                String region = ConfigLoader.get(props, "region");
                String ak = ConfigLoader.get(props, "ak");
                String sk = ConfigLoader.get(props, "sk");
                String token = ConfigLoader.get(props, "token");
                String topicId = ConfigLoader.get(props, "topicId");
                String cfgCompress = ConfigLoader.get(props, "compress");
                if (cfgCompress != null && cfgCompress.length() > 0) { compressType = parseCompressType(cfgCompress); }
                if (!ConfigLoader.hasRequiredConfig(props)) {
                    append("Missing config: endPoint/region/ak/sk/topicId");
                    openConfigPage();
                    return;
                }
                int threadCount = Runtime.getRuntime().availableProcessors();
                if (threadCount < 1) { threadCount = 1; }
                if (threadCount > 2) { threadCount = 2; }
                append("Config endPoint=" + endPoint);
                append("Config region=" + region);
                append("Config topicId=" + topicId);
                append("Config compress=" + compressType.name());
                append("Config ak=" + maskSecret(ak));
                append("Config sk=" + maskSecret(sk));
                append("Config token=" + (token == null || token.isEmpty() ? "" : maskSecret(token)));
                append("Config sendThreadCount=" + threadCount + " retryMaxAttempts=3");
                LogProducerConfig cfg = new LogProducerConfig()
                        .setEndpoint(endPoint)
                        .setRegion(region)
                        .setAccessKeyId(ak)
                        .setAccessKeySecret(sk)
                        .setSecurityToken(token)
                        .setTopicId(topicId)
                        .setCompressType(compressType)
                        .setSendThreadCount(threadCount)
                        .setRetryMaxAttempts(3)
                        .setPacketLogBytes(1024 * 256)
                        .setPacketLogCount(512)
                        .setPacketTimeoutMs(1000);
                client = new LogProducerClient(cfg, this::handleCompletion);
            }
        } catch (Throwable t) {
            append("Start failed " + t);
            return;
        }
        running = true;
        append("Benchmark started compress=" + compressType.name());
        new Thread(new Runnable() {
            @Override public void run() { generateLoad(); }
        }, "benchmark-load").start();
    }

    private void generateLoad() {
        long target = 5000;
        long lastReport = System.currentTimeMillis();
        while (running && index.get() < target) {
            long id = index.incrementAndGet();
            final long fid = id;
            Map<String,String> kv = new HashMap<>();
            kv.put("id", String.valueOf(id));
            kv.put("ts", String.valueOf(System.currentTimeMillis()));
            kv.put("msg", "benchmark payload " + id);
            long enqueueStart = System.currentTimeMillis();
            try {
                Log log = new Log()
                        .putContents(kv)
                        .setLogTime(System.currentTimeMillis());
                client.addLog(log);
                latencies.add(System.currentTimeMillis() - enqueueStart);
                acceptedLogs.incrementAndGet();
            } catch (Throwable t) {
                rejectedLogs.incrementAndGet();
                lastError = "Enqueue fail exception=" + String.valueOf(t);
                long c = failureLogCount.incrementAndGet();
                if (c <= 5 || c % 100 == 0) { append(lastError); }
            }
            if (id % 100 == 0) {
                long now = System.currentTimeMillis();
                if (now - lastReport > 1000) {
                    String msg = "progress attempted=" + id
                            + " accepted=" + acceptedLogs.get()
                            + " rejected=" + rejectedLogs.get()
                            + " callbackOk=" + callbackSuccess.get()
                            + " callbackFail=" + callbackFailure.get();
                    if (lastError != null && !lastError.isEmpty()) { msg += " last=" + lastError; }
                    append(msg);
                    lastReport = now;
                }
            }
        }
        append("load finished sent=" + index.get());
    }

    private void stopAndExport() {
        running = false;
        try {
            if (client != null) { client.destroyLogProducer(); }
        } catch (Throwable ignored) {}
        client = null;
        long duration = System.currentTimeMillis() - beginTs;
        List<Long> copy = new ArrayList<>(latencies);
        Collections.sort(copy);
        long count = copy.size();
        long sum = 0;
        for (Long v : copy) { sum += v; }
        long avg = count == 0 ? 0 : sum / count;
        long p50 = percentile(copy, 50);
        long p95 = percentile(copy, 95);
        long p99 = percentile(copy, 99);
        String summary = "compress=" + compressType
                + ", totalAttempted=" + index.get()
                + ", acceptedLogs=" + acceptedLogs.get()
                + ", rejectedLogs=" + rejectedLogs.get()
                + ", callbackSuccess=" + callbackSuccess.get()
                + ", callbackFailure=" + callbackFailure.get()
                + ", durationMs=" + duration
                + ", avgEnqueueLatencyMs=" + avg
                + ", p50EnqueueLatencyMs=" + p50
                + ", p95EnqueueLatencyMs=" + p95
                + ", p99EnqueueLatencyMs=" + p99;
        append(summary);
        exportReport(summary, copy);
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int)Math.ceil(p / 100.0 * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx);
    }

    private void exportReport(String summary, List<Long> samples) {
        try {
            File dir = getExternalFilesDir("benchmark");
            if (dir == null) dir = getFilesDir();
            String ts = String.valueOf(System.currentTimeMillis());
            File json = new File(dir, "report_" + ts + ".json");
            File csv = new File(dir, "samples_" + ts + ".csv");
            String jsonBody = "{\"summary\":\"" + summary
                    + "\",\"compress\":\"" + compressType
                    + "\",\"acceptedLogs\":" + acceptedLogs.get()
                    + ",\"rejectedLogs\":" + rejectedLogs.get()
                    + ",\"callbackSuccess\":" + callbackSuccess.get()
                    + ",\"callbackFailure\":" + callbackFailure.get()
                    + ",\"count\":" + samples.size()
                    + "}";
            writeFile(json, jsonBody);
            StringBuilder sb = new StringBuilder();
            sb.append("enqueue_latency_ms\n");
            for (Long v : samples) { sb.append(v).append('\n'); }
            writeFile(csv, sb.toString());
            append("exported to " + json.getAbsolutePath());
        } catch (Throwable t) {
            append("export failed " + t);
        }
    }

    private void handleCompletion(LogProducerResult result) {
        if (result == null) {
            callbackFailure.incrementAndGet();
            lastError = "Callback fail result=null";
            long c = failureLogCount.incrementAndGet();
            if (c <= 5 || c % 100 == 0) { append(lastError); }
            return;
        }

        if (result.isSuccess()) {
            callbackSuccess.incrementAndGet();
            return;
        }

        callbackFailure.incrementAndGet();
        lastError = formatFailure(result);
        long c = failureLogCount.incrementAndGet();
        if (c <= 5 || c % 100 == 0) { append(lastError); }
    }

    private String formatFailure(LogProducerResult result) {
        return "Callback fail " + result.getFailureSummary();
    }

    private void writeFile(File f, String body) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(body.getBytes(StandardCharsets.UTF_8));
        fos.flush();
        fos.close();
    }

    private void append(final String msg) {
        handler.post(new Runnable() { @Override public void run() { if (logView != null) { logView.append("\n" + msg); int l = logView.getLayout() == null ? 0 : logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight(); if (l > 0) { logView.scrollTo(0, l); } } } });
    }

    private static LogProducerConfig.CompressType parseCompressType(String ct) {
        if ("none".equalsIgnoreCase(ct)) {
            return LogProducerConfig.CompressType.NONE;
        }
        if ("lz4".equalsIgnoreCase(ct)) {
            return LogProducerConfig.CompressType.LZ4;
        }
        throw new IllegalArgumentException("unsupported compress type: " + ct);
    }

    private void openConfigPage() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    private void openFormalBenchmarkPage() {
        startActivity(new Intent(this, FormalBenchmarkActivity.class));
    }
}
