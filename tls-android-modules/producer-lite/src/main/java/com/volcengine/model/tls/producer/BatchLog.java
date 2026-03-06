package com.volcengine.model.tls.producer;

import com.volcengine.model.tls.pb.PutLogRequest;
import com.volcengine.service.tls.RetryManager;
import com.volcengine.service.tls.SendBatchTask;
import com.volcengine.service.tls.TLSLogClient;
import com.volcengine.util.TlsLogger;
import com.volcengine.util.TlsLoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchLog implements Delayed {
    public static class BatchKey {
        String shardHash; String topicId; String source; String fileName;
        public BatchKey(String shardHash, String topicId, String source, String fileName) { this.shardHash = shardHash; this.topicId = topicId; this.source = source; this.fileName = fileName; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; BatchKey batchKey = (BatchKey) o; return Objects.equals(shardHash, batchKey.shardHash) && Objects.equals(topicId, batchKey.topicId) && Objects.equals(source, batchKey.source) && Objects.equals(fileName, batchKey.fileName); }
        @Override public int hashCode() { return Objects.hash(shardHash, topicId, source, fileName); }
        @Override public String toString() { return "BatchKey{" + "shardHash='" + shardHash + '\'' + ", topicId='" + topicId + '\'' + ", source='" + source + '\'' + ", fileName='" + fileName + '\'' + '}'; }
        public String getShardHash() { return shardHash; }
        public String getTopicId() { return topicId; }
    }
    public static class BatchManager {
        BatchLog batchLog;
        public BatchManager() { }
        public boolean fullAndSendBatchRequest() { return batchLog.fullAndSendBatchRequest(); }
        public void addNow(ProducerConfig config, ExecutorService executorService, TLSLogClient client,
                           BlockingQueue<BatchLog> successQueue, BlockingQueue<BatchLog> failureQueue,
                           AtomicInteger batchCount, RetryManager retryManager) {
            if (batchLog != null) {
                executorService.submit(new SendBatchTask(batchLog, config, successQueue, failureQueue, client, retryManager));
                batchLog = null;
            }
        }
        public void removeBatch(List<BatchLog> batchLogs) { if (batchLog != null) { batchLogs.add(batchLog); batchLog = null; } }
        public BatchLog getBatchLog() { return batchLog; }
        public void setBatchLog(BatchLog batchLog) { this.batchLog = batchLog; }
    }

    private static final TlsLogger LOG = TlsLoggerFactory.getLogger(BatchLog.class);
    private BatchKey batchKey;
    private int currentBatchSize;
    private int currentBatchCount;
    private List<CallBack> callBackList = new ArrayList<>();
    private PutLogRequest.LogGroupList logGroupList = PutLogRequest.LogGroupList.newBuilder().build();
    private ProducerConfig producerConfig;
    private Deque<Attempt> reservedAttempts;
    private int attemptCount;
    private long createMs;
    private long nextRetryMs;
    private long retryBackoffMs;
    private long maxRetryBackoffMs;
    private long baseRetryBackoffMs;
    private long baseIncreaseBackoffMs;

    public BatchLog(BatchKey batchKey, ProducerConfig producerConfig) {
        this.batchKey = batchKey;
        this.currentBatchSize = 0;
        this.currentBatchCount = 0;
        this.producerConfig = producerConfig;
        this.attemptCount = 0;
        this.reservedAttempts = new ArrayDeque<>(producerConfig.getMaxReservedAttempts());
        this.createMs = System.currentTimeMillis();
        this.retryBackoffMs = 0;
        this.maxRetryBackoffMs = 10 * 1000;
        this.baseRetryBackoffMs = 1000;
        this.baseIncreaseBackoffMs = 1000;
    }

    public boolean tryAdd(PutLogRequest.LogGroup logGroup, int batchSize, CallBack callBack) {
        int currentBatchCount = getCurrentBatchCount();
        int currentBatchSize = getCurrentBatchSize();
        if (logGroup.getLogsList().size() + currentBatchCount > ProducerConfig.MAX_BATCH_COUNT
                || batchSize + currentBatchSize > ProducerConfig.MAX_BATCH_SIZE) { return false; }
        PutLogRequest.LogGroupList.Builder builder = PutLogRequest.LogGroupList.newBuilder().addLogGroups(logGroup);
        if (this.logGroupList.getLogGroupsList().size() > 0) { builder.addAllLogGroups(this.logGroupList.getLogGroupsList()); }
        this.logGroupList = builder.build();
        if (callBack != null) { getCallBackList().add(callBack); }
        setCurrentBatchCount(currentBatchCount + logGroup.getLogsList().size());
        setCurrentBatchSize(currentBatchSize + batchSize);
        return true;
    }
    public boolean fullAndSendBatchRequest() { return currentBatchCount >= producerConfig.getMaxBatchCount() || currentBatchSize >= producerConfig.getMaxBatchSizeBytes(); }
    public synchronized void addAttempt(Attempt attempt) { reservedAttempts.addLast(attempt); attemptCount++; while (reservedAttempts.size() > producerConfig.getMaxReservedAttempts()) { reservedAttempts.pollFirst(); } }
    public synchronized void fireCallbacks() {
        List<Attempt> attempts = new ArrayList<>(reservedAttempts);
        if (attempts.size() == 0) { LOG.error(String.format("batch log %s fire call back failed ", batchKey.toString())); return; }
        Attempt attempt = attempts.get(attempts.size() - 1);
        Result result = new Result(attempt.isSuccess(), attempts, attemptCount);
        fireCallbacks(result);
    }
    public void handleNextTry() {
        if (attemptCount == 1) { retryBackoffMs += baseRetryBackoffMs; }
        else { double increaseBackoffMs = Math.random() * baseIncreaseBackoffMs; retryBackoffMs += (long) increaseBackoffMs; }
        retryBackoffMs = Math.min(retryBackoffMs, maxRetryBackoffMs);
        nextRetryMs = System.currentTimeMillis() + retryBackoffMs;
    }
    private void fireCallbacks(Result result) { for (CallBack callBack : callBackList) { callBack.onComplete(result); } }
    @Override public int compareTo(Delayed o) { return (int) (nextRetryMs - ((BatchLog) o).getNextRetryMs()); }
    @Override public long getDelay(TimeUnit unit) { return unit.convert(nextRetryMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS); }

    public BatchKey getBatchKey() { return batchKey; }
    public int getCurrentBatchSize() { return currentBatchSize; }
    public int getCurrentBatchCount() { return currentBatchCount; }
    public List<CallBack> getCallBackList() { return callBackList; }
    public PutLogRequest.LogGroupList getLogGroupList() { return logGroupList; }
    public ProducerConfig getProducerConfig() { return producerConfig; }
    public int getAttemptCount() { return attemptCount; }
    public long getCreateMs() { return createMs; }
    public long getNextRetryMs() { return nextRetryMs; }
    public long getRetryBackoffMs() { return retryBackoffMs; }
    public long getMaxRetryBackoffMs() { return maxRetryBackoffMs; }
    public long getBaseRetryBackoffMs() { return baseRetryBackoffMs; }
    public long getBaseIncreaseBackoffMs() { return baseIncreaseBackoffMs; }
    public void setCurrentBatchSize(int v) { this.currentBatchSize = v; }
    public void setCurrentBatchCount(int v) { this.currentBatchCount = v; }
    public void setRetryBackoffMs(long v) { this.retryBackoffMs = v; }
}
