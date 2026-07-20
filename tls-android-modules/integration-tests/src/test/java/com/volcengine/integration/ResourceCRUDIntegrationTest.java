package com.volcengine.integration;

import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceCRUDIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void project_topic_index_modify_and_describe() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real integration test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    try {
      setup.createProjectTopicIndex("it-project-", "it-topic-");

      ModifyProjectResponse mpr = setup.client.modifyProject(new ModifyProjectRequest(setup.projectId, null, "integration-crud"));
      assertNotNull(mpr);
      DescribeProjectResponse dpr = setup.client.describeProject(new DescribeProjectRequest(setup.projectId));
      assertNotNull(dpr);
      assertEquals("integration-crud", dpr.getProjectInfo().getDescription());

      ModifyTopicRequest mtr = new ModifyTopicRequest();
      mtr.setTopicId(setup.topicId);
      mtr.setTtl(14);
      mtr.setDescription("integration-topic");
      ModifyTopicResponse mtrResp = setup.client.modifyTopic(mtr);
      assertNotNull(mtrResp);
      DescribeTopicResponse dtr = setup.client.describeTopic(new DescribeTopicRequest(setup.topicId));
      assertNotNull(dtr);
      assertEquals(14, dtr.getTopicInfo().getTtl());

      DescribeProjectsResponse dps = setup.client.describeProjects(new DescribeProjectsRequest());
      assertNotNull(dps);
      assertTrue(dps.getTotal() >= 1);

      DescribeTopicsRequest dtsReq = new DescribeTopicsRequest();
      dtsReq.setProjectId(dpr.getProjectInfo().getProjectId());
      DescribeTopicsResponse dts = setup.client.describeTopics(dtsReq);
      assertNotNull(dts);
      assertTrue(dts.getTotal() >= 1);

      ModifyIndexResponse mir = setup.client.modifyIndex(new ModifyIndexRequest(setup.topicId, new FullTextInfo(true, ",-;|", false), null));
      assertNotNull(mir);
      DescribeIndexResponse dir = setup.client.describeIndex(new DescribeIndexRequest(setup.topicId));
      assertNotNull(dir);
      assertEquals(true, dir.getFullTextInfo().isCaseSensitive());
    } finally {
      setup.deleteAll();
      setup.close();
    }
  }
}
