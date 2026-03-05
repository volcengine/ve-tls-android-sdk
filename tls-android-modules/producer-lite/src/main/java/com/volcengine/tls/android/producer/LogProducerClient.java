package com.volcengine.tls.android.producer;

import com.volcengine.model.tls.LogContent;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.ProducerConfig;
import com.volcengine.service.tls.ProducerImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogProducerClient {
    private final LogProducerConfig config;
    private ProducerImpl producer;

    public LogProducerClient(LogProducerConfig config) { this.config = config; }

    public void start() throws LogException {
        ProducerConfig pc = new ProducerConfig(
                config.getEndpoint(),
                config.getRegion(),
                config.getAccessKeyId(),
                config.getAccessKeySecret(),
                config.getSecurityToken()
        );
        pc.setMaxBatchSizeBytes(config.getPacketLogBytes());
        pc.setMaxBatchCount(config.getPacketLogCount());
        pc.setLingerMs(config.getPacketTimeout());
        pc.setTotalSizeInBytes(config.getMaxBufferLimit());
        pc.setRetryCount(config.getRetryCount());
        pc.setMaxReservedAttempts(config.getReservedAttempts());
        pc.setMaxThreadCount(config.getSendThreadCount());
        pc.setCompressType(config.getCompressType());
        if (config.getGroupTags() != null) { pc.setGroupTags(config.getGroupTags()); }
        pc.setEnableTimeNs(config.isEnableTimeNs());
        producer = new ProducerImpl(pc);
        producer.start();
    }

    public void close() throws LogException, InterruptedException { if (producer != null) { producer.close(); } }
    public void resetAccessKeyToken(String accessKey, String secretKey, String securityToken) throws LogException { if (producer != null) { producer.resetAccessKeyToken(accessKey, secretKey, securityToken); } }
  public void sendLog(Map<String,String> kv, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); producer.sendLogV2(config.getHashKey(), config.getTopicId(), null, null, item, callBack); }
  public void sendLog(Map<String,String> kv, long timeMillis, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); item.setTime(timeMillis); producer.sendLogV2(config.getHashKey(), config.getTopicId(), null, null, item, callBack); }
  public void sendLog(Map<String,String> kv, long timeMillis, Integer timeNs, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); item.setTime(timeMillis); item.setTimeNs(timeNs); producer.sendLogV2(config.getHashKey(), config.getTopicId(), null, null, item, callBack); }
  public void sendLogs(List<Map<String,String>> list, CallBack callBack) throws LogException, InterruptedException {
    List<LogItem> items = new ArrayList<>();
    if (list != null) { for (Map<String,String> kv : list) { items.add(mapToLogItem(kv)); } }
    producer.sendLogsV2(config.getHashKey(), config.getTopicId(), null, null, items, callBack);
  }
  private LogItem mapToLogItem(Map<String,String> kv) {
    LogItem item = new LogItem();
    java.util.Map<String,String> common = config.getCommonFields();
    int cap = 0;
    if (common != null) { cap += common.size(); }
    if (kv != null) { cap += kv.size(); }
    if (cap == 0) { return item; }
    List<LogContent> contents = new ArrayList<>(cap);
    if (common != null && !common.isEmpty()) {
      if (kv != null && !kv.isEmpty()) {
        java.util.Set<String> overrideKeys = kv.keySet();
        for (Map.Entry<String,String> e : common.entrySet()) {
          if (!overrideKeys.contains(e.getKey())) { contents.add(new LogContent(e.getKey(), e.getValue())); }
        }
      } else {
        for (Map.Entry<String,String> e : common.entrySet()) { contents.add(new LogContent(e.getKey(), e.getValue())); }
      }
    }
    if (kv != null && !kv.isEmpty()) { for (Map.Entry<String,String> e : kv.entrySet()) { contents.add(new LogContent(e.getKey(), e.getValue())); } }
    item.setContents(contents);
    return item;
  }
  public void sendLog(String source, String path, Map<String,String> kv, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); producer.sendLogV2(config.getHashKey(), config.getTopicId(), source, path, item, callBack); }
  public void sendLog(String source, String path, Map<String,String> kv, long timeMillis, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); item.setTime(timeMillis); producer.sendLogV2(config.getHashKey(), config.getTopicId(), source, path, item, callBack); }
  public void sendLog(String source, String path, Map<String,String> kv, long timeMillis, Integer timeNs, CallBack callBack) throws LogException, InterruptedException { LogItem item = mapToLogItem(kv); item.setTime(timeMillis); item.setTimeNs(timeNs); producer.sendLogV2(config.getHashKey(), config.getTopicId(), source, path, item, callBack); }
    public void closeNow() throws LogException, InterruptedException { if (producer != null) { producer.closeNow(); } }
    public void reconfig(LogProducerConfig newConfig) throws LogException {
        ProducerConfig pc = new ProducerConfig(
                newConfig.getEndpoint(),
                newConfig.getRegion(),
                newConfig.getAccessKeyId(),
                newConfig.getAccessKeySecret(),
                newConfig.getSecurityToken()
        );
        pc.setMaxBatchSizeBytes(newConfig.getPacketLogBytes());
        pc.setMaxBatchCount(newConfig.getPacketLogCount());
        pc.setLingerMs(newConfig.getPacketTimeout());
        pc.setTotalSizeInBytes(newConfig.getMaxBufferLimit());
        pc.setRetryCount(newConfig.getRetryCount());
        pc.setMaxReservedAttempts(newConfig.getReservedAttempts());
        pc.setMaxThreadCount(newConfig.getSendThreadCount());
        pc.setCompressType(newConfig.getCompressType());
        if (newConfig.getGroupTags() != null) { pc.setGroupTags(newConfig.getGroupTags()); }
        pc.setEnableTimeNs(newConfig.isEnableTimeNs());
        if (producer != null) { producer.config(pc); }
    }
}
