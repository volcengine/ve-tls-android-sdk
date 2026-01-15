package com.volcengine.integration;

import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.request.CreateDownloadTaskRequest;
import com.volcengine.model.tls.request.DescribeDownloadTasksRequest;
import com.volcengine.model.tls.response.CreateDownloadTaskResponse;
import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.service.tls.TLSLogClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DownloadTaskIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void create_and_list_download_task() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    String topicId = env("topicId");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null && topicId != null,
      "missing env, skip real download task test");

    TLSLogClient client = ClientBuilder.newClient(new ClientConfig(endPoint, region, ak, sk, env("token")));
    try {
      long now = System.currentTimeMillis();
      CreateDownloadTaskRequest req = new CreateDownloadTaskRequest();
      req.setTopicId(topicId);
      req.setQuery("*");
      req.setStartTime(now - 600_000);
      req.setEndTime(now);
      req.setCompression("gzip");
      req.setDataFormat("json");
      req.setSort("desc");
      req.setTaskName("it-download-" + now);
      req.setLimit(100);
      CreateDownloadTaskResponse resp = client.createDownloadTask(req);
      assertNotNull(resp);

      assertNotNull(client.describeDownloadTasks(new DescribeDownloadTasksRequest(topicId, 1, 20)));
    } finally {
      client.destroy();
    }
  }
}
