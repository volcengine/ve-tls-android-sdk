package com.volcengine.integration;

import com.volcengine.model.tls.request.CreateRuleRequest;
import com.volcengine.model.tls.request.DeleteRuleRequest;
import com.volcengine.model.tls.response.CreateRuleResponse;
import com.volcengine.model.tls.response.DeleteRuleResponse;
import com.volcengine.model.tls.DescribeRulesRequest;
import com.volcengine.model.tls.response.DescribeRulesResponse;
import com.volcengine.model.tls.RuleInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RulesPagingFilterIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void describe_rules_paging_and_filter() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real rules paging test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    List<String> createdRuleIds = new ArrayList<>();
    String prefix = "it-rules-paging-" + System.currentTimeMillis();
    try {
      setup.createProjectTopicIndex("it-rules-paging-project-", "it-rules-paging-topic-");

      for (int i = 0; i < 2; i++) {
        CreateRuleRequest crr = new CreateRuleRequest();
        crr.setTopicId(setup.topicId);
        crr.setRuleName(prefix + "-" + i);
        crr.setLogType("minimalist_log");
        crr.setInputType(0);
        crr.setPaths(Collections.singletonList("/var/log/*.log"));
        CreateRuleResponse resp = setup.client.createRule(crr);
        assertNotNull(resp);
        createdRuleIds.add(resp.getRuleId());
      }

      DescribeRulesRequest req = new DescribeRulesRequest();
      req.setProjectId(setup.projectId);
      req.setRuleName(prefix); // 模糊匹配
      req.setPageNumber(1);
      req.setPageSize(1);
      DescribeRulesResponse page1 = setup.client.describeRules(req);
      assertNotNull(page1);
      assertTrue(page1.getTotal() >= 2);
      List<RuleInfo> list1 = page1.getRuleInfos();
      assertEquals(1, list1.size());

      req.setPageNumber(2);
      DescribeRulesResponse page2 = setup.client.describeRules(req);
      assertNotNull(page2);
      List<RuleInfo> list2 = page2.getRuleInfos();
      assertEquals(1, list2.size());
      assertNotEquals(list1.get(0).getRuleId(), list2.get(0).getRuleId());
    } finally {
      for (String rid : createdRuleIds) {
        try { DeleteRuleResponse d = setup.client.deleteRule(new DeleteRuleRequest(rid)); assertNotNull(d);} catch (Exception ignored) {}
      }
      setup.deleteAll();
      setup.close();
    }
  }
}

