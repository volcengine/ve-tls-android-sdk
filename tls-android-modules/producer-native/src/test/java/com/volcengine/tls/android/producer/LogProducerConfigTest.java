package com.volcengine.tls.android.producer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class LogProducerConfigTest {

    @Test
    public void compressType_exposesOnlyNoneAndLz4() {
        assertArrayEquals(
                new LogProducerConfig.CompressType[] {
                        LogProducerConfig.CompressType.NONE,
                        LogProducerConfig.CompressType.LZ4
                },
                LogProducerConfig.CompressType.values()
        );
    }
}
