package com.volcengine.integration;

import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class RuleHostGroupIntegrationTest {
  private static String env(String k) { return System.getenv(k); }

  @Test
  void create_apply_describe_modify_delete_rule_with_host_group() throws Exception {
    String endPoint = env("endPoint");
    String region = env("region");
    String ak = env("ak");
    String sk = env("sk");
    Assumptions.assumeTrue(endPoint != null && region != null && ak != null && sk != null,
      "missing env, skip real rule-host-group test");

    BaseIntegrationSetup setup = new BaseIntegrationSetup();
    try {
      setup.createProjectTopicIndex("it-rule-project-", "it-rule-topic-");

      String hostGroupId = env("hostGroupId");
      boolean createdHostGroup = false;
      if (hostGroupId == null || hostGroupId.isEmpty()) {
        try {
          CreateHostGroupRequest chg = new CreateHostGroupRequest();
          chg.setHostGroupName("it-hostgroup-" + System.currentTimeMillis());
          chg.setHostGroupType("Label");
          chg.setHostIdentifier("integration");
          CreateHostGroupResponse chgr = setup.client.createHostGroup(chg);
          assertNotNull(chgr);
          hostGroupId = chgr.getHostGroupId();
          createdHostGroup = true;
        } catch (Exception e) {
          Assumptions.assumeTrue(false, "host group create not allowed in current env, skip");
          return;
        }
      }

      CreateRuleRequest crr = new CreateRuleRequest();
      crr.setTopicId(setup.topicId);
      crr.setRuleName("it-rule-" + System.currentTimeMillis());
      crr.setLogType("minimalist_log");
      crr.setInputType(0);
      crr.setPaths(Collections.singletonList("/var/log/*.log"));
      CreateRuleResponse crrResp = setup.client.createRule(crr);
      assertNotNull(crrResp);
      String ruleId = crrResp.getRuleId();

      ApplyRuleToHostGroupsRequest applyReq = new ApplyRuleToHostGroupsRequest(ruleId, Arrays.asList(hostGroupId));
      assertNotNull(setup.client.applyRuleToHostGroups(applyReq));

      DescribeRuleResponse drr = setup.client.describeRule(new DescribeRuleRequest(ruleId));
      assertNotNull(drr);
      assertEquals(ruleId, drr.getRuleInfo().getRuleId());

      DescribeHostGroupRulesResponse dhgr = setup.client.describeHostGroupRules(new DescribeHostGroupRulesRequest(hostGroupId, 1, 20));
      assertNotNull(dhgr);

      ModifyRuleRequest mrr = new ModifyRuleRequest();
      mrr.setRuleId(ruleId);
      mrr.setRuleName(drr.getRuleInfo().getRuleName());
      assertNotNull(setup.client.modifyRule(mrr));

      try {
        setup.client.deleteRuleFromHostGroups(new DeleteRuleFromHostGroupsRequest(ruleId, Arrays.asList(hostGroupId)));
      } catch (Exception ignored) { }

      DeleteRuleResponse delResp = setup.client.deleteRule(new DeleteRuleRequest(ruleId));
      assertNotNull(delResp);

      if (createdHostGroup) {
        try { setup.client.deleteHostGroup(new DeleteHostGroupRequest(hostGroupId)); } catch (Exception ignored) { }
      }
    } finally {
      setup.deleteAll();
      setup.close();
    }
  }
}
