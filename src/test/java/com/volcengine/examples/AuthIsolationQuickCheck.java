package com.volcengine.examples;

import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.DescribeProjectsResponse;
import com.volcengine.service.tls.TLSLogClient;

public class AuthIsolationQuickCheck {
    public static void main(String[] args) throws Exception {
        String endpoint = "https://tls.*.com"; // 替换为真实地址
        String region = "cn-beijing";

        ClientConfig cfg1 = new ClientConfig(endpoint, region, "1", "2", "3");
        TLSLogClient c1 = ClientBuilder.newClient(cfg1);

        ClientConfig cfg2 = new ClientConfig(endpoint, region, "1", "2", "3");
        TLSLogClient c2 = ClientBuilder.newClient(cfg2);

        // 举例调用只读接口（确保返回成功或权限合理）
        DescribeProjectsRequest req = new DescribeProjectsRequest();
        DescribeProjectsResponse res = c1.describeProjects(req);
        System.out.println("describe projects success,response:" + res);
        DescribeProjectsResponse res2 = c2.describeProjects(req);
        System.out.println("describe projects success,response:" + res2);

        c1.destroy();
        c2.destroy();
    }
}