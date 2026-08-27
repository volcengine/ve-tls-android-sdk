package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("17c5bcd99327d0406228460fb3340e90cd563266", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
