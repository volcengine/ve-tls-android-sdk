package com.volcengine.tls.android.demo;

import android.app.Activity;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;

public class BenchmarkActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView logView;
    private volatile boolean running;
    private LogProducerClient client;
    private String compressType = "lz4";
    private final AtomicLong index = new AtomicLong(0);
    private final ConcurrentHashMap<Long, Long> startMap = new ConcurrentHashMap<>();
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<Long>());
    private final AtomicLong success = new AtomicLong(0);
    private final AtomicLong failure = new AtomicLong(0);
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
        Button startZlib = new Button(this);
        startZlib.setText("Start ZLIB Benchmark");
        startZlib.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { start("zlib"); } });
        Button stopBtn = new Button(this);
        stopBtn.setText("Stop & Export");
        stopBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopAndExport(); } });
        logView = new TextView(this);
        logView.setText("Ready");
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(2000);
        layout.addView(startLz4);
        layout.addView(startZlib);
        layout.addView(stopBtn);
        layout.addView(logView);
        setContentView(layout);
    }

    private void start(String ct) {
        if (running) return;
        System.setProperty("tls.debugHeaders", "true");
        append("Debug tls.debugHeaders=true");
        compressType = ct;
        index.set(0);
        success.set(0);
        failure.set(0);
        latencies.clear();
        startMap.clear();
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
                if (cfgCompress != null && cfgCompress.length() > 0) { compressType = cfgCompress; }
                if (endPoint == null || region == null || ak == null || sk == null || topicId == null) {
                    append("Missing config: endPoint/region/ak/sk/topicId");
                    return;
                }
                int threadCount = Runtime.getRuntime().availableProcessors();
                if (threadCount < 1) { threadCount = 1; }
                if (threadCount > 2) { threadCount = 2; }
                append("Config endPoint=" + endPoint);
                append("Config region=" + region);
                append("Config topicId=" + topicId);
                append("Config compress=" + compressType);
                append("Config ak=" + maskSecret(ak));
                append("Config sk=" + maskSecret(sk));
                append("Config token=" + (token == null || token.isEmpty() ? "" : maskSecret(token)));
                append("Config sendThreadCount=" + threadCount + " retryCount=3");
                LogProducerConfig cfg = new LogProducerConfig()
                        .setEndpoint(endPoint)
                        .setRegion(region)
                        .setAccessKeyId(ak)
                        .setAccessKeySecret(sk)
                        .setSecurityToken(token)
                        .setTopicId(topicId)
                        .setCompressType(compressType)
                        .setSendThreadCount(threadCount)
                        .setRetryCount(3)
                        .setPacketLogBytes(1024 * 256)
                        .setPacketLogCount(512)
                        .setPacketTimeout(1000);
                client = new LogProducerClient(cfg);
                client.start();
            }
        } catch (Throwable t) {
            append("Start failed " + t);
            return;
        }
        running = true;
        append("Benchmark started compress=" + compressType);
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
            long st = System.currentTimeMillis();
            startMap.put(id, st);
            try {
                client.sendLog(kv, new CallBack() {
                    @Override public void onComplete(Result r) {
                        if (r.isSuccess()) {
                            long done = System.currentTimeMillis();
                            Long s = startMap.remove(fid);
                            if (s != null) { latencies.add(done - s); }
                            success.incrementAndGet();
                        } else {
                            failure.incrementAndGet();
                            com.volcengine.model.tls.producer.Attempt a = null;
                            java.util.List<com.volcengine.model.tls.producer.Attempt> ats = r.getAttempts();
                            if (ats != null && !ats.isEmpty()) { a = ats.get(ats.size() - 1); }
                            if (a != null) {
                                lastError = "Fail attempts=" + r.getAttemptCount() + " http=" + a.getHttpCode() + " code=" + String.valueOf(a.getErrorCode()) + " msg=" + String.valueOf(a.getErrorMessage());
                                if (a.getRequestId() != null && !a.getRequestId().isEmpty()) { lastError += " reqId=" + a.getRequestId(); }
                            } else {
                                lastError = "Fail attempts=" + r.getAttemptCount();
                            }
                            long c = failureLogCount.incrementAndGet();
                            if (c <= 5 || c % 100 == 0) { append(lastError); }
                        }
                    }
                });
            } catch (Throwable t) {
                failure.incrementAndGet();
                lastError = "Fail exception=" + String.valueOf(t);
                long c = failureLogCount.incrementAndGet();
                if (c <= 5 || c % 100 == 0) { append(lastError); }
            }
            if (id % 100 == 0) {
                long now = System.currentTimeMillis();
                if (now - lastReport > 1000) {
                    long ok = success.get();
                    long fail = failure.get();
                    String msg = "progress sent=" + id + " ok=" + ok + " fail=" + fail;
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
            if (client != null) { client.close(); }
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
        String summary = "compress=" + compressType + ", totalSent=" + index.get() + ", success=" + success.get() + ", failure=" + failure.get() + ", durationMs=" + duration + ", avgLatencyMs=" + avg + ", p50=" + p50 + ", p95=" + p95 + ", p99=" + p99;
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
            String jsonBody = "{\"summary\":\"" + summary + "\",\"compress\":\"" + compressType + "\",\"success\":" + success.get() + ",\"failure\":" + failure.get() + ",\"count\":" + samples.size() + "}";
            writeFile(json, jsonBody);
            StringBuilder sb = new StringBuilder();
            sb.append("latency_ms\n");
            for (Long v : samples) { sb.append(v).append('\n'); }
            writeFile(csv, sb.toString());
            append("exported to " + json.getAbsolutePath());
        } catch (Throwable t) {
            append("export failed " + t);
        }
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
}
