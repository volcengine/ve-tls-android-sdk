package com.volcengine.tls.android.empty;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;

import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong counter = new AtomicLong(0);
    private TextView logView;
    private boolean isRunning = false;

    private final Runnable sendTask = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                long index = counter.incrementAndGet();
                appendLog("Sending Producer Log: " + index);
                appendLog("Producer Success: " + index + " attempts=1");
                handler.postDelayed(this, 2000);
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
        startBtn.setText("Start Sending Logs");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startLogging(); }
        });

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Sending Logs");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { stopLogging(); }
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
        isRunning = true;
        handler.post(sendTask);
        appendLog("Started Sync Client...");
    }

    private void stopLogging() {
        isRunning = false;
        handler.removeCallbacks(sendTask);
        appendLog("Stopped");
    }

    private void appendLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (logView != null) {
                    logView.append("\n" + msg);
                    if (logView.getLayout() != null) {
                        int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
                        if (scrollAmount > 0) { logView.scrollTo(0, scrollAmount); }
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogging();
    }
}
