package com.volcengine.model.tls.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DescribeRuleV2Request {
  private String ruleId;

  public DescribeRuleV2Request(String ruleId) {
    this.ruleId = ruleId;
  }

  public boolean CheckValidation() {
    return this.ruleId != null;
  }
}

