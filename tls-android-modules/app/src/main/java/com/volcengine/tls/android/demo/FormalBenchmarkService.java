package com.volcengine.tls.android.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class FormalBenchmarkService extends Service {
    static final String ACTION_EVENT = "com.volcengine.tls.android.demo.FORMAL_BENCHMARK_EVENT";
    private static final String ACTION_START = "com.volcengine.tls.android.demo.action.START_FORMAL_BENCHMARK";
    private static final String ACTION_STOP = "com.volcengine.tls.android.demo.action.STOP_FORMAL_BENCHMARK";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_DONE = "done";
    private static final String EXTRA_RESULT_DIR = "resultDir";
    private static final String EXTRA_RUN_ID = "runId";
    private static final String EXTRA_PRESET = "preset";
    private static final String CHANNEL_ID = "formal-benchmark";
    private static final int NOTIFICATION_ID = 1002;

    private static volatile boolean running;
    private static volatile String lastRunId = "";
    private static volatile String lastResultDir = "";

    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile String activePreset = FormalBenchmarkPlan.PRESET_DEFAULT;

    public static void start(Context context) {
        start(context, FormalBenchmarkPlan.PRESET_DEFAULT);
    }

    public static void start(Context context, String preset) {
        Intent intent = new Intent(context, FormalBenchmarkService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PRESET, preset);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, FormalBenchmarkService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isRunning() {
        return running;
    }

    public static String lastRunId() {
        return lastRunId;
    }

    public static String lastResultDir() {
        return lastResultDir;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cancelRequested.set(true);
            broadcast("Stop requested", false, null, null);
            return START_NOT_STICKY;
        }
        if (running) {
            broadcast("Formal benchmark already running", false, lastRunId, lastResultDir);
            return START_NOT_STICKY;
        }
        activePreset = intent == null ? FormalBenchmarkPlan.PRESET_DEFAULT : intent.getStringExtra(EXTRA_PRESET);
        if (activePreset == null || activePreset.trim().isEmpty()) {
            activePreset = FormalBenchmarkPlan.PRESET_DEFAULT;
        }
        running = true;
        cancelRequested.set(false);
        createChannelIfNeeded();
        startForeground(NOTIFICATION_ID, buildNotification("Formal benchmark starting"));
        new Thread(this::runBenchmark, "formal-benchmark-service").start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelRequested.set(true);
        running = false;
        super.onDestroy();
    }

    private void runBenchmark() {
        try {
            Properties props = ConfigLoader.load(this);
            if (!ConfigLoader.hasRequiredConfig(props)) {
                broadcast("Missing config: endPoint/region/ak/sk/topicId", true, null, null);
                return;
            }
            FormalBenchmarkRunner.RunArtifacts artifacts = FormalBenchmarkRunner.runAll(
                    this,
                    props,
                    cancelRequested,
                    this::onEvent,
                    FormalBenchmarkPlan.scenariosForPreset(activePreset));
            lastRunId = artifacts.runId;
            lastResultDir = artifacts.resultDir.getAbsolutePath();
            broadcast("Formal benchmark finished, summary=" + artifacts.summaryFile.getAbsolutePath(),
                    true,
                    artifacts.runId,
                    artifacts.resultDir.getAbsolutePath());
        } catch (Throwable t) {
            broadcast("Formal benchmark failed: " + String.valueOf(t), true, null, null);
        } finally {
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void onEvent(String message) {
        broadcast(message, false, lastRunId, lastResultDir);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(message));
        }
    }

    private void broadcast(String message, boolean done, String runId, String resultDir) {
        Intent intent = new Intent(ACTION_EVENT);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_DONE, done);
        if (runId != null) {
            intent.putExtra(EXTRA_RUN_ID, runId);
        }
        if (resultDir != null) {
            intent.putExtra(EXTRA_RESULT_DIR, resultDir);
        }
        sendBroadcast(intent);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("TLS Formal Benchmark")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "TLS Formal Benchmark", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }
}
