package com.volcengine.integration;

import com.volcengine.model.tls.ClientBuilder;
import com.volcengine.model.tls.ClientConfig;
import com.volcengine.service.tls.TLSLogClient;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import com.volcengine.model.tls.FullTextInfo;
import java.util.concurrent.Callable;

/**
 * Base initializer for real environment integration tests.
 * Creates Project/Topic/Index and exposes IDs for follow-up tests.
 * Reads env: endPoint, region, ak, sk, token
 */
public class BaseIntegrationSetup {
  public final TLSLogClient client;
  public final String region;
  public String projectId;
  public String topicId;

  public BaseIntegrationSetup() {
    String endPoint = System.getenv("endPoint");
    this.region = System.getenv("region");
    String ak = System.getenv("ak");
    String sk = System.getenv("sk");
    String token = System.getenv("token");
    ClientConfig cfg = new ClientConfig(endPoint, region, ak, sk, token);
    try {
      this.client = ClientBuilder.newClient(cfg);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void createProjectTopicIndex(String projectNamePrefix, String topicNamePrefix) throws Exception {
    String projectName = projectNamePrefix + System.currentTimeMillis();
    CreateProjectResponse cpr = retry(() -> client.createProject(new CreateProjectRequest(projectName, region, "integration")));
    this.projectId = cpr.getProjectId();

    CreateTopicRequest ctr = new CreateTopicRequest();
    ctr.setProjectId(projectId);
    ctr.setTopicName(topicNamePrefix + System.currentTimeMillis());
    ctr.setTtl(7);
    CreateTopicResponse ctrResp;
    try {
      ctrResp = retry(() -> client.createTopic(ctr));
    } catch (Exception e) {
      safeDeleteProject();
      throw e;
    }
    this.topicId = ctrResp.getTopicId();

    try {
      retry(() -> client.createIndex(new CreateIndexRequest(topicId, new FullTextInfo(false, ",-;", false), null)));
    } catch (Exception e) {
      safeDeleteTopic();
      safeDeleteProject();
      throw e;
    }
  }

  public void deleteAll() throws Exception {
    safeDeleteTopic();
    safeDeleteProject();
  }

  public void close() { try { client.destroy(); } catch (Exception ignored) { } }

  private <T> T retry(Callable<T> task) throws Exception {
    int attempts = 0;
    long backoff = 200;
    while (true) {
      try { return task.call(); } catch (Exception e) {
        attempts++;
        if (attempts >= 3) throw e;
        Thread.sleep(backoff);
        backoff = Math.min(backoff * 2, 1000);
      }
    }
  }

  private void safeDeleteTopic() {
    if (topicId != null) {
      try { retry(() -> { client.deleteIndex(new DeleteIndexRequest(topicId)); return null; }); } catch (Exception ignored) { }
      try { retry(() -> { client.deleteTopic(new DeleteTopicRequest(topicId)); return null; }); } catch (Exception ignored) { }
      topicId = null;
    }
  }

  private void safeDeleteProject() {
    if (projectId != null) {
      try { retry(() -> { client.deleteProject(new DeleteProjectRequest(projectId)); return null; }); } catch (Exception ignored) { }
      projectId = null;
    }
  }
}
