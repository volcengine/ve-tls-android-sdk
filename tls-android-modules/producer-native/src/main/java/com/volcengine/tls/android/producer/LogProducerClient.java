package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;
import com.volcengine.tls.android.producer.internal.ConfigSnapshot;
import com.volcengine.tls.android.producer.internal.JniNativeProducerBridge;
import com.volcengine.tls.android.producer.internal.ProcessUtil;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class LogProducerClient {
    private static final CountDownLatch DESTROY_NOT_STARTED = new CountDownLatch(0);
    private static final long CREATE_RETRY_INITIAL_MS = 250;
    private static final long CREATE_RETRY_MAX_MS = 5000;
    private static final ScheduledExecutorService CREATE_RETRY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tls-producer-create-retry");
                thread.setDaemon(true);
                return thread;
            });
    private final Object lifecycleLock = new Object();
    private final ConfigSnapshot config;
    private final LogProducerCallback callback;
    private final NativeProducerBridge bridge;
    private final ScheduledExecutorService createRetryExecutor;
    private volatile long producerHandle = 0;
    private volatile boolean destroyed;
    private volatile CountDownLatch destroyCompletion = DESTROY_NOT_STARTED;
    private RuntimeException createFailure;
    private ScheduledFuture<?> createRetry;
    private long createRetryDelayMs = CREATE_RETRY_INITIAL_MS;

    public LogProducerClient(LogProducerConfig config) {
        this(config, null);
    }

    public LogProducerClient(LogProducerConfig config, LogProducerCallback callback) {
        this(config, callback, ProcessUtil.getCurrentProcessName(), null);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, NativeProducerBridge bridge) {
        this(config, callback, ProcessUtil.getCurrentProcessName(), bridge);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, String processName, NativeProducerBridge bridge) {
        this(config, callback, processName, bridge, CREATE_RETRY_EXECUTOR);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, String processName,
            NativeProducerBridge bridge, ScheduledExecutorService createRetryExecutor) {
        if (config != null) {
            validatePersistentSetup(config, processName);
        }
        this.config = config == null ? null : new ConfigSnapshot(config, processName);
        if (config != null) {
            config.freeze();
        }
        this.callback = callback;
        this.bridge = bridge == null ? new JniNativeProducerBridge() : bridge;
        this.createRetryExecutor = createRetryExecutor;
    }

    static LogProducerClient forTest(LogProducerConfig config, NativeProducerBridge bridge) {
        return forTest(config, bridge, ProcessUtil.getCurrentProcessName());
    }

    static LogProducerClient forTest(LogProducerConfig config, NativeProducerBridge bridge, String processName) {
        return new LogProducerClient(config, null, processName, bridge);
    }

    public void addLog(Log log) {
        addLog(log, 0);
    }

    /**
     * If native initialization fails, this log has not been accepted. For a retryable
     * {@link LogProducerInitializationException}, this client retries initialization
     * in the background. The caller may retain and resubmit the rejected log.
     */
    public void addLog(Log log, int flush) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.addLog(handle, log, flush);
        }
    }

    /**
     * Updates the send target for new requests. Any request that has already entered the
     * native send path may still use the previously captured endpoint, but subsequent
     * requests should converge quickly to the refreshed endpoint/region/topic.
     * This call does not rewrite or isolate persistent backlog that already exists under
     * the configured {@code persistentFilePath}; if target identity changes, callers should
     * switch to a new persistent path to avoid replaying durable backlog to the new target.
     */
    public void updateEndpoint(String endpoint, String region, String topicId) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.updateEndpoint(handle, endpoint, region, topicId);
        }
    }

    /**
     * Transactionally updates static credentials for subsequent sends. In persistent mode,
     * a successful update also resumes records retained after an authentication failure.
     * Refresh STS credentials before expiry: retained authentication failures do not emit
     * a terminal completion callback, so that callback cannot trigger credential refresh.
     */
    public void resetSecurityToken(String accessKeyId, String accessKeySecret, String securityToken) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.resetSecurityToken(handle, accessKeyId, accessKeySecret, securityToken);
        }
    }

    public void destroyLogProducer() {
        synchronized (lifecycleLock) {
            if (destroyed) {
                return;
            }
            destroyed = true;
            if (createRetry != null) {
                createRetry.cancel(false);
                createRetry = null;
            }
            if (bridge == null) {
                return;
            }
            long handle = producerHandle;
            producerHandle = 0;
            if (handle == 0) {
                destroyCompletion = DESTROY_NOT_STARTED;
                return;
            }
            int destroyWaitMs = config == null || config.isDestroyWaitSplitConfigured() ? 0 : config.getDestroyWaitMs();
            int destroyFlusherWaitMs = config == null ? 0 : config.getDestroyFlusherWaitMs();
            int destroySenderWaitMs = config == null ? 0 : config.getDestroySenderWaitMs();
            boolean destroyWaitSplitEnabled = config != null && config.isDestroyWaitSplitConfigured();
            CountDownLatch completion = new CountDownLatch(1);
            destroyCompletion = completion;
            Thread destroyWorker = new Thread(() -> {
                try {
                    bridge.destroy(handle, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled);
                } finally {
                    completion.countDown();
                }
            }, "tls-producer-destroy");
            destroyWorker.setDaemon(false);
            destroyWorker.start();
        }
    }

    /**
     * Waits up to {@code timeoutMs} for the already-scheduled destroy work to complete.
     * A {@code false} result only means the Java-side wait timed out or was interrupted;
     * it does not imply forcible native teardown.
     */
    public boolean awaitDestroy(long timeoutMs) {
        try {
            boolean completed = destroyCompletion.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                System.err.println("LogProducerClient destroy wait timed out after " + timeoutMs + "ms");
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("LogProducerClient destroy wait interrupted");
            return false;
        }
    }

    private long ensureProducerLocked() {
        if (destroyed) {
            throw new IllegalStateException("producer destroyed");
        }
        if (createFailure != null) {
            throw createFailure;
        }
        if (producerHandle != 0) {
            return producerHandle;
        }
        if (bridge == null) {
            return 0;
        }
        if (config == null) {
            throw rememberCreateFailure(new IllegalArgumentException("config == null"));
        }
        config.validateForCreate();
        RuntimeException failure = tryCreateProducerLocked();
        if (failure != null) {
            throw failure;
        }
        return producerHandle;
    }

    private RuntimeException tryCreateProducerLocked() {
        try {
            producerHandle = bridge.create(config, callback);
        } catch (RuntimeException e) {
            return rememberCreateFailure(e);
        }
        if (producerHandle == 0) {
            return rememberCreateFailure(new IllegalStateException("native producer create failed"));
        }
        createFailure = null;
        return null;
    }

    private RuntimeException rememberCreateFailure(RuntimeException failure) {
        createFailure = failure;
        if (failure instanceof LogProducerInitializationException
                && ((LogProducerInitializationException) failure).isRetryable()) {
            WeakReference<LogProducerClient> clientReference = new WeakReference<>(this);
            createRetry = createRetryExecutor.schedule(() -> {
                LogProducerClient client = clientReference.get();
                if (client != null) {
                    client.retryCreate();
                }
            }, createRetryDelayMs, TimeUnit.MILLISECONDS);
            createRetryDelayMs = Math.min(CREATE_RETRY_MAX_MS, createRetryDelayMs * 2);
        }
        return failure;
    }

    private void retryCreate() {
        synchronized (lifecycleLock) {
            createRetry = null;
            if (!destroyed) {
                // The attempt records its outcome and schedules any subsequent retry.
                tryCreateProducerLocked();
            }
        }
    }

    private static void validatePersistentSetup(LogProducerConfig config, String processName) {
        if (!config.isPersistent()) {
            return;
        }
        if (processName == null || processName.trim().isEmpty()) {
            throw new IllegalStateException("persistent mode requires resolvable process identity");
        }
    }
}
