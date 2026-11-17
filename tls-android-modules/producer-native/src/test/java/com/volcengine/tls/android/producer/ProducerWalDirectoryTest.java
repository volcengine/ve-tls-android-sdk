package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.ConfigSnapshot;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ProducerWalDirectoryTest {
    @Rule public final TemporaryFolder filesDir = new TemporaryFolder();

    @Test
    public void legacyPathRetainsProcessScopedDirectory() {
        LogProducerConfig config = new LogProducerConfig().setPersistent(true)
                .setPersistentFilePath("/app/tls");

        assertEquals("/app/tls", new ConfigSnapshot(config, "example.app").getPersistentFilePath());
        assertEquals("/app/tls/example.app_push",
                new ConfigSnapshot(config, "example.app:push").getPersistentFilePath());
    }

    @Test
    public void idSelectsFixedDirectoryAndRejectsRebinding() throws Exception {
        File files = filesDir.getRoot();
        String expected = new File(files, "com.volcengine.tls/producer/push.prod").getAbsolutePath();
        assertEquals(expected, ProducerWalDirectory.resolve(files, "push.prod", null));
        assertEquals(expected, ProducerWalDirectory.resolve(files, "push.prod", null));
        assertThrows(IllegalArgumentException.class, () -> ProducerWalDirectory.resolve(
                files, "push.prod", legacyDirectory("another-wal").getAbsolutePath()));
    }

    @Test
    public void migratedIdUsesExactLegacyDirectoryInAnyProcess() throws Exception {
        File files = filesDir.getRoot();
        File legacy = legacyDirectory("example.app_push").getCanonicalFile();
        String resolved = ProducerWalDirectory.resolve(files, "push-prod", legacy.getAbsolutePath());
        assertEquals(legacy.getAbsolutePath(), resolved);
        LogProducerConfig config = new LogProducerConfig().setPersistent(true).setProducerId("push-prod");

        String afterMove = ProducerWalDirectory.resolve(files, "push-prod", null);
        assertEquals(resolved, new ConfigSnapshot(config, "example.app:other", afterMove).getPersistentFilePath());
        assertEquals(resolved, ProducerWalDirectory.resolve(files, "push-prod", legacy.getAbsolutePath()));
        assertFalse(new File(files, "com.volcengine.tls/producer/push-prod").exists());
        assertTrue(new File(legacy, "manifest").isFile());
    }

    @Test
    public void rejectsMissingLegacyWalAndRefusesToRedirectARecordedBinding() throws Exception {
        File files = filesDir.getRoot();
        File legacy = filesDir.newFolder("empty");
        assertThrows(IllegalArgumentException.class,
                () -> ProducerWalDirectory.resolve(files, "push-prod", legacy.getPath()));

        File manifest = new File(legacy, "manifest");
        assertTrue(manifest.createNewFile());
        assertEquals(legacy.getCanonicalPath(), ProducerWalDirectory.resolve(files, "push-prod", legacy.getPath()));
        assertTrue(manifest.delete());

        LogProducerInitializationException failure = assertThrows(LogProducerInitializationException.class,
                () -> ProducerWalDirectory.resolve(files, "push-prod", null));
        assertEquals(LogProducerInitializationException.Reason.WAL_OPEN_FAILED, failure.getReason());
        assertFalse(new File(files, "com.volcengine.tls/producer/push-prod").exists());
    }

    @Test
    public void incompleteBindingDoesNotFallBackToEmptyWal() throws Exception {
        File files = filesDir.getRoot();
        ProducerWalDirectory.resolve(files, "push-prod", null);
        File binding = new File(files, "com.volcengine.tls/producer-bindings/push-prod.path");
        try (FileOutputStream out = new FileOutputStream(binding)) {
            out.write(0);
        }

        LogProducerInitializationException failure = assertThrows(LogProducerInitializationException.class,
                () -> ProducerWalDirectory.resolve(files, "push-prod", null));
        assertEquals(LogProducerInitializationException.Reason.WAL_OPEN_FAILED, failure.getReason());
    }

    @Test
    public void producerIdUsesIosIdentifierRules() {
        LogProducerConfig config = new LogProducerConfig();
        assertEquals("prod-42.push_a", config.setProducerId("prod-42.push_a").getProducerId());
        for (String invalid : new String[]{"", ".", "..", "a/b", "测试", new String(new char[65]).replace('\0', 'a')}) {
            assertThrows(IllegalArgumentException.class, () -> config.setProducerId(invalid));
        }
    }

    @Test
    public void persistentIdRequiresApplicationOpen() {
        LogProducerConfig config = new LogProducerConfig().setPersistent(true).setProducerId("push-prod");
        assertThrows(IllegalArgumentException.class, () -> LogProducerClient.forTest(config, null, "example.app"));
    }

    private File legacyDirectory(String name) throws IOException {
        File directory = filesDir.newFolder(name);
        assertTrue(new File(directory, "manifest").createNewFile());
        return directory;
    }
}
