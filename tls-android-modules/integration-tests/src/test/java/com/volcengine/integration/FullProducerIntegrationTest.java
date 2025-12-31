package com.volcengine.integration;

import com.volcengine.model.tls.LogContent;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.ProducerConfig;
import com.volcengine.model.tls.producer.Result;
import com.volcengine.service.tls.Producer;
import com.volcengine.service.tls.ProducerImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FullProducerIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void sendLog_success_when_env_present() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    String token = env("token");
    String topicId = env("topicId");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null && topicId != null,
      "missing env, skip real integration test");

    ProducerConfig cfg = new ProducerConfig(endPoint, region, ak, sk, token);
    cfg.setMaxBatchSizeBytes(256 * 1024);
    cfg.setMaxBatchCount(1);
    cfg.setLingerMs(100);
    cfg.setRetryCount(3);
    Producer producer = new ProducerImpl(cfg);
    producer.start();

    LogItem item = new LogItem();
    item.setTime(System.currentTimeMillis());
    List<LogContent> contents = new ArrayList<>();
    contents.add(new LogContent("integration", "full"));
    item.setContents(contents);

    CountDownLatch latch = new CountDownLatch(1);
    final Result[] holder = new Result[1];
    producer.sendLogV2(null, topicId, null, null, item, new CallBack() {
      @Override public void onComplete(Result result) { holder[0] = result; latch.countDown(); }
    });

    assertTrue(latch.await(15, TimeUnit.SECONDS));
    assertNotNull(holder[0]);
    assertTrue(holder[0].isSuccess(), "send should succeed");
    producer.close();
  }
}
