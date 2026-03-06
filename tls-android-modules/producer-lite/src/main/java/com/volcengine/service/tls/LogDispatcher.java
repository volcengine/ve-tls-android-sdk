package com.volcengine.service.tls;

import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.pb.PutLogRequest;
import com.volcengine.model.tls.producer.BatchLog;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.ProducerConfig;
import com.volcengine.util.TlsLogger;
import com.volcengine.util.TlsLoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.volcengine.model.tls.Const.SEPARATOR;

public class LogDispatcher {
    public static final String TLS_THREAD_POOL_FORMAT = "dispatcher-thread-%d";
    private final ProducerConfig producerConfig;
    private final ExecutorService executorService;
    private TLSLogClient client;
    private final String producerName;
    private final BlockingQueue<BatchLog> successQueue;
    private final BlockingQueue<BatchLog> failureQueue;
    private static final TlsLogger LOG = TlsLoggerFactory.getLogger(LogDispatcher.class);
    private volatile boolean closed;
    private final Semaphore memoryLock;
    private final AtomicInteger batchCount;
    private final RetryManager retryManager;
    private final ConcurrentHashMap<BatchLog.BatchKey, BatchLog.BatchManager> batches;

    public LogDispatcher(ProducerConfig producerConfig, String producerName, BlockingQueue<BatchLog> successQueue,
                         BlockingQueue<BatchLog> failureQueue, Semaphore memoryLock,
                         AtomicInteger batchCount, RetryManager retryManager) throws LogException {
        this.producerConfig = producerConfig;
        this.producerName = producerName;
        this.executorService = Executors.newFixedThreadPool(
                producerConfig.getMaxThreadCount(), new NamedDaemonThreadFactory(producerName + SEPARATOR + TLS_THREAD_POOL_FORMAT));
        this.memoryLock = memoryLock;
        this.successQueue = successQueue;
        this.failureQueue = failureQueue;
        this.batches = new ConcurrentHashMap<>();
        this.batchCount = batchCount;
        this.retryManager = retryManager;
        this.client = new MinimalTLSLogClientImpl(producerConfig.getClientConfig());
    }

    public TLSLogClient getClient() { return client; }
    public ConcurrentHashMap<BatchLog.BatchKey, BatchLog.BatchManager> getBatches() { return batches; }

    public void start() {
        this.closed = false;
        LOG.debug(String.format("log dispatcher %s started and client init success", producerName));
    }

    public ExecutorService getExecutorService() { return this.executorService; }
    public void close() { this.closed = true; }
    public void closeNow() { this.closed = true; executorService.shutdownNow(); }

    private BatchLog.BatchManager getOrCreateBatchManager(BatchLog.BatchKey batchKey) {
        BatchLog.BatchManager batchManager = batches.get(batchKey);
        if (batchManager != null) { return batchManager; }
        batchManager = new BatchLog.BatchManager();
        BatchLog.BatchManager original = batches.putIfAbsent(batchKey, batchManager);
        return original != null ? original : batchManager;
    }

    public void resetAccessKeyToken(String accessKey, String secretKey, String securityToken) {
        ClientConfig clientConfig = producerConfig.getClientConfig();
        clientConfig.resetAccessKeyToken(accessKey, secretKey, securityToken);
        client.resetAccessKeyToken(accessKey, secretKey, securityToken);
        LOG.info(String.format("log dispatcher %s update credentials success", producerName));
    }

    public void addBatch(String hashKey, String topicId, String source, String filename,
                         PutLogRequest.LogGroup logGroup, CallBack callBack) throws InterruptedException, LogException {
        doAdd(hashKey, topicId, source, filename, logGroup, callBack);
    }

    private void doAdd(String hashKey, String topicId, String source, String filename, PutLogRequest.LogGroup logGroup, CallBack callBack)
            throws LogException, InterruptedException {
        if (closed) { throw new LogException("Producer Error", "closed LogDispatcher cannot receive logs anymore", null); }
        int batchSize = calculateSize(logGroup);
        producerConfig.checkBatchSize(batchSize);
        long maxBlockMs = producerConfig.getMaxBlockMs();

        if (maxBlockMs == 0) { memoryLock.acquire(); }
        else {
            boolean acquired = memoryLock.tryAcquire(batchSize, maxBlockMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                LOG.warn(String.format("Failed to acquire memory within the configured max blocking time %d ms, requiredSizeInBytes=%d, availableSizeInBytes=%d",
                        producerConfig.getMaxBlockMs(), batchSize, memoryLock.availablePermits()));
                throw new LogException("Producer Error", String.format("dispatcher %s try acquire memory lock failed", producerName), null);
            }
        }
        try {
            BatchLog.BatchKey batchKey = new BatchLog.BatchKey(hashKey, topicId, source, filename);
            BatchLog.BatchManager batchManager = getOrCreateBatchManager(batchKey);
            synchronized (batchManager) { addToBatchManager(batchKey, logGroup, callBack, batchSize, batchManager); }
        } catch (Exception e) {
            memoryLock.release(batchSize);
            throw new LogException("Producer Error", "dispatcher add batch concurrent error", null);
        }
    }

    private int calculateSize(PutLogRequest.LogGroup logGroup) { if (logGroup == null) { return 0; } return logGroup.getSerializedSize(); }

    private void addToBatchManager(BatchLog.BatchKey batchKey, PutLogRequest.LogGroup logGroup, CallBack callBack,
                                   int batchSize, BatchLog.BatchManager batchManager) throws LogException {
        BatchLog batchLog = batchManager.getBatchLog();
        if (batchLog != null) {
            boolean success = batchLog.tryAdd(logGroup, batchSize, callBack);
            if (success) {
                if (batchManager.fullAndSendBatchRequest()) {
                    batchManager.addNow(producerConfig, executorService, client, successQueue, failureQueue, batchCount, retryManager);
                }
                return;
            } else {
                batchManager.addNow(producerConfig, executorService, client, successQueue, failureQueue, batchCount, retryManager);
            }
        }
        batchLog = new BatchLog(batchKey, producerConfig);
        batchManager.setBatchLog(batchLog);
        boolean success = batchLog.tryAdd(logGroup, batchSize, callBack);
        if (!success) {
            LOG.error(String.format("tryAdd batchLog failed, batchKey = %s, batchSize = %d, batchCount = %d",
                    batchKey.toString(), batchSize, logGroup.getLogsCount()));
            throw new LogException("Producer Error", "tryAdd batchLog failed", null);
        }
        if (batchManager.fullAndSendBatchRequest()) {
            batchManager.addNow(producerConfig, executorService, client, successQueue, failureQueue, batchCount, retryManager);
        }
    }

    static class NamedDaemonThreadFactory implements ThreadFactory {
        private final String nameFormat;
        private final AtomicInteger idx = new AtomicInteger(0);
        NamedDaemonThreadFactory(String nameFormat) { this.nameFormat = nameFormat; }
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName(String.format(nameFormat, idx.incrementAndGet()));
            return t;
        }
    }
}
