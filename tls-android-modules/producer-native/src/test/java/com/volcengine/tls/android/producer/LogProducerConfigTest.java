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
    public void persistentDurability_defaultsToBufferedWal() {
        LogProducerConfig config = new LogProducerConfig();

        assertEquals(LogProducerConfig.PersistentDurability.BUFFERED_WAL,
                config.getPersistentDurability());
        assertFalse(config.isPersistentForceFlush());
    }

    @Test
    public void persistentOverflowPolicy_exposesAllContractValues() {
        assertArrayEquals(
                new LogProducerConfig.PersistentOverflowPolicy[] {
                        LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW,
                        LogProducerConfig.PersistentOverflowPolicy.BLOCK,
                        LogProducerConfig.PersistentOverflowPolicy.DROP_OLDEST_UNACKED,
                        LogProducerConfig.PersistentOverflowPolicy.DROP_NEWEST_SAMPLE
                },
                LogProducerConfig.PersistentOverflowPolicy.values());
    }

    @Test
    public void persistentCapacity_defaultsMatchContract() {
        LogProducerConfig config = new LogProducerConfig();

        assertEquals(0, config.getPersistentMaxBytes());
        assertEquals(0, config.getPersistentMaxRecords());
        assertEquals(0, config.getPersistentMaxSegments());
        assertEquals(85, config.getPersistentHighWatermarkPct());
        assertEquals(70, config.getPersistentLowWatermarkPct());
        assertEquals(LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW,
                config.getPersistentOverflowPolicy());
        assertEquals(10, config.getPersistentSampleEveryN());
        assertEquals(1000, config.getPersistentBlockTimeoutMs());
    }

    @Test
    public void persistentCapacity_settersAreFluentAndExposeValues() {
        LogProducerConfig config = new LogProducerConfig();

        assertSame(config, config
                .setPersistentMaxBytes(1024)
                .setPersistentMaxRecords(200)
                .setPersistentMaxSegments(4)
                .setPersistentHighWatermarkPct(90)
                .setPersistentLowWatermarkPct(60)
                .setPersistentOverflowPolicy(LogProducerConfig.PersistentOverflowPolicy.BLOCK)
                .setPersistentSampleEveryN(7)
                .setPersistentBlockTimeoutMs(2500));
        assertEquals(1024, config.getPersistentMaxBytes());
        assertEquals(200, config.getPersistentMaxRecords());
        assertEquals(4, config.getPersistentMaxSegments());
        assertEquals(90, config.getPersistentHighWatermarkPct());
        assertEquals(60, config.getPersistentLowWatermarkPct());
        assertEquals(LogProducerConfig.PersistentOverflowPolicy.BLOCK,
                config.getPersistentOverflowPolicy());
        assertEquals(7, config.getPersistentSampleEveryN());
        assertEquals(2500, config.getPersistentBlockTimeoutMs());
    }

    @Test
    public void persistentCapacity_settersRejectInvalidValues() {
        LogProducerConfig config = new LogProducerConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setPersistentMaxBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentMaxRecords(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentMaxSegments(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentHighWatermarkPct(0));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentHighWatermarkPct(101));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentLowWatermarkPct(0));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentLowWatermarkPct(101));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentOverflowPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentSampleEveryN(0));
        assertThrows(IllegalArgumentException.class, () -> config.setPersistentBlockTimeoutMs(0));
    }

    @Test
    public void persistentCreate_requiresLowWatermarkBelowHighWatermark() {
        LogProducerConfig config = persistentCreateConfig()
                .setPersistentHighWatermarkPct(70)
                .setPersistentLowWatermarkPct(70);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> config.validateForCreate("demo"));
        assertTrue(error.getMessage().contains("persistentLowWatermarkPct"));
        assertTrue(error.getMessage().contains("persistentHighWatermarkPct"));
    }

    @Test
    public void persistentCreate_requiresPositiveLegacyLimits() {
        LogProducerConfig missingFileCount = persistentCreateConfig()
                .setPersistentMaxFileCount(0);
        IllegalArgumentException fileCountError = assertThrows(
                IllegalArgumentException.class,
                () -> missingFileCount.validateForCreate("demo"));
        assertTrue(fileCountError.getMessage().contains("persistentMaxFileCount"));

        LogProducerConfig missingFileSize = persistentCreateConfig()
                .setPersistentMaxFileSize(0);
        IllegalArgumentException fileSizeError = assertThrows(
                IllegalArgumentException.class,
                () -> missingFileSize.validateForCreate("demo"));
        assertTrue(fileSizeError.getMessage().contains("persistentMaxFileSize"));

        LogProducerConfig missingLogCount = persistentCreateConfig()
                .setPersistentMaxLogCount(0);
        IllegalArgumentException logCountError = assertThrows(
                IllegalArgumentException.class,
                () -> missingLogCount.validateForCreate("demo"));
        assertTrue(logCountError.getMessage().contains("persistentMaxLogCount"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void legacyForceFlush_mapsToSyncWalAndBackToBuffered() {
        LogProducerConfig config = new LogProducerConfig().setPersistentForceFlush(true);

        assertTrue(config.isPersistentForceFlush());
        assertEquals(LogProducerConfig.PersistentDurability.SYNC_WAL,
                config.getPersistentDurability());

        config.setPersistentForceFlush(false);
        assertEquals(LogProducerConfig.PersistentDurability.BUFFERED_WAL,
                config.getPersistentDurability());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void explicitBufferedWal_conflictsWithLegacyForceFlush() {
        LogProducerConfig explicitFirst = new LogProducerConfig()
                .setPersistentDurability(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
        assertThrows(IllegalArgumentException.class,
                () -> explicitFirst.setPersistentForceFlush(true));

        LogProducerConfig legacyFirst = new LogProducerConfig().setPersistentForceFlush(true);
        assertThrows(IllegalArgumentException.class,
                () -> legacyFirst.setPersistentDurability(
                        LogProducerConfig.PersistentDurability.BUFFERED_WAL));
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

    private static LogProducerConfig persistentCreateConfig() {
        return new LogProducerConfig()
                .setEndpoint("endpoint")
                .setRegion("region")
                .setTopicId("topic")
                .setPersistent(true)
                .setPersistentFilePath("/tmp/producer")
                .setPersistentMaxFileCount(4)
                .setPersistentMaxFileSize(1024)
                .setPersistentMaxLogCount(100);
    }
}
