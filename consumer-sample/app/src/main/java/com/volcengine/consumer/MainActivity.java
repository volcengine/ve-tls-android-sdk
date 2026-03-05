package com.volcengine.consumer;

import android.app.Activity;
import android.os.Bundle;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    LogProducerConfig cfg = new LogProducerConfig()
        .setEndpoint(BuildConfig.TLS_ENDPOINT)
        .setRegion(BuildConfig.TLS_REGION)
        .setAccessKeyId(BuildConfig.TLS_AK)
        .setAccessKeySecret(BuildConfig.TLS_SK)
        .setSecurityToken(BuildConfig.TLS_TOKEN)
        .setTopicId(BuildConfig.TLS_TOPIC_ID)
        .setCompressType("lz4")
        .setSendThreadCount(1)
        .setRetryCount(0)
        .setPacketLogBytes(128 * 1024)
        .setPacketLogCount(128)
        .setPacketTimeout(1000);

    LogProducerClient client = new LogProducerClient(cfg);
    try {
      client.start();
      Map<String,String> kv = new HashMap<>();
      kv.put("hello", "world");
      client.sendLog(kv, r -> {});
    } catch (Exception ignored) {
    } finally {
      try { client.close(); } catch (Exception ignored) {}
    }
    finish();
  }
}
