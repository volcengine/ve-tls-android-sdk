package com.volcengine.tls.android.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import java.util.ArrayList;
import java.util.List;

import android.text.method.ScrollingMovementMethod;

 

 

public class MainActivity extends Activity {
    private LogProducerClient producerClient;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong counter = new AtomicLong(0);
    private final AtomicLong pendingIndex = new AtomicLong(0);
    private static final String TAG = "TLS-Demo";
    private TextView logView; // Changed from statusText to logView
    private boolean isRunning = false;
    private static String maskSecret(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= 8) return "****";
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    private final Runnable sendTask = new Runnable() {
        @Override
        public void run() {
            if (producerClient != null && isRunning) {
                // Run network operation in a background thread to avoid NetworkOnMainThreadException
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            long index = counter.incrementAndGet();
                            Map<String, String> kv = new HashMap<>();
                            kv.put("index", String.valueOf(index));
                            kv.put("data", "producer client test content " + index);
                            kv.put("time", String.valueOf(System.currentTimeMillis()));
                            com.volcengine.tls.android.producer.Log log = new com.volcengine.tls.android.producer.Log()
                                    .putContents(kv)
                                    .setLogTime(System.currentTimeMillis());
                            pendingIndex.set(index);
                            appendLog("Sending Producer Log: " + index);
                            producerClient.addLog(log);
                        } catch (Exception e) {
                            appendLog("Failed: " + String.valueOf(e));
                            isRunning = false;
                        }
                    }
                }).start();
                // Removed the unconditional postDelayed here
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        Button startBtn = new Button(this);
        startBtn.setText("Start Sending Logs");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLogging();
            }
        });

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Sending Logs");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLogging();
            }
        });

        Button configBtn = new Button(this);
        configBtn.setText("Open Config");
        configBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openConfigPage();
            }
        });

        Button formalBtn = new Button(this);
        formalBtn.setText("Open Formal Benchmark");
        formalBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFormalBenchmarkPage();
            }
        });

        logView = new TextView(this);
        logView.setText("Ready");
        logView.setPadding(0, 50, 0, 0);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(1000);
        logView.setVerticalScrollBarEnabled(true);

        layout.addView(startBtn);
        layout.addView(stopBtn);
        layout.addView(configBtn);
        layout.addView(formalBtn);
        layout.addView(logView);
        setContentView(layout);
    }

    private void startLogging() {
        if (isRunning) return;
        System.setProperty("tls.debugHeaders", "true");
        appendLog("Debug tls.debugHeaders=true");
        
        java.util.Properties props = ConfigLoader.load(this);
        String endPoint = ConfigLoader.get(props, "endPoint");
        String region = ConfigLoader.get(props, "region");
        String ak = ConfigLoader.get(props, "ak");
        String sk = ConfigLoader.get(props, "sk");
        String token = ConfigLoader.get(props, "token");
        String compress = ConfigLoader.get(props, "compress");
        if (compress == null || compress.length() == 0) compress = "lz4";
        if (!ConfigLoader.hasRequiredConfig(props)) {
            appendLog("Missing config: endPoint/region/ak/sk/topicId");
            openConfigPage();
            return;
        }
        appendLog("Config endPoint=" + endPoint);
        appendLog("Config region=" + region);
        appendLog("Config topicId=" + ConfigLoader.get(props, "topicId"));
        appendLog("Config compress=" + compress);
        appendLog("Config ak=" + maskSecret(ak));
        appendLog("Config sk=" + maskSecret(sk));
        appendLog("Config token=" + (token == null || token.isEmpty() ? "" : maskSecret(token)));
        appendLog("Config sendThreadCount=1 retryMaxAttempts=3");

        try {
            if (producerClient == null) {
                final LogProducerConfig.CompressType compressType = parseCompressType(compress);
                LogProducerConfig config = new LogProducerConfig()
                        .setEndpoint(endPoint)
                        .setRegion(region)
                        .setAccessKeyId(ak)
                        .setAccessKeySecret(sk)
                        .setSecurityToken(token)
                        .setTopicId(ConfigLoader.get(props, "topicId"))
                        .setCompressType(compressType)
                        .setSendThreadCount(1)
                        .setRetryMaxAttempts(3);
                producerClient = new LogProducerClient(config, result -> {
                    long index = pendingIndex.get();
                    if (result.isSuccess()) {
                        String detail = "http=" + result.getHttpCode();
                        if (result.getRequestId() != null && !result.getRequestId().isEmpty()) {
                            detail += " reqId=" + result.getRequestId();
                        }
                        appendLog("Producer Success: " + index + " " + detail);
                        handler.postDelayed(sendTask, 2000);
                    } else {
                        appendLog("Producer Failed: " + result.getFailureSummary());
                        isRunning = false;
                    }
                });
            }
            
            isRunning = true;
            handler.post(sendTask);
            appendLog("Started Sync Client...");
        } catch (Throwable e) {
            android.util.Log.e(TAG, "Failed to start client", e);
            appendLog("Start Failed: " + String.valueOf(e));
            isRunning = false;
        }
    }

    private void stopLogging() {
        isRunning = false;
        handler.removeCallbacks(sendTask);
        if (producerClient != null) {
            try { producerClient.destroyLogProducer(); } catch (Exception ignored) {}
        }
        producerClient = null;
        appendLog("Stopped");
    }

    private void appendLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (logView != null) {
                    logView.append("\n" + msg);
                    // Auto scroll to bottom
                    int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
                    if (scrollAmount > 0) {
                        logView.scrollTo(0, scrollAmount);
                    }
                }
            }
        });
    }

    // Removed updateStatus to avoid confusion
    // private void updateStatus(final String msg) ...

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogging();
    }

    private void openConfigPage() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    private void openFormalBenchmarkPage() {
        startActivity(new Intent(this, FormalBenchmarkActivity.class));
    }

    private static LogProducerConfig.CompressType parseCompressType(String compress) {
        if ("none".equalsIgnoreCase(compress)) {
            return LogProducerConfig.CompressType.NONE;
        }
        if ("lz4".equalsIgnoreCase(compress)) {
            return LogProducerConfig.CompressType.LZ4;
        }
        throw new IllegalArgumentException("unsupported compress type: " + compress);
    }
}
