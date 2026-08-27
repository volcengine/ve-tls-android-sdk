package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("75a3415a32ecf6e4ee2088b745c196234de76625", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
