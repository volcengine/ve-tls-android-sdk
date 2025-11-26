package com.volcengine.example.tls;

import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import com.volcengine.service.tls.Producer;
import com.volcengine.service.tls.ProducerImpl;
import com.volcengine.service.tls.TLSLogClient;

import java.util.ArrayList;
import java.util.List;

import static com.volcengine.model.tls.Const.LZ4;

public class ProducerDemo {
    public static void main(String[] args) throws Exception {
        String endPoint = System.getenv("endPoint");
        String region = System.getenv("region");
        String ak = System.getenv("ak");
        String sk = System.getenv("sk");
        String token = System.getenv("token");
        int topicTtl = envIntOr("TOPIC_TTL", 7);
        int indexWaitSeconds = envIntOr("INDEX_WAIT_SECONDS", 60);
        int produceCount = envIntOr("PRODUCE_COUNT", 20);
        boolean doSearch = envBoolOr("DO_SEARCH", true);
        int searchWindowSeconds = envIntOr("SEARCH_WINDOW_SECONDS", 60);

        // Create resources via client
        ClientConfig cfg = new ClientConfig(endPoint, region, ak, sk, token);
        TLSLogClient client = ClientBuilder.newClient(cfg);
        String projectName = "producer-demo-project-" + System.currentTimeMillis();
        CreateProjectResponse cpr = client.createProject(new CreateProjectRequest(projectName, region, "producer demo"));
        String projectId = cpr.getProjectId();
        String topicName = "producer-demo-topic-" + System.currentTimeMillis();
        CreateTopicRequest ctr = new CreateTopicRequest();
        ctr.setProjectId(projectId);
        ctr.setTopicName(topicName);
        ctr.setTtl(topicTtl);
        CreateTopicResponse ctResp = client.createTopic(ctr);
        String topicId = ctResp.getTopicId();
        client.createIndex(new CreateIndexRequest(topicId, new FullTextInfo(false, ",-;", false), null));

        // Wait for index to be ready
        Thread.sleep(indexWaitSeconds * 1000L);

        // Produce logs
        Producer producer = ProducerImpl.defaultProducer(endPoint, region, ak, sk, token);
        producer.start();

        CallBack cb = new CallBack() {
            @Override
            public void onComplete(Result result) {
                System.out.println("producer result: " + result);
            }
        };

        List<LogItem> logs = new ArrayList<>();
        for (int i = 0; i < produceCount; i++) {
            LogItem it = new LogItem(System.currentTimeMillis());
            it.addContent("key", "value-" + i);
            logs.add(it);
        }
        producer.sendLogsV2("", topicId, "android-example", "producer-demo", logs, cb);
        producer.close();

        // Optional: search to verify
        if (doSearch) {
            try {
                SearchLogsRequest slr = new SearchLogsRequest();
                slr.setTopicId(topicId);
                slr.setQuery("*");
                slr.setStartTime(System.currentTimeMillis() - (searchWindowSeconds * 1000L));
                slr.setEndTime(System.currentTimeMillis());
                SearchLogsResponse slResp = client.searchLogs(slr);
                System.out.println("Search count: " + (slResp.getLogs() == null ? 0 : slResp.getLogs().size()));
            } catch (LogException e) { /* ignore */ }
        }

        // Cleanup
        client.deleteIndex(new DeleteIndexRequest(topicId));
        client.deleteTopic(new DeleteTopicRequest(topicId));
        client.deleteProject(new DeleteProjectRequest(projectId));
        client.destroy();
    }

    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? v : def;
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
