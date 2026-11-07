package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("3e368a18330f529f984e51f8d274ecd582d15ca6", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
