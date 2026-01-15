package com.volcengine.model.tls.request;

import com.volcengine.model.tls.pb.PutLogRequest;

import static com.volcengine.model.tls.Const.LZ4;

public class PutLogsRequest {
    private PutLogRequest.LogGroupList logGroupList;
    private String topicId;
    private String hashKey;
    private String compressType = LZ4;

    public PutLogsRequest() {}
    public PutLogsRequest(PutLogRequest.LogGroupList logGroupList, String topicId) { this.logGroupList = logGroupList; this.topicId = topicId; }
    public PutLogsRequest(PutLogRequest.LogGroupList logGroupList, String topicId, String hashKey, String compressType) {
        this.logGroupList = logGroupList; this.topicId = topicId; this.hashKey = hashKey; this.compressType = compressType;
    }

    public PutLogRequest.LogGroupList getLogGroupList() { return logGroupList; }
    public void setLogGroupList(PutLogRequest.LogGroupList logGroupList) { this.logGroupList = logGroupList; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public String getHashKey() { return hashKey; }
    public void setHashKey(String hashKey) { this.hashKey = hashKey; }
    public String getCompressType() { return compressType; }
    public void setCompressType(String compressType) { this.compressType = compressType; }
    public boolean CheckValidation() { return !(this.topicId == null || this.logGroupList == null); }
}
