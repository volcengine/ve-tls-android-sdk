package com.volcengine.integration;

import com.volcengine.model.tls.DescribeRulesRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RulesReadIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void describe_rules_by_project() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real rules read test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    try {
      setup.createProjectTopicIndex("it-rules-project-", "it-rules-topic-");
      DescribeRulesRequest req = new DescribeRulesRequest();
      req.setProjectId(setup.projectId);
      assertNotNull(setup.client.describeRules(req));
    } finally {
      setup.deleteAll();
      setup.close();
    }
  }
}

