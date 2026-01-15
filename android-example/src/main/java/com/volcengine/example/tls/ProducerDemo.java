package com.volcengine.example.tls;

import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import java.util.HashMap;
import java.util.Map;

public class ProducerDemo {
    public static void main(String[] args) throws Exception {
        // Initialize Config
        String endPoint = envOr("endPoint", "");
        String region = envOr("region", "");
        String accessKeyId = envOr("ak", "");
        String accessKeySecret = envOr("sk", "");
        String token = envOr("token", "");
        String topicId = envOr("topicId", "");

        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(endPoint)
                .setRegion(region)
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setSecurityToken(token)
                .setTopicId(topicId)
                .setCompressType("lz4"); // Ensure LZ4 is set

        LogProducerClient client = new LogProducerClient(config);
        if (endPoint.isEmpty() || region.isEmpty() || accessKeyId.isEmpty() || accessKeySecret.isEmpty() || topicId.isEmpty()) {
            System.err.println("Missing env: endPoint/region/ak/sk/topicId");
            return;
        }
        client.start();
        
        System.out.println("LogProducerClient started.");

        // Send logs using Producer client
        for (int i = 0; i < 5; i++) {
            Map<String, String> kv = new HashMap<>();
            kv.put("index", String.valueOf(i));
            kv.put("data", "LogProducerClient test " + i);
            kv.put("time", String.valueOf(System.currentTimeMillis()));
            
            client.sendLog(kv, new CallBack() {
                @Override
                public void onComplete(Result result) {
                    boolean ok = result.isSuccess();
                    java.util.List<com.volcengine.model.tls.producer.Attempt> ats = result.getAttempts();
                    com.volcengine.model.tls.producer.Attempt last = null;
                    if (ats != null && !ats.isEmpty()) { last = ats.get(ats.size()-1); }
                    if (ok) {
                        String req = last != null ? String.valueOf(last.getRequestId()) : "";
                        System.out.println("Send success reqId=" + req + " attempts=" + result.getAttemptCount());
                    } else {
                        String http = last != null ? String.valueOf(last.getHttpCode()) : "";
                        String code = last != null ? String.valueOf(last.getErrorCode()) : "";
                        String msg = last != null ? String.valueOf(last.getErrorMessage()) : "";
                        System.out.println("Send failed http=" + http + " code=" + code + " msg=" + msg + " attempts=" + result.getAttemptCount());
                    }
                }
            });
        }
        
        // Wait for sending to finish
        Thread.sleep(3000);
        
        client.close();
        System.out.println("LogProducerClient closed.");
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static int envIntOr(String name, int def) {
        try {
            return Integer.parseInt(envOr(name, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean envBoolOr(String name, boolean def) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("1") || v.equalsIgnoreCase("yes");
    }
}
