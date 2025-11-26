package com.volcengine.tls;

import com.volcengine.model.ServiceInfo;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.service.tls.TLSHttpUtil;
import com.volcengine.util.Const;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.Assert;

import java.util.ArrayList;

public class TLSClientAuthIsolationTest {
    @Test
    public void authIsolation_shouldSignWithDistinctCredentials() throws Exception {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            String endpoint = "http://" + server.getHostName() + ":" + server.getPort();
            String region = "cn-beijing";

            // client1
            ClientConfig cfg1 = new ClientConfig(endpoint, region, "AK_user1", "SK_user1", "ST_user1");
            ServiceInfo si1 = ClientConfig.initServiceInfo(cfg1);
            TLSHttpUtil util1 = new TLSHttpUtil(si1, com.volcengine.service.tls.TLSHttpUtil.API_INFO_LIST);
            util1.setAccessKey(cfg1.getAccessKeyId());
            util1.setSecretKey(cfg1.getAccessKeySecret());
            util1.setSessionToken(cfg1.getSecurityToken());

            // client2
            ClientConfig cfg2 = new ClientConfig(endpoint, region, "AK_user2", "SK_user2", "ST_user2");
            ServiceInfo si2 = ClientConfig.initServiceInfo(cfg2);
            TLSHttpUtil util2 = new TLSHttpUtil(si2, com.volcengine.service.tls.TLSHttpUtil.API_INFO_LIST);
            util2.setAccessKey(cfg2.getAccessKeyId());
            util2.setSecretKey(cfg2.getAccessKeySecret());
            util2.setSessionToken(cfg2.getSecurityToken());

            // mock responses
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

            // trigger requests
            util1.json(com.volcengine.model.tls.Const.DESCRIBE_PROJECTS, new ArrayList<>(), "{}");
            util2.json(com.volcengine.model.tls.Const.DESCRIBE_PROJECTS, new ArrayList<>(), "{}");

            // assert auth headers differ
            String auth1 = server.takeRequest().getHeader(Const.Authorization);
            String auth2 = server.takeRequest().getHeader(Const.Authorization);
            Assert.assertNotNull("Auth1 should not be null", auth1);
            Assert.assertNotNull("Auth2 should not be null", auth2);
            Assert.assertNotEquals("Authorization should differ between clients", auth1, auth2);

            util1.destroy();
        } finally {
            server.shutdown();
        }
    }
}