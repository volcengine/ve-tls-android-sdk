package com.volcengine.tls.android.producer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildConfigProvenanceTest {
    @Test
    public void cSdkCommitIsPinned() {
        assertEquals("736d953c306c4f6e447600dda6132f62b8290903", BuildConfig.VE_TLS_C_SDK_COMMIT);
    }
}
