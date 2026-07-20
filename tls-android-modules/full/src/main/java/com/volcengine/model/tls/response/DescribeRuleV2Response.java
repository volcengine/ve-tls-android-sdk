package com.volcengine.model.tls.response;

import com.alibaba.fastjson.annotation.JSONField;
import com.volcengine.model.Header;
import com.volcengine.model.tls.RuleInfo;
import com.volcengine.model.tls.exception.LogException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static com.volcengine.model.tls.Const.*;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
public class DescribeRuleV2Response extends CommonResponse {
  @JSONField(name = CS_ACCOUNT_CHANNEL)
  String csAccountChannel;
  @JSONField(name = PROJECT_ID)
  String projectId;
  @JSONField(name = PROJECT_NAME)
  String projectName;
  @JSONField(name = TOPIC_ID)
  String topicId;
  @JSONField(name = TOPIC_NAME)
  String topicName;
  @JSONField(name = RULE_INFO)
  RuleInfo ruleInfo;
  @JSONField(name = ALLOW_DELETE)
  boolean allowDelete;
  @JSONField(name = ALLOW_EDIT)
  boolean allowEdit;
  @JSONField(name = RULE_TYPE)
  Integer ruleType;

  public DescribeRuleV2Response(Header[] headers) { super(headers); }

  @Override
  public DescribeRuleV2Response deSerialize(byte[] data, Class clazz) throws LogException {
    DescribeRuleV2Response r = (DescribeRuleV2Response) super.deSerialize(data, clazz);
    this.setCsAccountChannel(r.getCsAccountChannel());
    this.setProjectId(r.getProjectId());
    this.setProjectName(r.getProjectName());
    this.setTopicId(r.getTopicId());
    this.setTopicName(r.getTopicName());
    this.setRuleInfo(r.getRuleInfo());
    this.setAllowDelete(r.isAllowDelete());
    this.setAllowEdit(r.isAllowEdit());
    this.setRuleType(r.getRuleType());
    return this;
  }
}

