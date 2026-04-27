package com.volcengine.tls.android.demo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FormalBenchmarkActivity extends Activity {
    private TextView logView;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null && !message.isEmpty()) {
                append(message);
            }
            String resultDir = intent.getStringExtra("resultDir");
            if (resultDir != null && !resultDir.isEmpty()) {
                append("Result dir=" + resultDir);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        Button startBtn = new Button(this);
        startBtn.setText("Start Formal Benchmark");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startFormalBenchmark(); }
        });

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Formal Benchmark");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { FormalBenchmarkService.stop(FormalBenchmarkActivity.this); }
        });

        Button configBtn = new Button(this);
        configBtn.setText("Open Config");
        configBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(FormalBenchmarkActivity.this, ConfigActivity.class)); }
        });

        logView = new TextView(this);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMaxLines(2000);
        logView.setText("Ready\n"
                + "Fixed matrix: memory/persistent x tls200/tls700 x 1/10/100/200/500 LPS\n"
                + "Defaults: LZ4, packet=1MiB/1024/3000ms, buffer=64MiB, sendThread=1, retry=90s/500ms/10s/0\n");
        if (FormalBenchmarkService.isRunning()) {
            append("Service already running runId=" + FormalBenchmarkService.lastRunId());
        } else if (!FormalBenchmarkService.lastResultDir().isEmpty()) {
            append("Last result dir=" + FormalBenchmarkService.lastResultDir());
        }

        layout.addView(startBtn);
        layout.addView(stopBtn);
        layout.addView(configBtn);
        layout.addView(logView);
        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(FormalBenchmarkService.ACTION_EVENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void startFormalBenchmark() {
        java.util.Properties props = ConfigLoader.load(this);
        if (!ConfigLoader.hasRequiredConfig(props)) {
            append("Missing config: endPoint/region/ak/sk/topicId");
            startActivity(new Intent(this, ConfigActivity.class));
            return;
        }
        append("Starting formal benchmark service");
        FormalBenchmarkService.start(this);
    }

    private void append(String line) {
        if (logView == null) {
            return;
        }
        logView.append("\n" + line);
        int scrollAmount = logView.getLayout() == null ? 0 : logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
        if (scrollAmount > 0) {
            logView.scrollTo(0, scrollAmount);
        }
    }
}
