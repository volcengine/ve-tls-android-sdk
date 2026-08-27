package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("18270db61acb890f55201fb430e811e3680fb99f", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
