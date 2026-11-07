package com.volcengine.tls.android.producer;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import com.volcengine.tls.android.producer.internal.ProcessUtil;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.*;

/** Runs an actual SDK owner in a separate Android process for interruption tests. */
public final class RecoveryProcessFixture implements AutoCloseable {
    private static final int INITIALIZE = 1;
    private static final int ADD = 2;
    private static final int OPEN = 3;
    private static final int STOP = 4;
    private static final int READY = 5;
    private static final int RESULT = 6;
    private static final int STOPPED = 7;
    private static final int ERROR = 8;
    private static final int SIGCONT = 18;
    private static final int SIGSTOP = 19;

    final BlockingQueue<LogProducerResult> results = new LinkedBlockingQueue<>();
    private final BlockingQueue<Bundle> replies = new LinkedBlockingQueue<>();
    private final CountDownLatch connected = new CountDownLatch(1);
    private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        Bundle data = new Bundle(message.getData());
        data.putInt("message", message.what);
        if (message.what == RESULT) {
            results.add(new LogProducerResult(LogProducerResult.Code.valueOf(data.getString("code")),
                    null, null, null, data.getInt("http"), 0, 0, 0, 0,
                    data.getBoolean("retryable"), data.getLong("start"), data.getLong("end")));
        } else {
            replies.add(data);
        }
        return true;
    }));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBinder = binder;
            sender = new Messenger(binder);
            connected.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) { }
    };
    private volatile IBinder serviceBinder;
    private volatile Messenger sender;
    private boolean bound;
    private boolean stopped;
    private boolean paused;
    private int pid;
    private String processName;

    RecoveryProcessFixture(String endpoint, File dir,
            LogProducerConfig.PersistentDurability durability) throws Exception {
        bound = context.bindService(new Intent(context, OwnerService.class), connection, Context.BIND_AUTO_CREATE);
        assertTrue("cannot bind recovery owner service", bound);
        try {
            assertTrue("owner service did not connect", connected.await(10, TimeUnit.SECONDS));
            Bundle args = new Bundle();
            args.putString("endpoint", endpoint);
            args.putString("dir", dir.getAbsolutePath());
            args.putString("durability", durability.name());
            send(INITIALIZE, args);
            Bundle ready = reply(READY);
            pid = ready.getInt("pid");
            processName = ready.getString("processName");
            assertTrue("owner must run in another process", pid > 0 && pid != Process.myPid());
            assertNotNull("owner process name missing", processName);
            android.util.Log.i("RecoveryProcessFixture", "owner started pid=" + pid
                    + " process=" + processName + " durability=" + durability);
        } catch (Exception | AssertionError e) {
            context.unbindService(connection);
            bound = false;
            throw e;
        }
    }

    int pid() { return pid; }
    String processName() { return processName; }

    void add(String id, long timeMs) throws Exception {
        Bundle args = new Bundle();
        args.putString("id", id);
        args.putLong("time", timeMs);
        send(ADD, args);
    }

    void open() throws Exception { send(OPEN, new Bundle()); }

    void pause() throws Exception {
        Process.sendSignal(pid, SIGSTOP);
        paused = true;
        long deadline = SystemClock.elapsedRealtime() + 5000;
        String detail = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            File taskDir = new File("/proc/" + pid + "/task");
            File[] threads = taskDir.listFiles();
            if (threads == null && Build.VERSION.SDK_INT >= 21) {
                detail = ShellThreadStates.read(pid);
                String[] lines = detail.trim().split("\\n");
                boolean allStopped = lines.length > 1
                        && lines[0].trim().matches("PID\\s+TID\\s+STAT");
                for (int i = 1; i < lines.length; i++) {
                    String[] fields = lines[i].trim().split("\\s+");
                    if (fields.length != 3 || !fields[0].equals(Integer.toString(pid))
                            || !fields[2].startsWith("T")) allStopped = false;
                }
                if (allStopped) {
                    android.util.Log.i("RecoveryProcessFixture", "all owner threads stopped pid=" + pid
                            + " threadCount=" + (lines.length - 1) + " via=ui-automation-shell");
                    return;
                }
                Thread.sleep(10);
                continue;
            }
            StringBuilder states = new StringBuilder("taskExists=").append(taskDir.exists())
                    .append(" taskReadable=").append(taskDir.canRead());
            boolean allStopped = threads != null && threads.length > 0;
            if (threads != null) {
                for (File thread : threads) {
                    try (RandomAccessFile status = new RandomAccessFile(new File(thread, "status"), "r")) {
                        String line;
                        boolean stopped = false;
                        while ((line = status.readLine()) != null) {
                            if (line.startsWith("State:")) {
                                stopped = line.contains("T");
                                states.append(' ').append(thread.getName()).append('=').append(line);
                                break;
                            }
                        }
                        if (!stopped) allStopped = false;
                    } catch (IOException exitedThread) {
                        // A thread can exit while its /proc entry is being read.
                        states.append(' ').append(thread.getName()).append('=').append(exitedThread);
                        allStopped = false;
                    }
                }
            }
            if (allStopped) {
                android.util.Log.i("RecoveryProcessFixture", "all owner threads stopped pid=" + pid
                        + " threadCount=" + threads.length);
                return;
            }
            detail = states.toString();
            Thread.sleep(10);
        }
        throw new AssertionError("owner process did not enter stopped state: " + pid + " " + detail);
    }

    // Modern Android hides sibling processes from the app UID. The instrumentation
    // shell identity can inspect each stopped thread without changing app permissions.
    private static final class ShellThreadStates {
        static String read(int pid) throws IOException {
            String command = "ps -T -p " + pid + " -o PID,TID,STAT";
            ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ParcelFileDescriptor.AutoCloseInputStream(output), "UTF-8"))) {
                StringBuilder states = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) states.append(line).append('\n');
                return states.toString();
            }
        }
    }

    void resume() {
        if (paused) {
            Process.sendSignal(pid, SIGCONT);
            paused = false;
        }
    }

    void kill() throws Exception {
        assertFalse("cannot kill a paused owner", paused);
        CountDownLatch died = new CountDownLatch(1);
        serviceBinder.linkToDeath(died::countDown, 0);
        android.util.Log.i("RecoveryProcessFixture", "sending SIGKILL pid=" + pid);
        Process.killProcess(pid);
        assertTrue("owner process did not die after SIGKILL", died.await(10, TimeUnit.SECONDS));
        android.util.Log.i("RecoveryProcessFixture", "binder death confirmed pid=" + pid);
        long deadline = SystemClock.elapsedRealtime() + 5000;
        boolean exited = false;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Build.VERSION.SDK_INT >= 21) {
                exited = ShellThreadStates.read(pid).trim().matches("PID\\s+TID\\s+STAT");
            } else {
                exited = !new File("/proc/" + pid).exists();
            }
            if (exited) break;
            Thread.sleep(10);
        }
        assertTrue("killed process still exists: " + pid, exited);
        android.util.Log.i("RecoveryProcessFixture", "process exit confirmed pid=" + pid);
        stopped = true;
        context.unbindService(connection);
        bound = false;
    }

    void stop() throws Exception {
        if (!stopped) {
            send(STOP, new Bundle());
            reply(STOPPED);
            stopped = true;
        }
    }

    private void send(int what, Bundle args) throws Exception {
        Message message = Message.obtain(null, what);
        message.setData(args);
        message.replyTo = receiver;
        sender.send(message);
    }

    private Bundle reply(int expected) throws Exception {
        Bundle reply = replies.poll(15, TimeUnit.SECONDS);
        assertNotNull("owner service reply timed out: " + expected, reply);
        if (reply.getInt("message") == ERROR) throw new AssertionError(reply.getString("error"));
        assertEquals(expected, reply.getInt("message"));
        return reply;
    }

    @Override public void close() throws Exception {
        resume();
        try {
            if (bound && !stopped) stop();
        } finally {
            if (bound) context.unbindService(connection);
            bound = false;
        }
    }

    /** Declared only in the instrumentation manifest, not in the SDK application. */
    public static final class OwnerService extends Service {
        private LogProducerClient client;
        private String endpoint;
        private Messenger receiver;
        private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            try {
                Bundle args = message.getData();
                if (message.what == INITIALIZE) {
                    receiver = message.replyTo;
                    endpoint = args.getString("endpoint");
                    LogProducerConfig.PersistentDurability durability =
                            LogProducerConfig.PersistentDurability.valueOf(args.getString("durability"));
                    LogProducerConfig config = ProducerRecoveryInstrumentedTest.recoveryGapConfig(
                            endpoint, new File(args.getString("dir")), durability);
                    client = new LogProducerClient(config, this::sendResult);
                    Bundle data = new Bundle();
                    data.putInt("pid", Process.myPid());
                    data.putString("processName", ProcessUtil.getCurrentProcessName());
                    sendReply(READY, data);
                } else if (message.what == ADD) {
                    Log log = ProducerRecoveryInstrumentedTest.event(args.getString("id"));
                    log.setLogTime(args.getLong("time"));
                    client.addLog(log, 0);
                } else if (message.what == OPEN) {
                    client.updateEndpoint(endpoint, "cn-beijing", "recovery-test");
                } else if (message.what == STOP) {
                    client.destroyLogProducer();
                    if (!client.awaitDestroy(5000)) throw new IllegalStateException("destroy timed out");
                    sendReply(STOPPED, new Bundle());
                }
            } catch (Exception e) {
                Bundle error = new Bundle();
                error.putString("error", android.util.Log.getStackTraceString(e));
                sendReply(ERROR, error);
            }
            return true;
        }));

        @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

        private void sendResult(LogProducerResult result) {
            Bundle data = new Bundle();
            data.putString("code", result.getCode().name());
            data.putInt("http", result.getHttpCode());
            data.putBoolean("retryable", result.isRetryable());
            data.putLong("start", result.getStartId());
            data.putLong("end", result.getEndId());
            sendReply(RESULT, data);
        }

        private void sendReply(int what, Bundle data) {
            try {
                Message reply = Message.obtain(null, what);
                reply.setData(data);
                receiver.send(reply);
            } catch (android.os.RemoteException e) {
                throw new IllegalStateException("test controller disconnected", e);
            }
        }
    }
}
