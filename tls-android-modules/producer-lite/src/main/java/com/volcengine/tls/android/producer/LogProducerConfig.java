package com.volcengine.tls.android.producer;

public class LogProducerConfig {
    private String endpoint;
    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String topicId;
    private String hashKey;
    private String compressType = "lz4";
    private int packetLogBytes = 1024 * 1024;
    private int packetLogCount = 1024;
    private int packetTimeout = 3000;
    private int maxBufferLimit = 64 * 1024 * 1024;
    private int sendThreadCount = 1;
    private int retryCount = 3;
    private int reservedAttempts = retryCount + 1;
    private java.util.Map<String,String> commonFields;
    private java.util.Map<String,String> groupTags;
    private boolean enableTimeNs = false;

    public String getEndpoint() { return endpoint; }
    public LogProducerConfig setEndpoint(String endpoint) { this.endpoint = endpoint; return this; }
    public String getRegion() { return region; }
    public LogProducerConfig setRegion(String region) { this.region = region; return this; }
    public String getAccessKeyId() { return accessKeyId; }
    public LogProducerConfig setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; return this; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public LogProducerConfig setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; return this; }
    public String getSecurityToken() { return securityToken; }
    public LogProducerConfig setSecurityToken(String securityToken) { this.securityToken = securityToken; return this; }
    public String getTopicId() { return topicId; }
    public LogProducerConfig setTopicId(String topicId) { this.topicId = topicId; return this; }
    public String getHashKey() { return hashKey; }
    public LogProducerConfig setHashKey(String hashKey) { this.hashKey = hashKey; return this; }
    public String getCompressType() { return compressType; }
    public LogProducerConfig setCompressType(String compressType) { this.compressType = compressType; return this; }
    public int getPacketLogBytes() { return packetLogBytes; }
    public LogProducerConfig setPacketLogBytes(int packetLogBytes) { this.packetLogBytes = packetLogBytes; return this; }
    public int getPacketLogCount() { return packetLogCount; }
    public LogProducerConfig setPacketLogCount(int packetLogCount) { this.packetLogCount = packetLogCount; return this; }
    public int getPacketTimeout() { return packetTimeout; }
    public LogProducerConfig setPacketTimeout(int packetTimeout) { this.packetTimeout = packetTimeout; return this; }
    public int getMaxBufferLimit() { return maxBufferLimit; }
    public LogProducerConfig setMaxBufferLimit(int maxBufferLimit) { this.maxBufferLimit = maxBufferLimit; return this; }
    public int getSendThreadCount() { return sendThreadCount; }
    public LogProducerConfig setSendThreadCount(int sendThreadCount) { this.sendThreadCount = sendThreadCount; return this; }
    public int getRetryCount() { return retryCount; }
    public LogProducerConfig setRetryCount(int retryCount) { this.retryCount = retryCount; this.reservedAttempts = retryCount + 1; return this; }
    public int getReservedAttempts() { return reservedAttempts; }
    public java.util.Map<String,String> getCommonFields() { return commonFields; }
    public LogProducerConfig setCommonFields(java.util.Map<String,String> commonFields) { this.commonFields = commonFields; return this; }
    public java.util.Map<String,String> getGroupTags() { return groupTags; }
    public LogProducerConfig setGroupTags(java.util.Map<String,String> groupTags) { this.groupTags = groupTags; return this; }
    public boolean isEnableTimeNs() { return enableTimeNs; }
    public LogProducerConfig setEnableTimeNs(boolean enableTimeNs) { this.enableTimeNs = enableTimeNs; return this; }
}
