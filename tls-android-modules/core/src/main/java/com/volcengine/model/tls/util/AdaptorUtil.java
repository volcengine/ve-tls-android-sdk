package com.volcengine.model.tls.util;

import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.pb.PutLogRequest;

import java.util.List;
import java.util.Map;

public class AdaptorUtil {
  public static PutLogRequest.LogGroupList logItems2PbGroupList(String path, String source, List<LogItem> logs) {
    return logItems2PbGroupList(path, source, logs, false);
  }
  public static PutLogRequest.LogGroupList logItems2PbGroupList(String path, String source, List<LogItem> logs, boolean enableTimeNs) {
    PutLogRequest.LogGroup.Builder gb = PutLogRequest.LogGroup.newBuilder();
    if (path != null) { gb.setFileName(path); }
    if (source != null) { gb.setSource(source); }
    if (logs != null) {
      for (LogItem item : logs) {
        long t = item.getTime();
        long nt;
        if (t <= 0) nt = System.currentTimeMillis();
        else if (t < 10_000_000_000L) nt = t * 1000; // seconds -> ms
        else if (t < 1_000_000_000_000_000L) nt = t; // ms
        else nt = t / 1_000_000; // ns -> ms
        PutLogRequest.Log.Builder lb = PutLogRequest.Log.newBuilder().setTime(nt);
        if (item.getTimeNs() != null) { lb.setTimeNs(item.getTimeNs()); }
        else if (enableTimeNs) { lb.setTimeNs((int)(System.nanoTime() % 1_000_000_000)); }
        if (item.getContents() != null) {
          for (com.volcengine.model.tls.LogContent c : item.getContents()) {
            PutLogRequest.LogContent.Builder cb = PutLogRequest.LogContent.newBuilder().setKey(c.getKey()).setValue(c.getValue());
            lb.addContents(cb.build());
          }
        }
        gb.addLogs(lb.build());
      }
    }
    PutLogRequest.LogGroupList.Builder lb = PutLogRequest.LogGroupList.newBuilder();
    lb.addLogGroups(gb.build());
    return lb.build();
  }

  public static PutLogRequest.LogGroup logItems2PbGroup(String path, String source, List<LogItem> logs, boolean enableTimeNs) {
    PutLogRequest.LogGroup.Builder gb = PutLogRequest.LogGroup.newBuilder();
    if (path != null) { gb.setFileName(path); }
    if (source != null) { gb.setSource(source); }
    if (logs != null) {
      for (LogItem item : logs) {
        long t = item.getTime();
        long nt;
        if (t <= 0) nt = System.currentTimeMillis();
        else if (t < 10_000_000_000L) nt = t * 1000;
        else if (t < 1_000_000_000_000_000L) nt = t;
        else nt = t / 1_000_000;
        PutLogRequest.Log.Builder lb = PutLogRequest.Log.newBuilder().setTime(nt);
        if (item.getTimeNs() != null) { lb.setTimeNs(item.getTimeNs()); }
        else if (enableTimeNs) { lb.setTimeNs((int)(System.nanoTime() % 1_000_000_000)); }
        if (item.getContents() != null) {
          for (com.volcengine.model.tls.LogContent c : item.getContents()) {
            PutLogRequest.LogContent.Builder cb = PutLogRequest.LogContent.newBuilder().setKey(c.getKey()).setValue(c.getValue());
            lb.addContents(cb.build());
          }
        }
        gb.addLogs(lb.build());
      }
    }
    return gb.build();
  }

  public static PutLogRequest.LogGroup logItems2PbGroup(String path, String source, List<LogItem> logs) {
    return logItems2PbGroup(path, source, logs, false);
  }

  public static PutLogRequest.LogGroup logItems2PbGroupWithTags(String path, String source, Map<String,String> groupTags, List<LogItem> logs, boolean enableTimeNs) {
    PutLogRequest.LogGroup.Builder gb = PutLogRequest.LogGroup.newBuilder();
    if (path != null) { gb.setFileName(path); }
    if (source != null) { gb.setSource(source); }
    if (groupTags != null) {
      for (Map.Entry<String,String> e : groupTags.entrySet()) {
        PutLogRequest.LogTag.Builder tb = PutLogRequest.LogTag.newBuilder().setKey(e.getKey()).setValue(e.getValue());
        gb.addLogTags(tb.build());
      }
    }
    if (logs != null) {
      for (LogItem item : logs) {
        long t = item.getTime();
        long nt;
        if (t <= 0) nt = System.currentTimeMillis();
        else if (t < 10_000_000_000L) nt = t * 1000;
        else if (t < 1_000_000_000_000_000L) nt = t;
        else nt = t / 1_000_000;
        PutLogRequest.Log.Builder lb = PutLogRequest.Log.newBuilder().setTime(nt);
        if (item.getTimeNs() != null) { lb.setTimeNs(item.getTimeNs()); }
        else if (enableTimeNs) { lb.setTimeNs((int)(System.nanoTime() % 1_000_000_000)); }
        if (item.getContents() != null) {
          for (com.volcengine.model.tls.LogContent c : item.getContents()) {
            PutLogRequest.LogContent.Builder cb = PutLogRequest.LogContent.newBuilder().setKey(c.getKey()).setValue(c.getValue());
            lb.addContents(cb.build());
          }
        }
        gb.addLogs(lb.build());
      }
    }
    return gb.build();
  }
}
