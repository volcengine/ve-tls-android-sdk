package com.volcengine.tls.android.demo;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigLoaderTest {
    @Test
    public void hasRequiredConfig_requiresEndpointRegionAkSkTopicId() {
        Properties props = new Properties();
        assertFalse(ConfigLoader.hasRequiredConfig(props));

        props.setProperty("endPoint", "https://tls-cn-test.volces.com");
        props.setProperty("region", "cn-test");
        props.setProperty("ak", "ak");
        props.setProperty("sk", "sk");
        props.setProperty("topicId", "topic");
        assertTrue(ConfigLoader.hasRequiredConfig(props));
    }

    @Test
    public void normalizeCompressValue_acceptsOnlyNoneOrLz4() {
        assertEquals("", ConfigLoader.normalizeCompressValue(null));
        assertEquals("", ConfigLoader.normalizeCompressValue(""));
        assertEquals("lz4", ConfigLoader.normalizeCompressValue("LZ4"));
        assertEquals("none", ConfigLoader.normalizeCompressValue("NONE"));
        assertEquals("", ConfigLoader.normalizeCompressValue("unexpected"));
    }
}
