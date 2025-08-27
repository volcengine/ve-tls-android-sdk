package com.volcengine.service;

import com.volcengine.model.tls.Const;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.producer.CallBack;
import com.volcengine.model.tls.producer.Result;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import com.volcengine.service.tls.Producer;
import com.volcengine.service.tls.ProducerImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProducerTest extends BaseTest {

    private String projectId;
    private String topicId;
    private long currentTimeMillis;

    private CallBack callBack = new CallBack() {
        @Override
        public void onComplete(Result result) {
            System.out.println("producer result:" + result);
        }
    };

    @Before
    public void setUp() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(Const.DATE_FORMAT);
        String prefix = "test-android-sdk-producer";
        String separator = "-";
        Date date = new Date();
        currentTimeMillis = date.getTime();
        String formatDate = sdf.format(date);

        //create project
        String projectName = prefix + separator + formatDate + separator + currentTimeMillis;
        String region = clientConfig.getRegion();
        String description = "test project";
        CreateProjectRequest project = new CreateProjectRequest(projectName, region, description);
        CreateProjectResponse createProjectResponse = client.createProject(project);
        System.out.println("create project success,response:" + createProjectResponse);
        projectId = createProjectResponse.getProjectId();

        //create topic
        String topicName = prefix + separator + formatDate + separator + currentTimeMillis;
        CreateTopicRequest createTopicRequest = new CreateTopicRequest();
        createTopicRequest.setTopicName(topicName);
        createTopicRequest.setProjectId(projectId);
        createTopicRequest.setTtl(7);
        CreateTopicResponse createTopicResponse = client.createTopic(createTopicRequest);
        System.out.println("create topic success,response:" + createTopicResponse);
        topicId = createTopicResponse.getTopicId();

        //create index
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(topicId,
                new FullTextInfo(false, ",-;", false), null);
        CreateIndexResponse createIndexResponse = client.createIndex(createIndexRequest);
        System.out.println("create index success,response:" + createIndexResponse);
    }

    @After
    public void tearDown() throws Exception {
        // delete index topic project
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(topicId);
        DeleteIndexResponse deleteIndexResponse = client.deleteIndex(deleteIndexRequest);
        System.out.println("delete index success,response:" + deleteIndexResponse);
        DeleteTopicResponse deleteTopicResponse = client.deleteTopic(new DeleteTopicRequest(topicId));
        System.out.println("delete topic success,response:" + deleteTopicResponse);
        DeleteProjectResponse deleteProjectResponse = client.deleteProject(new DeleteProjectRequest(projectId));
        System.out.println("delete project success,response:" + deleteProjectResponse);
    }

    @Test
    public void testProducerSendLogsV2() throws LogException, InterruptedException {
        Producer producer = ProducerImpl.defaultProducer(
            clientConfig.getEndpoint(),
            clientConfig.getRegion(),
            clientConfig.getAccessKeyId(),
            clientConfig.getAccessKeySecret(),
            clientConfig.getSecurityToken()
        );
        producer.start();

        int logNum = 4000;
        int keyNum = 2;

        List<LogItem> logs = new ArrayList<LogItem>();

        for (int i = 0; i < logNum; i++) {
            LogItem log = new LogItem(currentTimeMillis);
            for (int j = 0; j < keyNum; j++) {
                log.addContent("key" + j, "value" + j);
            }
            logs.add(log);
        }

        producer.sendLogsV2("", topicId, "test-source", "test-file", logs, callBack);

        producer.close();
    }

    @Test
    public void testProducerSendLogV2() throws LogException, InterruptedException {
        Producer producer = ProducerImpl.defaultProducer(
            clientConfig.getEndpoint(),
            clientConfig.getRegion(),
            clientConfig.getAccessKeyId(),
            clientConfig.getAccessKeySecret(),
            clientConfig.getSecurityToken()
        );
        producer.start();

        int logNum = 10000;
        int keyNum = 2;

        for (int i = 0; i < logNum; i++) {
            LogItem log = new LogItem(currentTimeMillis);
            for (int j = 0; j < keyNum; j++) {
                log.addContent("key" + j, "value" + j);
            }

            producer.sendLogV2("", topicId, "test-source", "test-file", log, callBack);
        }

        producer.close();
    }

}
