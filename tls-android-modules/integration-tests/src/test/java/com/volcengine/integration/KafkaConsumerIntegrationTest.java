package com.volcengine.integration;

import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.request.CloseKafkaConsumerRequest;
import com.volcengine.model.tls.request.OpenKafkaConsumerRequest;
import com.volcengine.model.tls.request.DescribeKafkaConsumerRequest;
import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.service.tls.TLSLogClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaConsumerIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void open_describe_close_kafka_consumer() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    String topicId = env("topicId");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null && topicId != null,
      "missing env, skip real kafka consumer test");

    TLSLogClient client = ClientBuilder.newClient(new ClientConfig(endPoint, region, ak, sk, env("token")));
    try {
      assertNotNull(client.openKafkaConsumer(new OpenKafkaConsumerRequest(topicId)));
      assertNotNull(client.describeKafkaConsumer(new DescribeKafkaConsumerRequest(topicId)));
      assertNotNull(client.closeKafkaConsumer(new CloseKafkaConsumerRequest(topicId)));
    } finally {
      client.destroy();
    }
  }
}
