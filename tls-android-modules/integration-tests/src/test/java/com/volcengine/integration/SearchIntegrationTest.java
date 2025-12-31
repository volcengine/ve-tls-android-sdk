package com.volcengine.integration;

import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.volcengine.model.tls.Const.LZ4;
import static org.junit.jupiter.api.Assertions.*;

public class SearchIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void search_histogram_shards_workflow() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real integration test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    try {
      setup.createProjectTopicIndex("it-search-project-", "it-search-topic-");
      TimeUnit.SECONDS.sleep(60);

      List<LogItem> logs = new ArrayList<>();
      LogItem item = new LogItem(System.currentTimeMillis());
      item.addContent("integration", "search");
      logs.add(item);
      PutLogsRequestV2 putReq = new PutLogsRequestV2(logs, setup.topicId, null, LZ4, "integration-tests", "search-test");
      PutLogsResponse putResp = setup.client.putLogsV2(putReq);
      assertNotNull(putResp);
      TimeUnit.SECONDS.sleep(60);

      long now = System.currentTimeMillis();
      SearchLogsRequest slr = new SearchLogsRequest();
      slr.setTopicId(setup.topicId);
      slr.setQuery("*");
      slr.setStartTime(now - 600_000);
      slr.setEndTime(now);
      SearchLogsResponse slResp = setup.client.searchLogs(slr);
      assertNotNull(slResp);

      DescribeHistogramV1Request hReq = new DescribeHistogramV1Request();
      hReq.setTopicId(setup.topicId);
      hReq.setQuery("*");
      hReq.setStartTime(now - 600_000);
      hReq.setEndTime(now);
      hReq.setInterval(60_000L);
      DescribeHistogramV1Response hResp = setup.client.describeHistogramV1(hReq);
      assertNotNull(hResp);

      DescribeShardsRequest dsReq = new DescribeShardsRequest(setup.topicId, 1, 20);
      DescribeShardsResponse dsResp = setup.client.describeShards(dsReq);
      assertNotNull(dsResp);
    } finally {
      setup.deleteAll();
      setup.close();
    }
  }
}
