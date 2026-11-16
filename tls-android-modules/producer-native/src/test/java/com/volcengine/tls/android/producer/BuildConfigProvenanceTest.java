package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("45b7cf893bd5248f0f082402c3b23d3c0bfb7e4f", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
