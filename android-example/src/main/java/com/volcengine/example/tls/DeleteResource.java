package com.volcengine.example.tls;

import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.LogItem;
import com.volcengine.model.tls.ProjectInfo;
import com.volcengine.model.tls.ConsumerGroup;
import com.volcengine.model.tls.TopicInfo;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import com.volcengine.service.tls.TLSLogClient;

import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.List;

import static com.volcengine.model.tls.Const.LZ4;

public class DeleteResource {
    public static void main(String[] args) throws LogException {
        String endPoint = System.getenv("endPoint");
        String region = System.getenv("region");
        String ak = System.getenv("ak");
        String sk = System.getenv("sk");
        String token = System.getenv("token");

        ClientConfig cfg = new ClientConfig(endPoint, region, ak, sk, token);
        TLSLogClient client = ClientBuilder.newClient(cfg);

        // 1.列举Project
        DescribeProjectsRequest dprReq = new DescribeProjectsRequest();
        dprReq.setPageNumber(1);
        dprReq.setPageSize(100);
        dprReq.setProjectName("consumer-demo-project-");
        DescribeProjectsResponse dpr = client.describeProjects(dprReq);
        System.out.println("DescribeProjects: " + dpr);

        // 2. 删除Project
        for (ProjectInfo project : dpr.getProjects()) {
            // 列举topic
            DescribeTopicsRequest dtrReq = new DescribeTopicsRequest();
            dtrReq.setProjectId(project.getProjectId());
            DescribeTopicsResponse dtr = client.describeTopics(dtrReq);
            System.out.println("DescribeTopics: " + dtr);
            
            // 删除topic
            if (dtr.getTopic() != null) {
                for (TopicInfo topic : dtr.getTopic()) {
                    DeleteTopicRequest dtrReq1 = new DeleteTopicRequest(topic.getTopicId());
                    DeleteTopicResponse dtrResp = client.deleteTopic(dtrReq1);
                    System.out.println("DeleteTopic: " + dtrResp);
                }
            }

            System.out.println("DeleteProject: " + project.getProjectId());
            // 删除project
            DeleteProjectRequest dprReq1 = new DeleteProjectRequest(project.getProjectId());
            DeleteProjectResponse dprResp = client.deleteProject(dprReq1);
            System.out.println("DeleteProject: " + dprResp);
        }

        client.destroy();
    }
}
