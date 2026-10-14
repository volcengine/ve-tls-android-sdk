package com.volcengine.tls.producer.sample;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerificationConfigTest {
    @Test
    public void fromMap_parsesBurstScenarioAndOverrides() {
        Map<String, String> values = new HashMap<>();
        values.put("scenario", "burst");
        values.put("sendCount", "8");
        values.put("callbackTimeoutMs", "9000");
        values.put("compress", "none");
        values.put("persistent", "true");
        values.put("topicId", "topic-a");

        VerificationConfig config = VerificationConfig.fromMap(values, defaults());

        assertEquals(VerificationConfig.Scenario.BURST, config.getScenario());
        assertEquals(8, config.getSendCount());
        assertEquals(9000, config.getCallbackTimeoutMs());
        assertEquals("none", config.getCompress());
        assertTrue(config.isPersistent());
        assertEquals("topic-a", config.getTopicId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromMap_rejectsUnsupportedCompress() {
        Map<String, String> values = new HashMap<>();
        values.put("compress", "gzip");

        VerificationConfig.fromMap(values, defaults());
    }

    @Test
    public void fromMap_defaultsMissingTokenToEmpty() {
        Map<String, String> defaults = defaults();
        defaults.remove("token");
        VerificationConfig config = VerificationConfig.fromMap(new HashMap<>(), defaults);
        assertEquals("", config.toProducerConfig().getSecurityToken());
    }

    @Test
    public void fromMap_preservesExplicitToken() {
        Map<String, String> values = new HashMap<>();
        values.put("token", "test-session-token");
        VerificationConfig config = VerificationConfig.fromMap(values, defaults());
        assertEquals("test-session-token", config.toProducerConfig().getSecurityToken());
    }

    private static Map<String, String> defaults() {
        Map<String, String> values = new HashMap<>();
        values.put("endpoint", "https://tls-cn-beijing.volces.com");
        values.put("region", "cn-beijing");
        values.put("topicId", "topic-default");
        values.put("ak", "ak");
        values.put("sk", "sk");
        values.put("token", "");
        return values;
    }
}
