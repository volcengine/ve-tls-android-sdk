package com.volcengine.tls.android.producer;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.File;

public final class ProducerNativeLoadInstrumentedTest extends TestCase {

    public void testNativeCreateAndDestroyWithoutSending() {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("https://tls-cn-beijing.volces.com")
                .setRegion("cn-beijing")
                .setTopicId("native-load-smoke")
                .setAccessKeyId("test-ak")
                .setAccessKeySecret("test-sk")
                .setDestroyFlusherWaitMs(1000)
                .setDestroySenderWaitMs(1000);

        LogProducerClient client = new LogProducerClient(config, null);
        client.updateEndpoint(
                "https://tls-cn-beijing.volces.com",
                "cn-beijing",
                "native-load-smoke");
        client.destroyLogProducer();
        assertTrue("native producer destroy timed out", client.awaitDestroy(5000));
    }

    public void testPersistentDurabilityModesCreateWalFilesWithoutSending() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (LogProducerConfig.PersistentDurability durability
                : LogProducerConfig.PersistentDurability.values()) {
            File persistentDir = new File(
                    context.getFilesDir(),
                    "producer-durability-" + durability.name() + "-" + System.nanoTime());
            LogProducerConfig config = new LogProducerConfig()
                    .setEndpoint("https://tls-cn-beijing.volces.com")
                    .setRegion("cn-beijing")
                    .setTopicId("native-durability-smoke")
                    .setAccessKeyId("test-ak")
                    .setAccessKeySecret("test-sk")
                    .setPersistent(true)
                    .setPersistentFilePath(persistentDir.getAbsolutePath())
                    .setPersistentMaxLogCount(64)
                    .setPersistentMaxFileSize(4096)
                    .setPersistentMaxFileCount(4)
                    .setPersistentDurability(durability)
                    .setDestroyFlusherWaitMs(1000)
                    .setDestroySenderWaitMs(1000);

            LogProducerClient client = new LogProducerClient(config, null);
            client.updateEndpoint(
                    "https://tls-cn-beijing.volces.com",
                    "cn-beijing",
                    "native-durability-smoke");
            assertTrue(new File(persistentDir, "manifest").isFile());
            assertTrue(new File(persistentDir, "checkpoint").isFile());
            assertTrue(new File(persistentDir, "seg-000001.log").isFile());
            client.destroyLogProducer();
            assertTrue("persistent producer destroy timed out", client.awaitDestroy(5000));
            deletePersistentTestDir(persistentDir);
        }
    }

    private static void deletePersistentTestDir(File dir) {
        new File(dir, "manifest").delete();
        new File(dir, "checkpoint").delete();
        new File(dir, "lease").delete();
        new File(dir, "seg-000001.log").delete();
        dir.delete();
    }
}
