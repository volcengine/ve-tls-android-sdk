package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;

import java.util.concurrent.atomic.AtomicLong;

public final class JniNativeProducerBridge implements NativeProducerBridge {
    public static final class AddLogPerfStatsSnapshot {
        private final long javaFlattenWallMs;
        private final long nativeAddLogWallMs;
        private final long nativeUtfDupWallMs;
        private final long nativeProducerAddLogWallMs;
        private final long nativeKeyLensWallMs;
        private final long nativePersistentPathWallMs;
        private final long nativeQueuePathWallMs;
        private final long nativeTlsBatchBuilderWallMs;
        private final long nativeTlsBatchFlushWallMs;
        private final long nativeTlsBatchMergeWallMs;
        private final long nativePersistentBuilderWallMs;
        private final long nativePersistentAppendWallMs;
        private final long nativePersistentEnqueueWallMs;
        private final long nativePersistentEnqueueWaitBufferWallMs;
        private final long nativePersistentEnqueueBuilderInitWallMs;
        private final long nativePersistentEnqueueIngressPushWallMs;
        private final long nativePersistentEnqueueIngressWaitQueueWallMs;
        private final long nativePersistentEnqueueIngressBookkeepingWallMs;
        private final long nativePersistentEnqueueIngressNotifyWallMs;
        private final long nativeTlsBatchPreambleWallMs;
        private final long nativeTlsBatchBookkeepingWallMs;
        private final long nativePersistentAppendEncodeWallMs;
        private final long nativePersistentAppendStoreWallMs;
        private final long nativeTlsBatchFlushShellWallMs;
        private final long nativeTlsBatchMergeShellWallMs;
        private final long nativePersistentAppendSizingWallMs;
        private final long nativePersistentAppendCapacityWallMs;
        private final long nativePersistentAppendPostStoreWallMs;
        private final long nativePersistentAppendRetryShellWallMs;
        private final long nativePersistentAppendPrecheckWallMs;
        private final long nativePersistentAppendMetaWallMs;
        private final long nativePersistentAppendRetryPersistentMutexWallMs;
        private final long nativePersistentAppendRetrySleepWallMs;
        private final long nativePersistentAppendRetryProducerMutexWallMs;
        private final long nativePersistentAppendStoreRotateWallMs;
        private final long nativePersistentAppendStoreWriteWallMs;
        private final long nativePersistentAppendRetryProducerLockWallMs;
        private final long nativePersistentAppendRetryProducerNotifyWallMs;

        AddLogPerfStatsSnapshot(
                long javaFlattenWallMs,
                long nativeAddLogWallMs,
                long nativeUtfDupWallMs,
                long nativeProducerAddLogWallMs,
                long nativeKeyLensWallMs,
                long nativePersistentPathWallMs,
                long nativeQueuePathWallMs,
                long nativeTlsBatchBuilderWallMs,
                long nativeTlsBatchFlushWallMs,
                long nativeTlsBatchMergeWallMs,
                long nativePersistentBuilderWallMs,
                long nativePersistentAppendWallMs,
                long nativePersistentEnqueueWallMs,
                long nativePersistentEnqueueWaitBufferWallMs,
                long nativePersistentEnqueueBuilderInitWallMs,
                long nativePersistentEnqueueIngressPushWallMs,
                long nativePersistentEnqueueIngressWaitQueueWallMs,
                long nativePersistentEnqueueIngressBookkeepingWallMs,
                long nativePersistentEnqueueIngressNotifyWallMs,
                long nativeTlsBatchPreambleWallMs,
                long nativeTlsBatchBookkeepingWallMs,
                long nativePersistentAppendEncodeWallMs,
                long nativePersistentAppendStoreWallMs,
                long nativeTlsBatchFlushShellWallMs,
                long nativeTlsBatchMergeShellWallMs,
                long nativePersistentAppendSizingWallMs,
                long nativePersistentAppendCapacityWallMs,
                long nativePersistentAppendPostStoreWallMs,
                long nativePersistentAppendRetryShellWallMs,
                long nativePersistentAppendPrecheckWallMs,
                long nativePersistentAppendMetaWallMs,
                long nativePersistentAppendRetryPersistentMutexWallMs,
                long nativePersistentAppendRetrySleepWallMs,
                long nativePersistentAppendRetryProducerMutexWallMs,
                long nativePersistentAppendStoreRotateWallMs,
                long nativePersistentAppendStoreWriteWallMs,
                long nativePersistentAppendRetryProducerLockWallMs,
                long nativePersistentAppendRetryProducerNotifyWallMs) {
            this.javaFlattenWallMs = javaFlattenWallMs;
            this.nativeAddLogWallMs = nativeAddLogWallMs;
            this.nativeUtfDupWallMs = nativeUtfDupWallMs;
            this.nativeProducerAddLogWallMs = nativeProducerAddLogWallMs;
            this.nativeKeyLensWallMs = nativeKeyLensWallMs;
            this.nativePersistentPathWallMs = nativePersistentPathWallMs;
            this.nativeQueuePathWallMs = nativeQueuePathWallMs;
            this.nativeTlsBatchBuilderWallMs = nativeTlsBatchBuilderWallMs;
            this.nativeTlsBatchFlushWallMs = nativeTlsBatchFlushWallMs;
            this.nativeTlsBatchMergeWallMs = nativeTlsBatchMergeWallMs;
            this.nativePersistentBuilderWallMs = nativePersistentBuilderWallMs;
            this.nativePersistentAppendWallMs = nativePersistentAppendWallMs;
            this.nativePersistentEnqueueWallMs = nativePersistentEnqueueWallMs;
            this.nativePersistentEnqueueWaitBufferWallMs = nativePersistentEnqueueWaitBufferWallMs;
            this.nativePersistentEnqueueBuilderInitWallMs = nativePersistentEnqueueBuilderInitWallMs;
            this.nativePersistentEnqueueIngressPushWallMs = nativePersistentEnqueueIngressPushWallMs;
            this.nativePersistentEnqueueIngressWaitQueueWallMs = nativePersistentEnqueueIngressWaitQueueWallMs;
            this.nativePersistentEnqueueIngressBookkeepingWallMs = nativePersistentEnqueueIngressBookkeepingWallMs;
            this.nativePersistentEnqueueIngressNotifyWallMs = nativePersistentEnqueueIngressNotifyWallMs;
            this.nativeTlsBatchPreambleWallMs = nativeTlsBatchPreambleWallMs;
            this.nativeTlsBatchBookkeepingWallMs = nativeTlsBatchBookkeepingWallMs;
            this.nativePersistentAppendEncodeWallMs = nativePersistentAppendEncodeWallMs;
            this.nativePersistentAppendStoreWallMs = nativePersistentAppendStoreWallMs;
            this.nativeTlsBatchFlushShellWallMs = nativeTlsBatchFlushShellWallMs;
            this.nativeTlsBatchMergeShellWallMs = nativeTlsBatchMergeShellWallMs;
            this.nativePersistentAppendSizingWallMs = nativePersistentAppendSizingWallMs;
            this.nativePersistentAppendCapacityWallMs = nativePersistentAppendCapacityWallMs;
            this.nativePersistentAppendPostStoreWallMs = nativePersistentAppendPostStoreWallMs;
            this.nativePersistentAppendRetryShellWallMs = nativePersistentAppendRetryShellWallMs;
            this.nativePersistentAppendPrecheckWallMs = nativePersistentAppendPrecheckWallMs;
            this.nativePersistentAppendMetaWallMs = nativePersistentAppendMetaWallMs;
            this.nativePersistentAppendRetryPersistentMutexWallMs = nativePersistentAppendRetryPersistentMutexWallMs;
            this.nativePersistentAppendRetrySleepWallMs = nativePersistentAppendRetrySleepWallMs;
            this.nativePersistentAppendRetryProducerMutexWallMs = nativePersistentAppendRetryProducerMutexWallMs;
            this.nativePersistentAppendStoreRotateWallMs = nativePersistentAppendStoreRotateWallMs;
            this.nativePersistentAppendStoreWriteWallMs = nativePersistentAppendStoreWriteWallMs;
            this.nativePersistentAppendRetryProducerLockWallMs = nativePersistentAppendRetryProducerLockWallMs;
            this.nativePersistentAppendRetryProducerNotifyWallMs = nativePersistentAppendRetryProducerNotifyWallMs;
        }

        public long getJavaFlattenWallMs() {
            return javaFlattenWallMs;
        }

        public long getNativeAddLogWallMs() {
            return nativeAddLogWallMs;
        }

        public long getNativeUtfDupWallMs() {
            return nativeUtfDupWallMs;
        }

        public long getNativeProducerAddLogWallMs() {
            return nativeProducerAddLogWallMs;
        }

        public long getNativeKeyLensWallMs() {
            return nativeKeyLensWallMs;
        }

        public long getNativePersistentPathWallMs() {
            return nativePersistentPathWallMs;
        }

        public long getNativeQueuePathWallMs() {
            return nativeQueuePathWallMs;
        }

        public long getNativeTlsBatchBuilderWallMs() {
            return nativeTlsBatchBuilderWallMs;
        }

        public long getNativeTlsBatchFlushWallMs() {
            return nativeTlsBatchFlushWallMs;
        }

        public long getNativeTlsBatchMergeWallMs() {
            return nativeTlsBatchMergeWallMs;
        }

        public long getNativePersistentBuilderWallMs() {
            return nativePersistentBuilderWallMs;
        }

        public long getNativePersistentAppendWallMs() {
            return nativePersistentAppendWallMs;
        }

        public long getNativePersistentEnqueueWallMs() {
            return nativePersistentEnqueueWallMs;
        }

        public long getNativePersistentEnqueueWaitBufferWallMs() {
            return nativePersistentEnqueueWaitBufferWallMs;
        }

        public long getNativePersistentEnqueueBuilderInitWallMs() {
            return nativePersistentEnqueueBuilderInitWallMs;
        }

        public long getNativePersistentEnqueueIngressPushWallMs() {
            return nativePersistentEnqueueIngressPushWallMs;
        }

        public long getNativePersistentEnqueueIngressWaitQueueWallMs() {
            return nativePersistentEnqueueIngressWaitQueueWallMs;
        }

        public long getNativePersistentEnqueueIngressBookkeepingWallMs() {
            return nativePersistentEnqueueIngressBookkeepingWallMs;
        }

        public long getNativePersistentEnqueueIngressNotifyWallMs() {
            return nativePersistentEnqueueIngressNotifyWallMs;
        }

        public long getNativeTlsBatchPreambleWallMs() {
            return nativeTlsBatchPreambleWallMs;
        }

        public long getNativeTlsBatchBookkeepingWallMs() {
            return nativeTlsBatchBookkeepingWallMs;
        }

        public long getNativePersistentAppendEncodeWallMs() {
            return nativePersistentAppendEncodeWallMs;
        }

        public long getNativePersistentAppendStoreWallMs() {
            return nativePersistentAppendStoreWallMs;
        }

        public long getNativeTlsBatchFlushShellWallMs() {
            return nativeTlsBatchFlushShellWallMs;
        }

        public long getNativeTlsBatchMergeShellWallMs() {
            return nativeTlsBatchMergeShellWallMs;
        }

        public long getNativePersistentAppendSizingWallMs() {
            return nativePersistentAppendSizingWallMs;
        }

        public long getNativePersistentAppendCapacityWallMs() {
            return nativePersistentAppendCapacityWallMs;
        }

        public long getNativePersistentAppendPostStoreWallMs() {
            return nativePersistentAppendPostStoreWallMs;
        }

        public long getNativePersistentAppendRetryShellWallMs() {
            return nativePersistentAppendRetryShellWallMs;
        }

        public long getNativePersistentAppendPrecheckWallMs() {
            return nativePersistentAppendPrecheckWallMs;
        }

        public long getNativePersistentAppendMetaWallMs() {
            return nativePersistentAppendMetaWallMs;
        }

        public long getNativePersistentAppendRetryPersistentMutexWallMs() {
            return nativePersistentAppendRetryPersistentMutexWallMs;
        }

        public long getNativePersistentAppendRetrySleepWallMs() {
            return nativePersistentAppendRetrySleepWallMs;
        }

        public long getNativePersistentAppendRetryProducerMutexWallMs() {
            return nativePersistentAppendRetryProducerMutexWallMs;
        }

        public long getNativePersistentAppendStoreRotateWallMs() {
            return nativePersistentAppendStoreRotateWallMs;
        }

        public long getNativePersistentAppendStoreWriteWallMs() {
            return nativePersistentAppendStoreWriteWallMs;
        }

        public long getNativePersistentAppendRetryProducerLockWallMs() {
            return nativePersistentAppendRetryProducerLockWallMs;
        }

        public long getNativePersistentAppendRetryProducerNotifyWallMs() {
            return nativePersistentAppendRetryProducerNotifyWallMs;
        }
    }

    public static final class SenderPerfStatsSnapshot {
        private final long sendCount;
        private final long sendWallMs;
        private final long signWallMs;
        private final long httpRequestWallMs;
        private final long managerTaskCount;
        private final long managerBuildTaskWallMs;
        private final long managerPrepareTaskWallMs;
        private final long managerCompressWallMs;
        private final long managerPushTaskWallMs;

        SenderPerfStatsSnapshot(long sendCount, long sendWallMs, long signWallMs, long httpRequestWallMs,
                                long managerTaskCount, long managerBuildTaskWallMs,
                                long managerPrepareTaskWallMs, long managerCompressWallMs,
                                long managerPushTaskWallMs) {
            this.sendCount = sendCount;
            this.sendWallMs = sendWallMs;
            this.signWallMs = signWallMs;
            this.httpRequestWallMs = httpRequestWallMs;
            this.managerTaskCount = managerTaskCount;
            this.managerBuildTaskWallMs = managerBuildTaskWallMs;
            this.managerPrepareTaskWallMs = managerPrepareTaskWallMs;
            this.managerCompressWallMs = managerCompressWallMs;
            this.managerPushTaskWallMs = managerPushTaskWallMs;
        }

        public long getSendCount() {
            return sendCount;
        }

        public long getSendWallMs() {
            return sendWallMs;
        }

        public long getSignWallMs() {
            return signWallMs;
        }

        public long getHttpRequestWallMs() {
            return httpRequestWallMs;
        }

        public long getManagerTaskCount() {
            return managerTaskCount;
        }

        public long getManagerBuildTaskWallMs() {
            return managerBuildTaskWallMs;
        }

        public long getManagerPrepareTaskWallMs() {
            return managerPrepareTaskWallMs;
        }

        public long getManagerCompressWallMs() {
            return managerCompressWallMs;
        }

        public long getManagerPushTaskWallMs() {
            return managerPushTaskWallMs;
        }
    }

    interface NativeLibraryVerifier {
        void verify();
    }

    interface CreateInvoker {
        long create(CreateArgs args);
    }

    interface CallbackDispatcherFactory {
        Object create(LogProducerCallback callback, boolean callbackFromSenderThread);
    }

    static final class CreateArgs {
        private final ConfigSnapshot config;
        private final int destroyWaitMs;
        private final int destroyFlusherWaitMs;
        private final int destroySenderWaitMs;
        private final boolean destroyWaitSplitEnabled;
        private final String[] logTagKeys;
        private final String[] logTagValues;
        private final int logTagCount;
        private final Object callbackDispatcher;

        CreateArgs(
                ConfigSnapshot config,
                int destroyWaitMs,
                int destroyFlusherWaitMs,
                int destroySenderWaitMs,
                boolean destroyWaitSplitEnabled,
                String[] logTagKeys,
                String[] logTagValues,
                int logTagCount,
                Object callbackDispatcher) {
            this.config = config;
            this.destroyWaitMs = destroyWaitMs;
            this.destroyFlusherWaitMs = destroyFlusherWaitMs;
            this.destroySenderWaitMs = destroySenderWaitMs;
            this.destroyWaitSplitEnabled = destroyWaitSplitEnabled;
            this.logTagKeys = logTagKeys;
            this.logTagValues = logTagValues;
            this.logTagCount = logTagCount;
            this.callbackDispatcher = callbackDispatcher;
        }

        ConfigSnapshot getConfig() {
            return config;
        }

        int getDestroyWaitMs() {
            return destroyWaitMs;
        }

        int getDestroyFlusherWaitMs() {
            return destroyFlusherWaitMs;
        }

        int getDestroySenderWaitMs() {
            return destroySenderWaitMs;
        }

        boolean isDestroyWaitSplitEnabled() {
            return destroyWaitSplitEnabled;
        }

        String[] getLogTagKeys() {
            return logTagKeys;
        }

        String[] getLogTagValues() {
            return logTagValues;
        }

        int getLogTagCount() {
            return logTagCount;
        }

        Object getCallbackDispatcher() {
            return callbackDispatcher;
        }
    }

    private static final boolean NATIVE_LIBRARY_LOADED;
    private static final Throwable NATIVE_LIBRARY_ERROR;
    private static final boolean ADD_LOG_PERF_ENABLED = false;
    private static final AtomicLong JAVA_FLATTEN_WALL_NS = new AtomicLong();
    private static final AtomicLong NATIVE_ADD_LOG_WALL_NS = new AtomicLong();
    private volatile String defaultHashKey;
    static final int COMPRESS_TYPE_UNSPECIFIED = 0;
    static final int COMPRESS_TYPE_NONE = 1;
    static final int COMPRESS_TYPE_LZ4 = 2;
    private final NativeLibraryVerifier nativeLibraryVerifier;
    private final CreateInvoker createInvoker;
    private final CallbackDispatcherFactory callbackDispatcherFactory;

    static {
        boolean loaded = false;
        Throwable error = null;
        try {
            System.loadLibrary("tls_producer_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            error = e;
        }
        NATIVE_LIBRARY_LOADED = loaded;
        NATIVE_LIBRARY_ERROR = error;
    }

    public JniNativeProducerBridge() {
        this(
                JniNativeProducerBridge::requireNativeLibrary,
                JniNativeProducerBridge::invokeNativeCreate,
                JniNativeProducerBridge::createCallbackDispatcher);
    }

    JniNativeProducerBridge(
            NativeLibraryVerifier nativeLibraryVerifier,
            CreateInvoker createInvoker,
            CallbackDispatcherFactory callbackDispatcherFactory) {
        this.nativeLibraryVerifier = nativeLibraryVerifier;
        this.createInvoker = createInvoker;
        this.callbackDispatcherFactory = callbackDispatcherFactory;
    }

    @Override
    public long create(ConfigSnapshot config, LogProducerCallback callback) {
        return createInternal(config, callback);
    }

    @Override
    public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
        requireNativeLibrary();
        int result = nativeUpdateEndpoint(producerHandle, endpoint, region, topicId);
        if (result != 0) {
            throw new IllegalStateException("native updateEndpoint failed: " + result);
        }
    }

    @Override
    public void resetSecurityToken(long producerHandle, String accessKeyId, String accessKeySecret, String securityToken) {
        requireNativeLibrary();
        int result = nativeResetSecurityToken(producerHandle, accessKeyId, accessKeySecret, securityToken);
        if (result != 0) {
            throw new IllegalStateException("native resetSecurityToken failed: " + result);
        }
    }

    @Override
    public void addLog(long producerHandle, Log log) {
        addLog(producerHandle, log, 0);
    }

    @Override
    public void destroy(long producerHandle, int destroyWaitMs, int destroyFlusherWaitMs, int destroySenderWaitMs, boolean destroyWaitSplitEnabled) {
        if (producerHandle == 0) {
            return;
        }
        requireNativeLibrary();
        nativeDestroy(producerHandle, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled);
    }

    public static void resetSenderPerfStats() {
        requireNativeLibrary();
        nativeResetSenderPerfStats();
    }

    public static void resetAddLogPerfStats() {
        requireNativeLibrary();
        JAVA_FLATTEN_WALL_NS.set(0L);
        NATIVE_ADD_LOG_WALL_NS.set(0L);
        nativeResetAddLogPerfStats();
    }

    public static SenderPerfStatsSnapshot snapshotSenderPerfStats() {
        requireNativeLibrary();
        long[] values = nativeSnapshotSenderPerfStats();
        if (values == null || values.length < 4) {
            return new SenderPerfStatsSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
        return new SenderPerfStatsSnapshot(
                values[0],
                values[1],
                values[2],
                values[3],
                values.length > 4 ? values[4] : 0L,
                values.length > 5 ? values[5] : 0L,
                values.length > 6 ? values[6] : 0L,
                values.length > 7 ? values[7] : 0L,
                values.length > 8 ? values[8] : 0L);
    }

    public static AddLogPerfStatsSnapshot snapshotAddLogPerfStats() {
        requireNativeLibrary();
        long[] values = nativeSnapshotAddLogPerfStats();
        long nativeUtfDupWallMs = values != null && values.length > 0 ? values[0] : 0L;
        long nativeProducerAddLogWallMs = values != null && values.length > 1 ? values[1] : 0L;
        long nativeKeyLensWallMs = values != null && values.length > 2 ? values[2] : 0L;
        long nativePersistentPathWallMs = values != null && values.length > 3 ? values[3] : 0L;
        long nativeQueuePathWallMs = values != null && values.length > 4 ? values[4] : 0L;
        long nativeTlsBatchBuilderWallMs = values != null && values.length > 5 ? values[5] : 0L;
        long nativeTlsBatchFlushWallMs = values != null && values.length > 6 ? values[6] : 0L;
        long nativeTlsBatchMergeWallMs = values != null && values.length > 7 ? values[7] : 0L;
        long nativePersistentBuilderWallMs = values != null && values.length > 8 ? values[8] : 0L;
        long nativePersistentAppendWallMs = values != null && values.length > 9 ? values[9] : 0L;
        long nativePersistentEnqueueWallMs = values != null && values.length > 10 ? values[10] : 0L;
        long nativePersistentEnqueueWaitBufferWallMs = values != null && values.length > 11 ? values[11] : 0L;
        long nativePersistentEnqueueBuilderInitWallMs = values != null && values.length > 12 ? values[12] : 0L;
        long nativePersistentEnqueueIngressPushWallMs = values != null && values.length > 13 ? values[13] : 0L;
        long nativePersistentEnqueueIngressWaitQueueWallMs = values != null && values.length > 14 ? values[14] : 0L;
        long nativePersistentEnqueueIngressBookkeepingWallMs = values != null && values.length > 15 ? values[15] : 0L;
        long nativePersistentEnqueueIngressNotifyWallMs = values != null && values.length > 16 ? values[16] : 0L;
        long nativeTlsBatchPreambleWallMs = values != null && values.length > 17 ? values[17] : 0L;
        long nativeTlsBatchBookkeepingWallMs = values != null && values.length > 18 ? values[18] : 0L;
        long nativePersistentAppendEncodeWallMs = values != null && values.length > 19 ? values[19] : 0L;
        long nativePersistentAppendStoreWallMs = values != null && values.length > 20 ? values[20] : 0L;
        long nativeTlsBatchFlushShellWallMs = values != null && values.length > 21 ? values[21] : 0L;
        long nativeTlsBatchMergeShellWallMs = values != null && values.length > 22 ? values[22] : 0L;
        long nativePersistentAppendSizingWallMs = values != null && values.length > 23 ? values[23] : 0L;
        long nativePersistentAppendCapacityWallMs = values != null && values.length > 24 ? values[24] : 0L;
        long nativePersistentAppendPostStoreWallMs = values != null && values.length > 25 ? values[25] : 0L;
        long nativePersistentAppendRetryShellWallMs = values != null && values.length > 26 ? values[26] : 0L;
        long nativePersistentAppendPrecheckWallMs = values != null && values.length > 27 ? values[27] : 0L;
        long nativePersistentAppendMetaWallMs = values != null && values.length > 28 ? values[28] : 0L;
        long nativePersistentAppendRetryPersistentMutexWallMs = values != null && values.length > 29 ? values[29] : 0L;
        long nativePersistentAppendRetrySleepWallMs = values != null && values.length > 30 ? values[30] : 0L;
        long nativePersistentAppendRetryProducerMutexWallMs = values != null && values.length > 31 ? values[31] : 0L;
        long nativePersistentAppendStoreRotateWallMs = values != null && values.length > 32 ? values[32] : 0L;
        long nativePersistentAppendStoreWriteWallMs = values != null && values.length > 33 ? values[33] : 0L;
        long nativePersistentAppendRetryProducerLockWallMs = values != null && values.length > 34 ? values[34] : 0L;
        long nativePersistentAppendRetryProducerNotifyWallMs = values != null && values.length > 35 ? values[35] : 0L;
        return new AddLogPerfStatsSnapshot(
                nanosToMillis(JAVA_FLATTEN_WALL_NS.get()),
                nanosToMillis(NATIVE_ADD_LOG_WALL_NS.get()),
                nativeUtfDupWallMs,
                nativeProducerAddLogWallMs,
                nativeKeyLensWallMs,
                nativePersistentPathWallMs,
                nativeQueuePathWallMs,
                nativeTlsBatchBuilderWallMs,
                nativeTlsBatchFlushWallMs,
                nativeTlsBatchMergeWallMs,
                nativePersistentBuilderWallMs,
                nativePersistentAppendWallMs,
                nativePersistentEnqueueWallMs,
                nativePersistentEnqueueWaitBufferWallMs,
                nativePersistentEnqueueBuilderInitWallMs,
                nativePersistentEnqueueIngressPushWallMs,
                nativePersistentEnqueueIngressWaitQueueWallMs,
                nativePersistentEnqueueIngressBookkeepingWallMs,
                nativePersistentEnqueueIngressNotifyWallMs,
                nativeTlsBatchPreambleWallMs,
                nativeTlsBatchBookkeepingWallMs,
                nativePersistentAppendEncodeWallMs,
                nativePersistentAppendStoreWallMs,
                nativeTlsBatchFlushShellWallMs,
                nativeTlsBatchMergeShellWallMs,
                nativePersistentAppendSizingWallMs,
                nativePersistentAppendCapacityWallMs,
                nativePersistentAppendPostStoreWallMs,
                nativePersistentAppendRetryShellWallMs,
                nativePersistentAppendPrecheckWallMs,
                nativePersistentAppendMetaWallMs,
                nativePersistentAppendRetryPersistentMutexWallMs,
                nativePersistentAppendRetrySleepWallMs,
                nativePersistentAppendRetryProducerMutexWallMs,
                nativePersistentAppendStoreRotateWallMs,
                nativePersistentAppendStoreWriteWallMs,
                nativePersistentAppendRetryProducerLockWallMs,
                nativePersistentAppendRetryProducerNotifyWallMs);
    }

    private long createInternal(ConfigSnapshot config, LogProducerCallback callback) {
        nativeLibraryVerifier.verify();
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        int tagCount = config.getTagCount();
        String[] tagKeys = new String[tagCount];
        String[] tagValues = new String[tagCount];
        for (int i = 0; i < tagCount; i++) {
            tagKeys[i] = config.getTagKey(i);
            tagValues[i] = config.getTagValue(i);
        }
        CreateArgs args = new CreateArgs(
                config,
                config.isDestroyWaitSplitConfigured() ? 0 : config.getDestroyWaitMs(),
                config.getDestroyFlusherWaitMs(),
                config.getDestroySenderWaitMs(),
                config.isDestroyWaitSplitConfigured(),
                tagKeys,
                tagValues,
                tagCount,
                callbackDispatcherFactory.create(callback, config.isCallbackFromSenderThread()));
        long handle = createInvoker.create(args);
        if (handle == 0) {
            throw new IllegalStateException("native producer create failed");
        }
        defaultHashKey = config.getHashKey();
        return handle;
    }

    private static Object createCallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread) {
        if (callback == null) {
            return null;
        }
        return new CallbackDispatcher(callback, callbackFromSenderThread);
    }

    private static long invokeNativeCreate(CreateArgs args) {
        ConfigSnapshot config = args.getConfig();
        return nativeCreate(
                config.getEndpoint(),
                config.getRegion(),
                config.getProjectId(),
                config.getTopicId(),
                config.getAccessKeyId(),
                config.getAccessKeySecret(),
                config.getSecurityToken(),
                config.getUserAgent(),
                config.getSource(),
                config.getHashKey(),
                toNativeCompressType(config.getCompressType()),
                config.getSendThreadCount(),
                config.isPersistent(),
                config.getPersistentFilePath(),
                config.isPersistentForceFlush(),
                config.getPersistentMaxFileCount(),
                config.getPersistentMaxFileSize(),
                config.getPersistentMaxLogCount(),
                config.getPacketLogBytes(),
                config.getPacketLogCount(),
                config.getPacketTimeoutMs(),
                config.getMaxBufferLimit(),
                config.getRetryMaxAttempts(),
                config.getRetryTotalTimeoutMs(),
                config.getRetryInitialIntervalMs(),
                config.getRetryMaxIntervalMs(),
                config.getConnectTimeoutMs(),
                config.getRequestTimeoutMs(),
                config.isEnableTimeNs(),
                args.getDestroyWaitMs(),
                args.getDestroyFlusherWaitMs(),
                args.getDestroySenderWaitMs(),
                args.isDestroyWaitSplitEnabled(),
                args.getLogTagKeys(),
                args.getLogTagValues(),
                args.getLogTagCount(),
                args.getCallbackDispatcher());
    }

    static int toNativeCompressType(LogProducerConfig.CompressType compressType) {
        if (compressType == null) {
            return COMPRESS_TYPE_UNSPECIFIED;
        }
        switch (compressType) {
            case NONE:
                return COMPRESS_TYPE_NONE;
            case LZ4:
                return COMPRESS_TYPE_LZ4;
            default:
                throw new IllegalArgumentException("unsupported compress type: " + compressType);
        }
    }

    @Override
    public void addLog(long producerHandle, Log log, int flush) {
        requireNativeLibrary();
        if (log == null) {
            throw new IllegalArgumentException("log == null");
        }
        long flattenStartNs = ADD_LOG_PERF_ENABLED ? System.nanoTime() : 0L;
        String[] keys;
        String[] values;
        if (log.getContent().isEmpty()) {
            keys = new String[0];
            values = new String[0];
        } else {
            keys = new String[log.getContent().size()];
            values = new String[log.getContent().size()];
            int index = 0;
            for (java.util.Map.Entry<String, String> entry : log.getContent().entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("log content key cannot be null");
                }
                keys[index] = entry.getKey();
                values[index] = entry.getValue() == null ? "" : entry.getValue();
                index++;
            }
        }
        if (ADD_LOG_PERF_ENABLED) {
            JAVA_FLATTEN_WALL_NS.addAndGet(System.nanoTime() - flattenStartNs);
        }
        long nativeAddLogStartNs = ADD_LOG_PERF_ENABLED ? System.nanoTime() : 0L;
        int result;
        try {
            result = nativeAddLog(producerHandle, log.getLogTime(), defaultHashKey, keys, values, flush);
        } finally {
            if (ADD_LOG_PERF_ENABLED) {
                NATIVE_ADD_LOG_WALL_NS.addAndGet(System.nanoTime() - nativeAddLogStartNs);
            }
        }
        if (result != 0) {
            throw new IllegalStateException("native addLog failed: " + result);
        }
    }

    private static void requireNativeLibrary() {
        if (!NATIVE_LIBRARY_LOADED) {
            throw new IllegalStateException("tls_producer_jni is not loaded", NATIVE_LIBRARY_ERROR);
        }
    }

    private static native long nativeCreate(
            String endpoint,
            String region,
            String projectId,
            String topicId,
            String accessKeyId,
            String accessKeySecret,
            String securityToken,
            String userAgent,
            String source,
            String hashKey,
            int compressType,
            int sendThreadCount,
            boolean persistent,
            String persistentFilePath,
            boolean persistentForceFlush,
            int persistentMaxFileCount,
            int persistentMaxFileSize,
            int persistentMaxLogCount,
            int packetLogBytes,
            int packetLogCount,
            int packetTimeoutMs,
            int maxBufferLimit,
            int retryMaxAttempts,
            int retryTotalTimeoutMs,
            int retryInitialIntervalMs,
            int retryMaxIntervalMs,
            int connectTimeoutMs,
            int requestTimeoutMs,
            boolean enableTimeNs,
            int destroyWaitMs,
            int destroyFlusherWaitMs,
            int destroySenderWaitMs,
            boolean destroyWaitSplitEnabled,
            String[] logTagKeys,
            String[] logTagValues,
            int logTagCount,
            Object callbackDispatcher);

    private static native int nativeAddLog(
            long producerHandle,
            long logTimeMs,
            String hashKey,
            String[] keys,
            String[] values,
            int flush);

    private static native int nativeUpdateEndpoint(long producerHandle, String endpoint, String region, String topicId);

    private static native int nativeResetSecurityToken(
            long producerHandle,
            String accessKeyId,
            String accessKeySecret,
            String securityToken);

    private static native void nativeDestroy(
            long producerHandle,
            int destroyWaitMs,
            int destroyFlusherWaitMs,
            int destroySenderWaitMs,
            boolean destroyWaitSplitEnabled);

    private static native void nativeResetSenderPerfStats();

    private static native long[] nativeSnapshotSenderPerfStats();

    private static native void nativeResetAddLogPerfStats();

    private static native long[] nativeSnapshotAddLogPerfStats();

    private static long nanosToMillis(long value) {
        return Math.max(0L, value / 1_000_000L);
    }
}
