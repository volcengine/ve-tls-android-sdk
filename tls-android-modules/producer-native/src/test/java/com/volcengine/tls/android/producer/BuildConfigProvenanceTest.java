package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("1d41ec4edb850ee7dd0b7f63c49738d6a9669c21", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
