package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("bf6458281cf3071ec79b2c2305123046864b4b52", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
