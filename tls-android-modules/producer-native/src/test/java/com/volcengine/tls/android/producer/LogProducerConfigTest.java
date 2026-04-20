package com.volcengine.tls.android.producer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void stringCompressTypeParser_rejectsUnsupportedValue() {
        LogProducerConfig config = new LogProducerConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setCompressType("gzip"));
    }

    @Test
    public void stringCompressTypeParser_acceptsKnownValuesCaseInsensitive() {
        LogProducerConfig config = new LogProducerConfig();

        config.setCompressType("NoNe");
        assertEquals(LogProducerConfig.CompressType.NONE, config.getCompressType());

        config.setCompressType("Lz4");
        assertEquals(LogProducerConfig.CompressType.LZ4, config.getCompressType());
    }

    @Test
    public void freeze_rejectsFurtherMutation() {
        LogProducerConfig config = new LogProducerConfig();

        assertSame(config, config.freeze());
        assertThrows(IllegalStateException.class, () -> config.setPersistent(true));
    }

    @Test
    public void destroyWaitSplit_settersExposeStageSpecificTimeouts() {
        LogProducerConfig config = new LogProducerConfig()
                .setDestroyFlusherWaitMs(100)
                .setDestroySenderWaitMs(200);

        assertEquals(300, config.getDestroyWaitMs());
        assertEquals(100, config.getDestroyFlusherWaitMs());
        assertEquals(200, config.getDestroySenderWaitMs());
        assertTrue(config.isDestroyWaitSplitConfigured());
    }

    @Test
    public void legacyDestroyWait_clearsSplitConfiguration() {
        LogProducerConfig config = new LogProducerConfig()
                .setDestroyFlusherWaitMs(100)
                .setDestroySenderWaitMs(200)
                .setDestroyWaitMs(7);

        assertEquals(7, config.getDestroyWaitMs());
        assertEquals(0, config.getDestroyFlusherWaitMs());
        assertEquals(0, config.getDestroySenderWaitMs());
        assertFalse(config.isDestroyWaitSplitConfigured());
    }
}
