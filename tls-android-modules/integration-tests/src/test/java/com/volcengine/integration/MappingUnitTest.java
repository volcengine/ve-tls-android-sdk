package com.volcengine.integration;

import com.volcengine.model.tls.LogContent;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.pb.PutLogRequest;
import com.volcengine.model.tls.util.AdaptorUtil;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MappingUnitTest {
  @Test
  void contents_and_tags_mapped_correctly() {
    List<LogItem> logs = new ArrayList<>();
    LogItem item = new LogItem();
    item.setTime(1700000000L); // seconds
    List<LogContent> contents = new ArrayList<>();
    contents.add(new LogContent("k1", "v1"));
    contents.add(new LogContent("k2", "v2"));
    item.setContents(contents);
    logs.add(item);

    Map<String,String> tags = new HashMap<>();
    tags.put("tg1", "tv1");

    PutLogRequest.LogGroup group = AdaptorUtil.logItems2PbGroupWithTags("path", "src", tags, logs, false);
    assertEquals("path", group.getFileName());
    assertEquals("src", group.getSource());
    assertEquals(1, group.getLogsCount());
    PutLogRequest.Log log = group.getLogs(0);
    assertTrue(log.getTime() > 0);
    assertEquals(2, log.getContentsCount());
    assertEquals("k1", log.getContents(0).getKey());
    assertEquals("v1", log.getContents(0).getValue());
    assertEquals(1, group.getLogTagsCount());
    assertEquals("tg1", group.getLogTags(0).getKey());
    assertEquals("tv1", group.getLogTags(0).getValue());
  }
}
