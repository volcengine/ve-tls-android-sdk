package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("08f33affc2f346f92dc0734cbb92330dd272156c", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
