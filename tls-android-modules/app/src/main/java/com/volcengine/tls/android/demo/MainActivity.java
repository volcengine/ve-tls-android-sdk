package com.volcengine.tls.android.demo;

import android.app.Activity;
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

import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;

 

 

import android.text.method.ScrollingMovementMethod;

 

 

public class MainActivity extends Activity {
    private LogProducerClient producerClient;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong counter = new AtomicLong(0);
    private static final String TAG = "TLS-Demo";
    private TextView logView; // Changed from statusText to logView
    private boolean isRunning = false;

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
                            appendLog("Sending Producer Log: " + index);
                            producerClient.sendLog(kv, new CallBack() {
                                @Override
                                public void onComplete(Result result) {
                                    if (result.isSuccess()) {
                                        appendLog("Producer Success: " + index + " attempts=" + result.getAttemptCount());
                                        handler.postDelayed(sendTask, 2000);
                                    } else {
                                        com.volcengine.model.tls.producer.Attempt a = null;
                                        java.util.List<com.volcengine.model.tls.producer.Attempt> ats = result.getAttempts();
                                        if (ats != null && ats.size() > 0) { a = ats.get(ats.size()-1); }
                                        if (a != null) {
                                            appendLog("Producer Failed: http=" + a.getHttpCode() + " code=" + String.valueOf(a.getErrorCode()) + " msg=" + String.valueOf(a.getErrorMessage()));
                                        } else {
                                            appendLog("Producer Failed");
                                        }
                                        isRunning = false;
                                    }
                                }
                            });
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

        logView = new TextView(this);
        logView.setText("Ready");
        logView.setPadding(0, 50, 0, 0);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(1000);
        logView.setVerticalScrollBarEnabled(true);

        layout.addView(startBtn);
        layout.addView(stopBtn);
        layout.addView(logView);
        setContentView(layout);
    }

    private void startLogging() {
        if (isRunning) return;
        
        java.util.Properties props = ConfigLoader.load(this);
        String endPoint = ConfigLoader.get(props, "endPoint");
        String region = ConfigLoader.get(props, "region");
        String ak = ConfigLoader.get(props, "ak");
        String sk = ConfigLoader.get(props, "sk");
        String token = ConfigLoader.get(props, "token");
        String compress = ConfigLoader.get(props, "compress");
        if (compress == null || compress.length() == 0) compress = "lz4";
        if (endPoint == null || region == null || ak == null || sk == null || ConfigLoader.get(props, "topicId") == null) {
            appendLog("Missing config: endPoint/region/ak/sk/topicId");
            return;
        }

        try {
            if (producerClient == null) {
                LogProducerConfig config = new LogProducerConfig()
                        .setEndpoint(endPoint)
                        .setRegion(region)
                        .setAccessKeyId(ak)
                        .setAccessKeySecret(sk)
                        .setSecurityToken(token)
                        .setTopicId(ConfigLoader.get(props, "topicId"))
                        .setCompressType(compress)
                        .setSendThreadCount(1)
                        .setRetryCount(3);
                producerClient = new LogProducerClient(config);
                producerClient.start();
            }
            
            isRunning = true;
            handler.post(sendTask);
            appendLog("Started Sync Client...");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start client", e);
            appendLog("Start Failed: " + String.valueOf(e));
            isRunning = false;
        }
    }

    private void stopLogging() {
        isRunning = false;
        handler.removeCallbacks(sendTask);
        if (producerClient != null) {
            try { producerClient.close(); } catch (Exception ignored) {}
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
}
