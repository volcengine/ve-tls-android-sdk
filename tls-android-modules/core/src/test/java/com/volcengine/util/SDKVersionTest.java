package com.volcengine.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SDKVersionTest {
    @Test
    public void explicitModuleAgentUsesSharedVersion() {
        assertEquals("volc-tls-android/producer/v" + SDKVersion.getVERSION(), SDKVersion.getAGENT("producer"));
        assertEquals("volc-tls-android/full/v" + SDKVersion.getVERSION(), SDKVersion.getAGENT("full"));
    }

    @Test
    public void emptyModuleKeepsDefaultAgent() {
        assertEquals(SDKVersion.getAGENT(), SDKVersion.getAGENT(""));
        assertEquals(SDKVersion.getAGENT(), SDKVersion.getAGENT(null));
    }
}
