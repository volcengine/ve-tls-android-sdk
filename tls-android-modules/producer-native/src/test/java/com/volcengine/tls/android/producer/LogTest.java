package com.volcengine.tls.android.producer;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class LogTest {

    @Test
    public void putContent_rejectsNullKey() {
        Log log = new Log();

        assertThrows(IllegalArgumentException.class, () -> log.putContent(null, "value"));
    }

    @Test
    public void putContent_keepsNullValueAsExplicitEmptyStringIntent() {
        Log log = new Log().putContent("key", null);

        assertEquals(Collections.singletonMap("key", ""), log.getContent());
    }

    @Test
    public void putContents_rejectsNullKeyAndNormalizesNullValue() {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("k1", "v1");
        contents.put("k2", null);
        contents.put(null, "boom");

        Log log = new Log();
        assertThrows(IllegalArgumentException.class, () -> log.putContents(contents));

        Log validLog = new Log();
        validLog.putContents(new LinkedHashMap<String, String>() {{
            put("k1", "v1");
            put("k2", null);
        }});

        assertEquals("v1", validLog.getContent().get("k1"));
        assertEquals("", validLog.getContent().get("k2"));
    }
}
