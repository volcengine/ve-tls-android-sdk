package com.volcengine.example.tls;

import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.LogContent;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import com.volcengine.model.tls.producer.ProducerConfig;
import com.volcengine.service.tls.Producer;
import com.volcengine.service.tls.ProducerImpl;

import java.util.ArrayList;
import java.util.List;

public class FullProducerDemo {
    public static void main(String[] args) throws Exception {
        String endPoint = envOr("endPoint", "");
        String region = envOr("region", "");
        String ak = envOr("ak", "");
        String sk = envOr("sk", "");
        String token = envOr("token", "");
        String topicId = envOr("topicId", "");

        if (endPoint.isEmpty() || region.isEmpty() || ak.isEmpty() || sk.isEmpty() || topicId.isEmpty()) {
            System.err.println("Missing env: endPoint/region/ak/sk/topicId");
            return;
        }

        ProducerConfig cfg = new ProducerConfig(endPoint, region, ak, sk, token);
        cfg.setMaxBatchSizeBytes(256 * 1024);
        cfg.setMaxBatchCount(512);
        cfg.setLingerMs(1000);
        cfg.setRetryCount(3);
        cfg.setMaxReservedAttempts(4);

        Producer producer = new ProducerImpl(cfg);
        producer.start();
        System.out.println("Full Producer started.");

        for (int i = 0; i < 5; i++) {
            LogItem item = new LogItem();
            item.setTime(System.currentTimeMillis());
            List<LogContent> contents = new ArrayList<>();
            contents.add(new LogContent("index", String.valueOf(i)));
            contents.add(new LogContent("data", "FullProducerDemo test " + i));
            item.setContents(contents);

            producer.sendLogV2(null, topicId, null, null, item, new CallBack() {
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

        Thread.sleep(3000);
        producer.close();
        System.out.println("Full Producer closed.");
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v != null ? v : def;
    }
}
