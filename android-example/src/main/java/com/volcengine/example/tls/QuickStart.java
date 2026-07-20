package com.volcengine.example.tls;

import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import com.volcengine.service.tls.TLSLogClient;

import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.List;

import static com.volcengine.model.tls.Const.LZ4;

public class QuickStart {
    public static void main(String[] args) throws LogException {
        String endPoint = System.getenv("endPoint");
        String region = System.getenv("region");
        String ak = System.getenv("ak");
        String sk = System.getenv("sk");
        String token = System.getenv("token");

        ClientConfig cfg = new ClientConfig(endPoint, region, ak, sk, token);
        TLSLogClient client = ClientBuilder.newClient(cfg);

        // 1. 创建Project
        String projectName = "android-example-project-" + System.currentTimeMillis();
        CreateProjectResponse cpr = client.createProject(new CreateProjectRequest(projectName, region, "demo"));
        String projectId = cpr.getProjectId();
        System.out.println("CreateProject: " + cpr);

        // 2. 创建Topic
        String topicName = "android-example-topic-" + System.currentTimeMillis();
        CreateTopicRequest ctr = new CreateTopicRequest();
        ctr.setProjectId(projectId);
        ctr.setTopicName(topicName);
        ctr.setTtl(7);
        CreateTopicResponse ctrResp = client.createTopic(ctr);
        String topicId = ctrResp.getTopicId();
        System.out.println("CreateTopic: " + ctrResp);

        // 3. 创建Index
        CreateIndexResponse cir = client.createIndex(new CreateIndexRequest(topicId, new FullTextInfo(false, ",-;", false), null));
        System.out.println("CreateIndex: " + cir);
        try {
            // 等价于 Thread.sleep(60000)，语义更清晰
            TimeUnit.SECONDS.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 4. 写入日志
        List<LogItem> logs = new ArrayList<>();
        LogItem item = new LogItem(System.currentTimeMillis());
        item.addContent("key", "value");
        logs.add(item);
        PutLogsRequestV2 putReq = new PutLogsRequestV2(logs, topicId, null, LZ4, "android-example", "demo-file");
        PutLogsResponse putResp = client.putLogsV2(putReq);
        System.out.println("PutLogsV2: " + putResp);
        try {
            // 等价于 Thread.sleep(60000)，语义更清晰
            TimeUnit.SECONDS.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 5. 检索日志
        SearchLogsRequest slr = new SearchLogsRequest();
        slr.setTopicId(topicId);
        slr.setQuery("*");
        slr.setStartTime(System.currentTimeMillis() - 1000_000);
        slr.setEndTime(System.currentTimeMillis());
        SearchLogsResponse slResp = client.searchLogs(slr);
        System.out.println("SearchLogs: " + slResp);

        // 6. 清理资源
        client.deleteIndex(new DeleteIndexRequest(topicId));
        client.deleteTopic(new DeleteTopicRequest(topicId));
        client.deleteProject(new DeleteProjectRequest(projectId));

        client.destroy();
    }
}
