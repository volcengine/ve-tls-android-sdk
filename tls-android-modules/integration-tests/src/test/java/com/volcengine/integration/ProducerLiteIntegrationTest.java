package com.volcengine.integration;

import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ProducerLiteIntegrationTest {
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

    LogProducerConfig cfg = new LogProducerConfig()
      .setEndpoint(endPoint)
      .setRegion(region)
      .setAccessKeyId(ak)
      .setAccessKeySecret(sk)
      .setSecurityToken(token)
      .setTopicId(topicId)
      .setCompressType(System.getenv().getOrDefault("compress", "lz4"))
      .setSendThreadCount(1)
      .setRetryCount(3)
      .setPacketLogBytes(256 * 1024)
      .setPacketLogCount(1)
      .setPacketTimeout(100);

    LogProducerClient client = new LogProducerClient(cfg);
    client.start();
    Map<String,String> kv = new HashMap<>();
    kv.put("integration", "lite");
    CountDownLatch latch = new CountDownLatch(1);
    final Result[] holder = new Result[1];
    client.sendLog(kv, new CallBack() {
      @Override public void onComplete(Result result) { holder[0] = result; latch.countDown(); }
    });
    assertTrue(latch.await(15, TimeUnit.SECONDS));
    assertNotNull(holder[0]);
    assertTrue(holder[0].isSuccess(), "send should succeed");
    client.close();
  }
}
