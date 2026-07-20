package com.volcengine.tls.android.producer;

import android.content.Context;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
    public void addTag_rejectsNullKeyOrValue() {
        LogProducerConfig config = new LogProducerConfig();

        assertThrows(IllegalArgumentException.class, () -> config.addTag(null, "v"));
        assertThrows(IllegalArgumentException.class, () -> config.addTag("k", null));
        assertThrows(IllegalArgumentException.class, () -> config.addTag(null, null));
    }

    @Test
    public void addTag_preservesInsertionOrderAndDuplicateKeys() {
        LogProducerConfig config = new LogProducerConfig()
                .addTag("k1", "v1")
                .addTag("k1", "v2")
                .addTag("k2", "v3");

        assertEquals(3, config.getTagCount());
        assertEquals("k1", config.getTagKey(0));
        assertEquals("v1", config.getTagValue(0));
        assertEquals("k1", config.getTagKey(1));
        assertEquals("v2", config.getTagValue(1));
        assertEquals("k2", config.getTagKey(2));
        assertEquals("v3", config.getTagValue(2));
    }

    @Test
    public void retryPolicy_defaultsMatchPhase1Contract() {
        LogProducerConfig config = new LogProducerConfig();

        assertEquals(0, config.getRetryMaxAttempts());
        assertEquals(90_000, config.getRetryTotalTimeoutMs());
        assertEquals(500, config.getRetryInitialIntervalMs());
        assertEquals(10_000, config.getRetryMaxIntervalMs());
    }

    @Test
    public void retryPolicy_rejectsOutOfRangeValues() {
        LogProducerConfig config = new LogProducerConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setRetryMaxAttempts(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryMaxAttempts(51));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryTotalTimeoutMs(0));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryInitialIntervalMs(99));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryInitialIntervalMs(30_001));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryMaxIntervalMs(999));
        assertThrows(IllegalArgumentException.class, () -> config.setRetryMaxIntervalMs(60_001));
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

        assertEquals(0, config.getDestroyWaitMs());
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

    @SuppressWarnings("deprecation")
    @Test
    public void contextConstructors_areDeprecatedAndDoNotPopulatePersistentPath() {
        for (Constructor<?> constructor : LogProducerConfig.class.getConstructors()) {
            boolean hasContextParameter = Arrays.stream(constructor.getParameterTypes())
                    .anyMatch(Context.class::equals);
            if (hasContextParameter) {
                assertTrue("@Deprecated expected on " + constructor,
                        constructor.isAnnotationPresent(Deprecated.class));
            }
        }

        LogProducerConfig config = new LogProducerConfig((Context) null)
                .setPersistent(true);

        assertNull(config.getPersistentFilePath());
    }
}
