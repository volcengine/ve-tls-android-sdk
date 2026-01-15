package com.volcengine.integration;

import com.volcengine.model.tls.request.CreateRuleRequest;
import com.volcengine.model.tls.request.DescribeRuleV2Request;
import com.volcengine.model.tls.response.CreateRuleResponse;
import com.volcengine.model.tls.response.DescribeRuleV2Response;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class DescribeRuleV2IntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void describe_rule_v2_after_create() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real rule v2 test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    try {
      setup.createProjectTopicIndex("it-rulev2-project-", "it-rulev2-topic-");

      CreateRuleRequest crr = new CreateRuleRequest();
      crr.setTopicId(setup.topicId);
      crr.setRuleName("it-rulev2-" + System.currentTimeMillis());
      crr.setLogType("minimalist_log");
      crr.setInputType(0);
      crr.setPaths(Collections.singletonList("/var/log/*.log"));
      CreateRuleResponse crrResp = setup.client.createRule(crr);
      assertNotNull(crrResp);

      DescribeRuleV2Response resp = setup.client.describeRuleV2(new DescribeRuleV2Request(crrResp.getRuleId()));
      assertNotNull(resp);
      assertEquals(crrResp.getRuleId(), resp.getRuleInfo().getRuleId());
    } finally {
      setup.deleteAll();
      setup.close();
    }
  }
}
