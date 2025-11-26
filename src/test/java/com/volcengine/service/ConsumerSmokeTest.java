package com.volcengine.service;

import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import com.volcengine.model.tls.request.CreateIndexRequest;
import com.volcengine.model.tls.request.CreateProjectRequest;
import com.volcengine.model.tls.request.CreateTopicRequest;
import com.volcengine.model.tls.request.DeleteIndexRequest;
import com.volcengine.model.tls.request.DeleteProjectRequest;
import com.volcengine.model.tls.request.DeleteTopicRequest;
import com.volcengine.model.tls.response.CreateIndexResponse;
import com.volcengine.model.tls.response.CreateProjectResponse;
import com.volcengine.model.tls.response.CreateTopicResponse;
import com.volcengine.model.tls.response.DeleteIndexResponse;
import com.volcengine.model.tls.response.DeleteProjectResponse;
import com.volcengine.model.tls.response.DeleteTopicResponse;
import com.volcengine.model.tls.consumer.ConsumerConfig;
import com.volcengine.service.tls.Producer;
import com.volcengine.service.tls.ProducerImpl;
//import com.volcengine.service.tls.consumer.Consumer;
//import com.volcengine.service.tls.consumer.ConsumerImpl;
//import com.volcengine.service.tls.consumer.LogProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.volcengine.model.tls.Const.LZ4;
import static org.junit.Assert.assertTrue;

public class ConsumerSmokeTest extends BaseTest {

    private String projectId;
    private String topicId;
    private long currentTimeMillis;

    @Before
    public void setUp() throws Exception {
        String prefix = "android-consumer-smoke";
        currentTimeMillis = System.currentTimeMillis();

        // create project
        String projectName = prefix + "-" + currentTimeMillis;
        String region = clientConfig.getRegion();
        String description = "consumer smoke test";
        CreateProjectRequest project = new CreateProjectRequest(projectName, region, description);
        CreateProjectResponse createProjectResponse = client.createProject(project);
        projectId = createProjectResponse.getProjectId();

        // create topic
        String topicName = prefix + "-" + currentTimeMillis;
        CreateTopicRequest createTopicRequest = new CreateTopicRequest();
        createTopicRequest.setTopicName(topicName);
        createTopicRequest.setProjectId(projectId);
        createTopicRequest.setTtl(7);
        CreateTopicResponse createTopicResponse = client.createTopic(createTopicRequest);
        topicId = createTopicResponse.getTopicId();

        // create index
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(topicId,
                new FullTextInfo(false, ",-;", false), null);
        CreateIndexResponse createIndexResponse = client.createIndex(createIndexRequest);
    }

    @After
    public void tearDown() throws Exception {
        // delete index topic project
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(topicId);
        DeleteIndexResponse deleteIndexResponse = client.deleteIndex(deleteIndexRequest);
        DeleteTopicResponse deleteTopicResponse = client.deleteTopic(new DeleteTopicRequest(topicId));
        DeleteProjectResponse deleteProjectResponse = client.deleteProject(new DeleteProjectRequest(projectId));
    }

//    @Test
//    public void testConsumerFetchAndProcess() throws LogException, InterruptedException {
//        // produce a few logs
//        Producer producer = ProducerImpl.defaultProducer(
//                clientConfig.getEndpoint(),
//                clientConfig.getRegion(),
//                clientConfig.getAccessKeyId(),
//                clientConfig.getAccessKeySecret(),
//                clientConfig.getSecurityToken()
//        );
//        producer.start();
//
//        List<LogItem> logs = new ArrayList<LogItem>();
//        for (int i = 0; i < 20; i++) {
//            LogItem log = new LogItem(System.currentTimeMillis());
//            log.addContent("key", "value-" + i);
//            logs.add(log);
//        }
//        producer.sendLogsV2("", topicId, "smoke-source", "smoke-file", logs, new CallBack() {
//            @Override
//            public void onComplete(Result result) {
//                // no-op
//            }
//        });
//        producer.close();
//
//        // configure consumer
//        ConsumerConfig cfg = new ConsumerConfig(
//                clientConfig.getEndpoint(),
//                clientConfig.getRegion(),
//                clientConfig.getAccessKeyId(),
//                clientConfig.getAccessKeySecret(),
//                clientConfig.getSecurityToken()
//        );
//        cfg.setProjectID(projectId);
//        cfg.setConsumerGroupName("android-consumer-group-" + currentTimeMillis);
//        cfg.setConsumerName("worker-1");
//        cfg.setTopicIDList(Collections.singletonList(topicId));
//        cfg.setHeartbeatIntervalInSecond(5);
//        cfg.setDataFetchIntervalInMillisecond(200);
//        cfg.setFlushCheckpointIntervalInSecond(2);
//        cfg.setMaxFetchLogGroupCount(10);
//        cfg.setStopTimeout(10);
//        cfg.setOrigin(true);
//        cfg.setCompressType(LZ4);
//
//        final AtomicInteger processedCount = new AtomicInteger(0);
//        LogProcessor processor = (t, s, groups) -> processedCount.addAndGet(groups.getLogGroupsCount());
//
//        Consumer consumer = new ConsumerImpl(cfg, processor);
//
//        consumer.start();
//        // allow some cycles to fetch and process
//        Thread.sleep(7000);
//        consumer.stop();
//
//        assertTrue("consumer should process some log groups", processedCount.get() > 0);
//    }
}
