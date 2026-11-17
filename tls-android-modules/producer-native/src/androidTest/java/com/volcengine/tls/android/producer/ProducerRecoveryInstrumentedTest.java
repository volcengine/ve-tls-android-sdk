package com.volcengine.tls.android.producer;

import android.app.Application;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import com.volcengine.tls.android.producer.internal.ProcessUtil;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

/** Loopback transport with fake credentials; exercises Java -> JNI -> C -> HTTP. */
public final class ProducerRecoveryInstrumentedTest extends TestCase {
    public void testPersistentRetriesBeyondRequestBudget() throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) -> attempt < 3 ? 503 : 200)) {
            File dir = testDir();
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(config(server, dir), completion::accept);
            try {
                client.addLog(log(), 1);
                completion.assertSuccess();
                assertEquals(3, server.requests.get());
                assertFalse("retry must preserve the encoded log", server.bodyChanged.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
            assertEquals("only the terminal success is reported", 1, completion.calls.get());
        }
    }

    public void testTransientHttpOptInRetriesOnlyCoreAllowlist() throws Exception {
        assertTransientHttpResponse(408, null, true, true);
        assertTransientHttpResponse(499, null, true, true);
        for (String code : Arrays.asList("ClientCanceled", "Timeout", "RequestTimeout", "ConnectionClosed")) {
            assertTransientHttpResponse(400, code, true, true);
        }
        assertTransientHttpResponse(400, "InvalidParameter", true, false);
        assertTransientHttpResponse(400, null, true, false);
    }

    public void testDefaultUsesStandardHttpRetryPolicy() throws Exception {
        assertTransientHttpResponse(408, null, false, false);
        assertTransientHttpResponse(499, null, false, false);
        assertTransientHttpResponse(400, "RequestTimeout", false, false);
        assertTransientHttpResponse(503, null, false, true);
    }

    private void assertTransientHttpResponse(int status, String errorCode, boolean enabled, boolean retries)
            throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) -> attempt == 1 ? status : 200, errorCode)) {
            File dir = testDir();
            File checkpoint = new File(ProcessUtil.rewritePersistentPath(dir.getAbsolutePath(),
                    ProcessUtil.getCurrentProcessName()), "checkpoint");
            Completion completion = new Completion();
            LogProducerConfig config = recoveryGapConfig(server, dir, LogProducerConfig.PersistentDurability.SYNC_WAL)
                    .setRetryMaxAttempts(2).setRetryTotalTimeoutMs(3000);
            if (enabled) {
                config.setRetryTransientHttpResponses(true);
            }
            LogProducerClient client = new LogProducerClient(config, completion::accept);
            try {
                try {
                    client.addLog(log(), 1);
                    if (retries) {
                        completion.assertSuccess();
                        assertFalse("retry changed the encoded batch", server.bodyChanged.get());
                        awaitCheckpoint(checkpoint, completion.result.get().getEndId(), 0);
                    } else {
                        assertTrue("terminal failure timed out", completion.done.await(5, TimeUnit.SECONDS));
                        LogProducerResult result = completion.result.get();
                        assertFalse(result.isSuccess());
                        assertFalse(result.isRetryable());
                        assertEquals(status, result.getHttpCode());
                        if (errorCode != null) {
                            assertEquals(errorCode, result.getErrorCode());
                        }
                    }
                } finally {
                    stop(client);
                }
                if (!retries && checkpoint.exists()) {
                    awaitCheckpoint(checkpoint, 0, 0);
                }
                assertEquals(retries ? 2 : 1, server.requests.get());
                assertEquals(1, completion.calls.get());
                server.assertHealthy();
            } finally {
                deleteTestDir(dir);
            }
        }
    }

    public void testBufferedAuthRetainResumesAfterCredentialUpdate() throws Exception {
        assertAuthResume(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncAuthRetainResumesAfterCredentialUpdate() throws Exception {
        assertAuthResume(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    private void assertAuthResume(LogProducerConfig.PersistentDurability durability) throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) ->
                headers.contains("x-security-token: renewed-test-token") ? 200 : 403)) {
            File dir = testDir();
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(
                    config(server, dir).setPersistentDurability(durability), completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue("authentication request missing", server.firstFailure.await(10, TimeUnit.SECONDS));
                assertFalse("retained authentication failure is not terminal",
                        completion.done.await(300, TimeUnit.MILLISECONDS));
                client.resetSecurityToken("renewed-test-ak", "renewed-test-sk", "renewed-test-token");
                completion.assertSuccess();
                assertEquals("no retry until credentials change", 2, server.requests.get());
                assertFalse("credential update must preserve the batch", server.bodyChanged.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
            assertEquals(1, completion.calls.get());
        }
    }

    public void testCloseDuringRetryRetainsWalForNextClient() throws Exception {
        AtomicBoolean available = new AtomicBoolean();
        try (TestServer server = new TestServer((attempt, headers, body) -> available.get() ? 200 : 503)) {
            File dir = testDir();
            Completion first = new Completion();
            LogProducerClient client = new LogProducerClient(config(server, dir), first::accept);
            try {
                client.addLog(log(), 1);
                assertTrue("must enter cross-cycle retry before close",
                        server.repeatedFailure.await(10, TimeUnit.SECONDS));
                stop(client);
                assertEquals("retry exhaustion is not terminal", 0, first.calls.get());
                available.set(true);
                Completion recovered = new Completion();
                LogProducerClient next = new LogProducerClient(config(server, dir), recovered::accept);
                try {
                    // Client creation is lazy. This opens/replays the existing WAL without adding a log.
                    next.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                    recovered.assertSuccess();
                } finally {
                    stop(next);
                }
                assertEquals(1, recovered.calls.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
        }
    }

    public void testBufferedRecoveryDoesNotResendSuccessAfterFailure() throws Exception {
        assertRecoveryDoesNotResendSuccessAfterFailure(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncRecoveryDoesNotResendSuccessAfterFailure() throws Exception {
        assertRecoveryDoesNotResendSuccessAfterFailure(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    public void testBufferedNewFailureAfterAcknowledgedRestartSurvives() throws Exception {
        assertNewFailureAfterRestartSurvives(LogProducerConfig.PersistentDurability.BUFFERED_WAL, false);
    }

    public void testSyncNewFailureAfterAcknowledgedRestartSurvives() throws Exception {
        assertNewFailureAfterRestartSurvives(LogProducerConfig.PersistentDurability.SYNC_WAL, false);
    }

    public void testBufferedNewFailureAfterSparseRestartSurvives() throws Exception {
        assertNewFailureAfterRestartSurvives(LogProducerConfig.PersistentDurability.BUFFERED_WAL, true);
    }

    public void testSyncNewFailureAfterSparseRestartSurvives() throws Exception {
        assertNewFailureAfterRestartSurvives(LogProducerConfig.PersistentDurability.SYNC_WAL, true);
    }

    public void testBufferedOwnerKeepsDirectoryAfterLeaseExpires() throws Exception {
        assertOwnerKeepsDirectoryAfterLeaseExpires(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncOwnerKeepsDirectoryAfterLeaseExpires() throws Exception {
        assertOwnerKeepsDirectoryAfterLeaseExpires(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    public void testBufferedProcessKillPreservesSparseAcknowledgements() throws Exception {
        assertProcessKillPreservesSparseAcknowledgements(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncProcessKillPreservesSparseAcknowledgements() throws Exception {
        assertProcessKillPreservesSparseAcknowledgements(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    public void testBufferedProducerIdTakesOverLegacyWalAcrossProcesses() throws Exception {
        assertProducerIdTakesOverLegacyWal(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncProducerIdTakesOverLegacyWalAcrossProcesses() throws Exception {
        assertProducerIdTakesOverLegacyWal(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    private void assertProducerIdTakesOverLegacyWal(LogProducerConfig.PersistentDurability durability)
            throws Exception {
        Application application = (Application) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        final long eventTime = 1700000000123L;
        AtomicBoolean allowReplay = new AtomicBoolean();
        try (TestServer server = new TestServer((attempt, headers, body) -> {
            String event = singleEventId(body);
            return !allowReplay.get() && ("B".equals(event) || "D".equals(event)) ? 400 : 200;
        })) {
            File dir = testDir();
            String producerId = dir.getName();
            RecoveryProcessFixture oldOwner = null;
            RecoveryProcessFixture reopened = null;
            LogProducerClient migrated = null;
            try {
                oldOwner = new RecoveryProcessFixture(server.endpoint(), dir, durability);
                assertFalse(ProcessUtil.getCurrentProcessName().equals(oldOwner.processName()));
                File actualDirectory = new File(ProcessUtil.rewritePersistentPath(
                        dir.getAbsolutePath(), oldOwner.processName()));
                File checkpoint = new File(actualDirectory, "checkpoint");
                oldOwner.add("A", eventTime);
                LogProducerResult a = assertResultAndBody(server, oldOwner.results, "A", 200);
                oldOwner.add("B", eventTime + 1);
                LogProducerResult b = assertResultAndBody(server, oldOwner.results, "B", 400);
                oldOwner.add("C", eventTime + 2);
                LogProducerResult c = assertResultAndBody(server, oldOwner.results, "C", 200);
                awaitCheckpoint(checkpoint, a.getEndId(), c.getStartId());
                oldOwner.add("D", eventTime + 3);
                LogProducerResult d = assertResultAndBody(server, oldOwner.results, "D", 400);
                assertTrue(a.getEndId() < b.getStartId() && b.getEndId() < c.getStartId()
                        && c.getEndId() < d.getStartId());

                LogProducerConfig firstSwitch = recoveryGapConfig(server, dir, durability)
                        .setPersistentFilePath(null)
                        .setProducerId(producerId, actualDirectory.getAbsolutePath());
                assertProducerIdBusy(application, firstSwitch);
                assertEquals(4, server.requests.get());

                int oldPid = oldOwner.pid();
                oldOwner.kill();
                oldOwner.close();
                oldOwner = null;
                byte[] lease = readFile(new File(actualDirectory, "lease"));
                long heartbeat = ByteBuffer.wrap(lease).order(ByteOrder.nativeOrder())
                        .getLong(leaseChecksumOffset(lease) - 16);
                allowReplay.set(true);
                BlockingQueue<LogProducerResult> results = new LinkedBlockingQueue<>();
                LogProducerConfig idOnly = recoveryGapConfig(server, dir, durability)
                        .setPersistentFilePath(null).setProducerId(producerId);
                migrated = openProducer(application, idOnly, results::add);
                long leaseAgeAtOpen = System.currentTimeMillis() - heartbeat;
                assertTrue("takeover must not wait for lease expiry", leaseAgeAtOpen >= 0 && leaseAgeAtOpen < 60000);
                Map<String, LogProducerResult> recovered = assertSuccessfulResultsAndBodies(server, results, "B", "D");
                assertSameLogIdRange(b, recovered.get("B"));
                assertSameLogIdRange(d, recovered.get("D"));
                awaitCheckpoint(checkpoint, d.getEndId(), 0);
                assertProducerIdBusy(application, idOnly);
                stop(migrated);
                migrated = null;

                // The same ID now opens from an Android service process again using only the ID.
                reopened = new RecoveryProcessFixture(server.endpoint(), dir, durability, producerId);
                reopened.add("after-id-move", eventTime + 4);
                LogProducerResult probe = assertResultAndBody(server, reopened.results, "after-id-move", 200);
                assertEquals(d.getEndId() + 1, probe.getStartId());
                awaitCheckpoint(checkpoint, probe.getEndId(), 0);
                reopened.stop();
                assertEquals("only the pending events and the new probe may be sent after takeover",
                        7, server.requests.get());
                assertNull(server.requestBodies.poll());
                assertNull(results.poll());
                assertNull(reopened.results.poll());
                android.util.Log.i("RecoveryProcessFixture", "producerId " + durability + " oldPid=" + oldPid
                        + " takeoverPid=" + android.os.Process.myPid() + " reopenPid=" + reopened.pid()
                        + " leaseAgeAtOpenMs=" + leaseAgeAtOpen + " replay=B/" + recovered.get("B").getStartId()
                        + ",D/" + recovered.get("D").getStartId() + " durableAck=" + d.getEndId()
                        + " reopenedAck=" + probe.getEndId() + " requests=" + server.requests.get());
                server.assertHealthy();
            } finally {
                if (reopened != null) reopened.close();
                if (migrated != null) stop(migrated);
                if (oldOwner != null) oldOwner.close();
                deleteTestDir(dir);
                File bindings = new File(application.getFilesDir(), "com.volcengine.tls/producer-bindings");
                for (String suffix : new String[]{".path", ".lock", ".path.tmp"}) {
                    File file = new File(bindings, producerId + suffix);
                    if (file.exists()) assertTrue(file.delete());
                }
            }
        }
    }

    private static void assertProducerIdBusy(Application application, LogProducerConfig config) throws Exception {
        try {
            LogProducerClient unexpectedlyOpened = openProducer(application, config, null);
            stop(unexpectedlyOpened);
            fail("a live owner must keep the WAL lock");
        } catch (LogProducerInitializationException failure) {
            assertEquals(LogProducerInitializationException.Reason.WAL_LOCK_BUSY, failure.getReason());
            assertTrue(failure.isRetryable());
        }
    }

    private static LogProducerClient openProducer(Application application, LogProducerConfig config,
            LogProducerCallback callback) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<LogProducerClient> ready = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        LogProducerClient.open(application, config, callback, (client, error) -> {
            ready.set(client);
            failure.set(error);
            completed.countDown();
        });
        assertTrue("producer open timed out", completed.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw failure.get();
        assertNotNull(ready.get());
        return ready.get();
    }

    public void testMemoryProducerCanSendWithoutPersistentDirectory() throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) -> 200)) {
            BlockingQueue<LogProducerResult> results = new LinkedBlockingQueue<>();
            LogProducerClient client = new LogProducerClient(
                    config(server, null).setCompressType(LogProducerConfig.CompressType.NONE), results::add);
            try {
                client.addLog(event("memory"), 1);
                assertResultAndBody(server, results, "memory", 200);
                stop(client);
                client = null;
                assertEquals(1, server.requests.get());
                assertNull("unexpected memory request", server.requestBodies.poll());
                assertNull("unexpected memory callback", results.poll());
                server.assertHealthy();
            } finally {
                if (client != null) stop(client);
            }
        }
    }

    private void assertOwnerKeepsDirectoryAfterLeaseExpires(
            LogProducerConfig.PersistentDurability durability) throws Exception {
        AtomicInteger pendingRequests = new AtomicInteger();
        try (TestServer server = new TestServer((attempt, headers, body) ->
                "pending".equals(singleEventId(body)) && pendingRequests.getAndIncrement() == 0 ? 400 : 200)) {
            File dir = testDir();
            RecoveryProcessFixture owner = null;
            LogProducerClient contender = null;
            try {
                owner = new RecoveryProcessFixture(server.endpoint(), dir, durability);
                String ownerProcessName = owner.processName();
                File actualDir = new File(ProcessUtil.rewritePersistentPath(
                        dir.getAbsolutePath(), ownerProcessName));
                BlockingQueue<LogProducerResult> ownerResults = owner.results;
                owner.add("pending", System.currentTimeMillis());
                LogProducerResult pending = assertResultAndBody(server, ownerResults, "pending", 400);

                // A stopped process cannot refresh the stale fixture or modify the snapshot.
                BlockingQueue<LogProducerResult> contenderResults = new LinkedBlockingQueue<>();
                owner.pause();
                try {
                    expireLeaseHeartbeat(new File(actualDir, "lease"));
                    Map<String, byte[]> before = snapshotFiles(actualDir);
                    contender = new LogProducerClient(
                            recoveryGapConfig(server, dir, durability), contenderResults::add, ownerProcessName, null);
                    LogProducerInitializationException denied = expectNativeCreationFailure(
                            contender, "unaccepted", LogProducerInitializationException.Reason.WAL_LOCK_BUSY);
                    assertTrue(denied.isRetryable());
                    assertFileSnapshotsEqual(before, snapshotFiles(actualDir));
                    android.util.Log.i("RecoveryProcessFixture", "stale lease contender refused pid="
                            + owner.pid() + " unchangedFiles=" + before.keySet());
                } finally {
                    owner.resume();
                }

                // Failed takeover must not disturb the still-live owner.
                owner.add("owner-after", System.currentTimeMillis());
                LogProducerResult ownerAfter = assertResultAndBody(server, ownerResults, "owner-after", 200);
                awaitCheckpoint(new File(actualDir, "checkpoint"), pending.getStartId() - 1, ownerAfter.getStartId());
                owner.kill();
                owner.close();
                owner = null;

                // The failed instance must recover without another SDK call triggering creation.
                assertSameLogIdRange(pending, assertResultAndBody(server, contenderResults, "pending", 200));
                android.util.Log.i("RecoveryProcessFixture", "same contender recovered pending=" + pending.getStartId()
                        + " without resubmitting the rejected event");
                contender.addLog(event("contender-after"), 1);
                assertResultAndBody(server, contenderResults, "contender-after", 200);
                stop(contender);
                contender = null;
                assertEquals("only committed WAL and the new submission may be sent", 4, server.requests.get());
                assertNull("unexpected request after recovery", server.requestBodies.poll());
                assertNull("unexpected owner callback", ownerResults.poll());
                assertNull("unexpected contender callback", contenderResults.poll());
                server.assertHealthy();
            } finally {
                if (contender != null) stop(contender);
                if (owner != null) owner.close();
                deleteTestDir(dir);
            }
        }
    }

    private void assertProcessKillPreservesSparseAcknowledgements(
            LogProducerConfig.PersistentDurability durability) throws Exception {
        final long eventTime = 1700000000123L;
        AtomicInteger bRequests = new AtomicInteger();
        AtomicInteger dRequests = new AtomicInteger();
        BlockingQueue<byte[]> observed = new LinkedBlockingQueue<>();
        try (TestServer server = new TestServer((attempt, headers, body) -> {
            observed.add(body);
            String id = singleEventId(body);
            if ("B".equals(id) && bRequests.getAndIncrement() == 0) return 400;
            if ("D".equals(id) && dRequests.getAndIncrement() == 0) return 400;
            return 200;
        })) {
            File dir = testDir();
            RecoveryProcessFixture first = null;
            RecoveryProcessFixture second = null;
            try {
                first = new RecoveryProcessFixture(server.endpoint(), dir, durability);
                String processName = first.processName();
                File actualDir = new File(ProcessUtil.rewritePersistentPath(dir.getAbsolutePath(), processName));
                first.add("A", eventTime);
                LogProducerResult a = assertResultAndBody(server, first.results, "A", 200);
                assertNotNull(observed.poll(15, TimeUnit.SECONDS));
                first.add("B", eventTime + 1);
                LogProducerResult b = assertResultAndBody(server, first.results, "B", 400);
                byte[] bBody = observed.poll(15, TimeUnit.SECONDS);
                assertNotNull(bBody);
                first.add("C", eventTime + 2);
                LogProducerResult c = assertResultAndBody(server, first.results, "C", 200);
                assertNotNull(observed.poll(15, TimeUnit.SECONDS));

                // Wait for the idle checkpoint to contain both the prefix and sparse success.
                awaitCheckpoint(new File(actualDir, "checkpoint"), a.getEndId(), c.getStartId());
                android.util.Log.i("RecoveryProcessFixture", "checkpoint checksum verified prefix="
                        + a.getEndId() + " sparse=" + c.getStartId());
                // This append takes the same persistence guard after the checkpoint save,
                // and remains pending. No producer flush or destroy is called before kill.
                first.add("D", eventTime + 3);
                LogProducerResult d = assertResultAndBody(server, first.results, "D", 400);
                byte[] dBody = observed.poll(15, TimeUnit.SECONDS);
                assertNotNull(dBody);
                assertTrue(a.hasLogIdRange() && b.hasLogIdRange()
                        && c.hasLogIdRange() && d.hasLogIdRange());
                assertTrue(a.getEndId() < b.getStartId() && b.getEndId() < c.getStartId()
                        && c.getEndId() < d.getStartId());
                assertEquals(eventTime + 1, singleLogTime(bBody));
                assertEquals(eventTime + 3, singleLogTime(dBody));
                assertEquals(4, server.requests.get());
                int killedPid = first.pid();
                android.util.Log.i("RecoveryProcessFixture", "before kill pid=" + killedPid
                        + " A=" + a.getStartId() + "/200 B=" + b.getStartId() + "/400 C="
                        + c.getStartId() + "/200 D=" + d.getStartId() + "/400");
                long killStarted = SystemClock.elapsedRealtime();
                first.kill();
                first.close();
                first = null;

                // Read the original lease left by the crashed process, without advancing its heartbeat.
                byte[] lease = readFile(new File(actualDir, "lease"));
                int checksumOffset = leaseChecksumOffset(lease);
                long heartbeat = ByteBuffer.wrap(lease).order(ByteOrder.nativeOrder())
                        .getLong(checksumOffset - 16);
                long leaseAge = System.currentTimeMillis() - heartbeat;
                assertTrue("expected a fresh lease, age=" + leaseAge, leaseAge >= 0 && leaseAge < 60000);
                second = new RecoveryProcessFixture(server.endpoint(), dir, durability);
                assertTrue("restart must use a new process", killedPid != second.pid());
                assertEquals("restart must retain the Android process identity", processName, second.processName());
                second.open();
                long openedInMs = SystemClock.elapsedRealtime() - killStarted;
                assertTrue("open waited for lease expiry", System.currentTimeMillis() - heartbeat < 60000);
                Map<String, LogProducerResult> recovered =
                        assertSuccessfulResultsAndBodies(server, second.results, "B", "D");
                assertSameLogIdRange(b, recovered.get("B"));
                assertSameLogIdRange(d, recovered.get("D"));
                Map<String, byte[]> replayBodies = new LinkedHashMap<>();
                for (int i = 0; i < 2; i++) {
                    byte[] body = observed.poll(15, TimeUnit.SECONDS);
                    assertNotNull("replay body missing", body);
                    assertNull(replayBodies.put(singleEventId(body), body));
                }
                assertTrue("B payload or timestamp changed during replay",
                        Arrays.equals(bBody, replayBodies.get("B")));
                assertTrue("D payload or timestamp changed during replay",
                        Arrays.equals(dBody, replayBodies.get("D")));
                assertEquals(eventTime + 1, singleLogTime(replayBodies.get("B")));
                assertEquals(eventTime + 3, singleLogTime(replayBodies.get("D")));

                second.add("barrier", eventTime + 4);
                LogProducerResult barrier = assertResultAndBody(server, second.results, "barrier", 200);
                assertEquals(d.getEndId() + 1, barrier.getStartId());
                awaitCheckpoint(new File(actualDir, "checkpoint"), barrier.getEndId(), 0);
                second.stop();
                assertEquals("A and C must not be resent; B and D must not be lost", 7, server.requests.get());
                assertNull("unexpected replay", server.requestBodies.poll());
                assertNull("unexpected callback", second.results.poll());
                android.util.Log.i("RecoveryProcessFixture", "restarted pid=" + second.pid()
                        + " replay B=" + recovered.get("B").getStartId() + " time=" + (eventTime + 1)
                        + " D=" + recovered.get("D").getStartId() + " time=" + (eventTime + 3)
                        + " requests=" + server.requests.get() + " oldLeaseAgeMs=" + leaseAge
                        + " killToOpenMs=" + openedInMs + " durableAck=" + barrier.getEndId());
                server.assertHealthy();
            } finally {
                if (second != null) second.close();
                if (first != null) first.close();
                deleteTestDir(dir);
            }
        }
    }

    public void testWalOpenFailureReportsNonRetryableReason() throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) -> 200)) {
            File path = testDir();
            assertTrue(path.createNewFile());
            LogProducerClient client = new LogProducerClient(
                    recoveryGapConfig(server, path, LogProducerConfig.PersistentDurability.SYNC_WAL));
            try {
                LogProducerInitializationException failure = expectNativeCreationFailure(
                        client, "unaccepted", LogProducerInitializationException.Reason.WAL_OPEN_FAILED);
                assertFalse(failure.isRetryable());
                assertTrue(path.delete());
                assertSame(failure, expectNativeCreationFailure(
                        client, "still-unaccepted", LogProducerInitializationException.Reason.WAL_OPEN_FAILED));
                assertEquals(0, server.requests.get());
            } finally {
                stop(client);
                deleteTestDir(path);
            }
            server.assertHealthy();
        }
    }

    private static void awaitCheckpoint(File checkpoint, long prefixId, long sparseId) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            byte[] bytes = readFile(checkpoint);
            if (sparseId == 0 && bytes.length == 48) {
                ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                if (data.getInt(0) == 0x54435031 && data.getInt(4) == 1 && data.getLong(8) == prefixId
                        && data.getInt(44) == leaseChecksum(bytes, 44)) {
                    return;
                }
            }
            if (bytes.length == 84) {
                ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                CRC32 crc = new CRC32();
                crc.update(bytes, 0, 64);
                crc.update(bytes, 68, 16);
                if (data.getInt(0) == 0x54435031 && data.getInt(4) == 1
                        && data.getLong(8) == prefixId
                        && data.getInt(44) == leaseChecksum(bytes, 44)
                        && data.order(ByteOrder.LITTLE_ENDIAN).getInt(48) == 0x324b4341
                        && data.getInt(52) == 1 && data.getLong(56) == 1
                        && data.getInt(64) == (int) crc.getValue()
                        && data.getLong(68) == sparseId && data.getLong(76) == sparseId) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("idle checkpoint did not persist the expected acknowledgements");
    }

    private static long singleLogTime(byte[] body) throws IOException {
        List<byte[]> groups = protobufBytes(body, 1);
        if (groups.size() != 1) throw new IOException("expected one log group");
        List<byte[]> logs = protobufBytes(groups.get(0), 1);
        if (logs.size() != 1) throw new IOException("expected one log");
        ByteArrayInputStream input = new ByteArrayInputStream(logs.get(0));
        if (readVarint(input) != 8) throw new IOException("expected log time as first field");
        return readVarint(input);
    }

    private static LogProducerInitializationException expectNativeCreationFailure(
            LogProducerClient client, String eventId, LogProducerInitializationException.Reason reason) {
        try {
            client.addLog(event(eventId), 1);
        } catch (LogProducerInitializationException expected) {
            assertEquals(reason, expected.getReason());
            return expected;
        }
        throw new AssertionError("expected native creation to fail with " + reason);
    }

    // The test fixture writes a valid stale heartbeat while preserving the owner and fencing token.
    private static void expireLeaseHeartbeat(File lease) throws IOException {
        byte[] bytes = readFile(lease);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
        int checksumOffset = leaseChecksumOffset(bytes);
        int heartbeatOffset = checksumOffset - 16;
        assertTrue("lease heartbeat is invalid", buffer.getLong(heartbeatOffset) > 60000);
        assertTrue("lease fencing token is invalid", buffer.getLong(checksumOffset - 8) > 0);
        buffer.putLong(heartbeatOffset, 1);
        buffer.putInt(checksumOffset, leaseChecksum(bytes, checksumOffset));

        File temp = new File(lease.getParentFile(), "lease.expired-test");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
            }
            assertTrue("cannot replace the lease fixture", temp.renameTo(lease));
        } finally {
            if (temp.exists()) assertTrue(temp.delete());
        }
        assertTrue("lease fixture write did not persist", Arrays.equals(bytes, readFile(lease)));
    }

    private static int leaseChecksumOffset(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
        assertTrue("lease file is too short", bytes.length >= 24);
        assertEquals("unexpected lease magic", 0x544c5331, buffer.getInt(0));
        assertEquals("unexpected lease version", 1, buffer.getInt(4));

        // The C lease struct ends with heartbeat and fencing_token (8 bytes each),
        // then checksum; native struct alignment may add four trailing bytes.
        int checksumOffset = -1;
        for (int trailing : new int[] {0, 4}) {
            int candidate = bytes.length - 4 - trailing;
            if (candidate >= 24 && buffer.getInt(candidate) == leaseChecksum(bytes, candidate)) {
                assertEquals("ambiguous lease checksum layout", -1, checksumOffset);
                checksumOffset = candidate;
            }
        }
        assertTrue("unknown lease checksum layout", checksumOffset >= 24);
        return checksumOffset;
    }

    private static int leaseChecksum(byte[] bytes, int length) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < length; i++) {
            hash = (hash ^ (bytes[i] & 0xff)) * 0x01000193;
        }
        return hash;
    }

    private static byte[] readFile(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length > Integer.MAX_VALUE) throw new IOException("test file is too large");
            byte[] bytes = new byte[(int) length];
            input.readFully(bytes);
            return bytes;
        }
    }

    private static Map<String, byte[]> snapshotFiles(File directory) throws IOException {
        String[] names = directory.list();
        assertNotNull("persistent directory missing", names);
        Arrays.sort(names);
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : names) {
            File file = new File(directory, name);
            assertTrue("unexpected non-file in WAL directory: " + name, file.isFile());
            files.put(name, readFile(file));
        }
        assertTrue("lease missing", files.containsKey("lease"));
        assertTrue("checkpoint missing", files.containsKey("checkpoint"));
        assertTrue("manifest missing", files.containsKey("manifest"));
        assertTrue("WAL segment missing", files.containsKey("seg-000001.log"));
        return files;
    }

    private static void assertFileSnapshotsEqual(Map<String, byte[]> before, Map<String, byte[]> after) {
        assertEquals("WAL directory entries changed", before.keySet(), after.keySet());
        for (Map.Entry<String, byte[]> entry : before.entrySet()) {
            assertTrue("failed open changed " + entry.getKey(),
                    Arrays.equals(entry.getValue(), after.get(entry.getKey())));
        }
    }

    private void assertNewFailureAfterRestartSurvives(
            LogProducerConfig.PersistentDurability durability, boolean sparseSuccess) throws Exception {
        AtomicInteger bRequests = new AtomicInteger();
        AtomicInteger dRequests = new AtomicInteger();
        try (TestServer server = new TestServer((attempt, headers, body) -> {
            String id = singleEventId(body);
            if (sparseSuccess && "B".equals(id) && bRequests.getAndIncrement() == 0) return 400;
            if ("D".equals(id) && dRequests.getAndIncrement() == 0) return 400;
            return 200;
        })) {
            File dir = testDir();
            LogProducerClient first = null;
            LogProducerClient second = null;
            LogProducerClient third = null;
            try {
                BlockingQueue<LogProducerResult> firstResults = new LinkedBlockingQueue<>();
                first = new LogProducerClient(recoveryGapConfig(server, dir, durability), firstResults::add);
                first.addLog(event("A"), 1);
                LogProducerResult highestOldId = assertResultAndBody(server, firstResults, "A", 200);
                LogProducerResult failedB = null;
                if (sparseSuccess) {
                    first.addLog(event("B"), 1);
                    failedB = assertResultAndBody(server, firstResults, "B", 400);
                    first.addLog(event("C"), 1);
                    highestOldId = assertResultAndBody(server, firstResults, "C", 200);
                }
                stop(first);
                first = null;

                BlockingQueue<LogProducerResult> secondResults = new LinkedBlockingQueue<>();
                second = new LogProducerClient(recoveryGapConfig(server, dir, durability), secondResults::add);
                second.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                if (sparseSuccess) {
                    assertSameLogIdRange(failedB, assertResultAndBody(server, secondResults, "B", 200));
                }
                second.addLog(event("D"), 1);
                LogProducerResult failedNewRecord = assertResultAndBody(server, secondResults, "D", 400);
                stop(second);
                second = null;

                BlockingQueue<LogProducerResult> thirdResults = new LinkedBlockingQueue<>();
                third = new LogProducerClient(recoveryGapConfig(server, dir, durability), thirdResults::add);
                third.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                // Recovery and a new record can reach the sender in either order.
                third.addLog(event("id-barrier"), 1);
                Map<String, LogProducerResult> thirdDelivered =
                        assertSuccessfulResultsAndBodies(server, thirdResults, "D", "id-barrier");
                assertTrue("new record must not reuse an earlier acknowledged ID",
                        highestOldId.hasLogIdRange() && failedNewRecord.hasLogIdRange()
                                && failedNewRecord.getStartId() > highestOldId.getEndId());
                assertSameLogIdRange(failedNewRecord, thirdDelivered.get("D"));
                assertTrue("barrier must receive a new ID",
                        thirdDelivered.get("id-barrier").getStartId() > failedNewRecord.getEndId());
                stop(third);
                third = null;
                assertEquals("only D and the new barrier may follow recovery", sparseSuccess ? 7 : 4,
                        server.requests.get());
                assertNull("unexpected request after third close", server.requestBodies.poll());
                assertNull("unexpected callback after third close", thirdResults.poll());
                server.assertHealthy();
            } finally {
                if (third != null) stop(third);
                if (second != null) stop(second);
                if (first != null) stop(first);
                deleteTestDir(dir);
            }
        }
    }

    private void assertRecoveryDoesNotResendSuccessAfterFailure(
            LogProducerConfig.PersistentDurability durability) throws Exception {
        AtomicInteger bRequests = new AtomicInteger();
        try (TestServer server = new TestServer((attempt, headers, body) ->
                "B".equals(singleEventId(body)) && bRequests.getAndIncrement() == 0 ? 400 : 200)) {
            File dir = testDir();
            LogProducerClient first = null;
            LogProducerClient second = null;
            LogProducerClient third = null;
            try {
                BlockingQueue<LogProducerResult> firstResults = new LinkedBlockingQueue<>();
                first = new LogProducerClient(recoveryGapConfig(server, dir, durability), firstResults::add);

                first.addLog(event("A"), 1);
                assertResultAndBody(server, firstResults, "A", 200);
                first.addLog(event("B"), 1);
                LogProducerResult failedB = assertResultAndBody(server, firstResults, "B", 400);
                first.addLog(event("C"), 1);
                LogProducerResult successC = assertResultAndBody(server, firstResults, "C", 200);
                stop(first);
                first = null;
                assertEquals(3, server.requests.get());

                BlockingQueue<LogProducerResult> secondResults = new LinkedBlockingQueue<>();
                second = new LogProducerClient(recoveryGapConfig(server, dir, durability), secondResults::add);
                second.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                // Both recovered B and the new record must arrive, in either order.
                second.addLog(event("barrier-2"), 1);
                Map<String, LogProducerResult> secondDelivered =
                        assertSuccessfulResultsAndBodies(server, secondResults, "B", "barrier-2");
                assertSameLogIdRange(failedB, secondDelivered.get("B"));
                assertTrue("barrier must receive a new ID",
                        secondDelivered.get("barrier-2").getStartId() > successC.getEndId());
                stop(second);
                second = null;
                assertEquals("C must not be sent a second time", 5, server.requests.get());
                assertNull("unexpected request after second close", server.requestBodies.poll());
                assertNull("unexpected callback after second close", secondResults.poll());

                BlockingQueue<LogProducerResult> thirdResults = new LinkedBlockingQueue<>();
                third = new LogProducerClient(recoveryGapConfig(server, dir, durability), thirdResults::add);
                third.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                // The only request in this generation must be this newly added barrier.
                third.addLog(event("barrier-3"), 1);
                assertResultAndBody(server, thirdResults, "barrier-3", 200);
                stop(third);
                third = null;
                assertEquals("third recovery must not replay any old record", 6, server.requests.get());
                assertNull("unexpected request after third close", server.requestBodies.poll());
                assertNull("unexpected callback after third close", thirdResults.poll());
                server.assertHealthy();
            } finally {
                if (third != null) stop(third);
                if (second != null) stop(second);
                if (first != null) stop(first);
                deleteTestDir(dir);
            }
        }
    }

    private static LogProducerConfig recoveryGapConfig(
            TestServer server, File dir, LogProducerConfig.PersistentDurability durability) {
        return recoveryGapConfig(server.endpoint(), dir, durability);
    }

    static LogProducerConfig recoveryGapConfig(
            String endpoint, File dir, LogProducerConfig.PersistentDurability durability) {
        return config(endpoint, dir)
                .setMaxBufferLimit(8 * 1024 * 1024)
                .setPersistentMaxBytes(10 * 1024 * 1024)
                .setPersistentMaxRecords(65536).setPersistentMaxSegments(10)
                .setPersistentMaxLogCount(65536).setPersistentMaxFileCount(10)
                .setPersistentMaxFileSize(1024 * 1024)
                .setPersistentDurability(durability)
                .setPersistentForceFlush(durability == LogProducerConfig.PersistentDurability.SYNC_WAL)
                .setCompressType(LogProducerConfig.CompressType.NONE)
                .setPacketLogCount(1);
    }

    static Log event(String id) {
        return new Log().putContent("event_id", id).putContent("payload", "payload-" + id);
    }

    private static String singleEventId(byte[] body) throws IOException {
        List<Map<String, String>> logs = decodeLogs(body);
        if (logs.size() != 1 || logs.get(0).get("event_id") == null) {
            throw new IOException("expected exactly one identified event");
        }
        return logs.get(0).get("event_id");
    }

    private static Map<String, LogProducerResult> assertSuccessfulResultsAndBodies(
            TestServer server, BlockingQueue<LogProducerResult> results, String... ids) throws Exception {
        Map<String, LogProducerResult> delivered = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) {
            LogProducerResult result = results.poll(15, TimeUnit.SECONDS);
            assertNotNull("completion timed out", result);
            assertTrue(result.getFailureSummary(), result.isSuccess());
            assertEquals(200, result.getHttpCode());
            assertTrue("callback must identify the sent log", result.hasLogIdRange());
            assertEquals(result.getStartId(), result.getEndId());
            byte[] body = server.requestBodies.poll(15, TimeUnit.SECONDS);
            assertNotNull("request body missing", body);
            String id = singleEventId(body);
            assertTrue("unexpected event: " + id, Arrays.asList(ids).contains(id));
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("event_id", id);
            expected.put("payload", "payload-" + id);
            assertEquals("unexpected decoded request body", Collections.singletonList(expected), decodeLogs(body));
            assertNull("event delivered more than once: " + id, delivered.put(id, result));
        }
        return delivered;
    }

    private static void assertSameLogIdRange(LogProducerResult expected, LogProducerResult actual) {
        assertTrue(expected.hasLogIdRange());
        assertTrue(actual.hasLogIdRange());
        assertEquals(expected.getStartId(), actual.getStartId());
        assertEquals(expected.getEndId(), actual.getEndId());
    }

    private static LogProducerResult assertResultAndBody(TestServer server, BlockingQueue<LogProducerResult> results,
            String id, int expectedStatus) throws Exception {
        LogProducerResult result = results.poll(15, TimeUnit.SECONDS);
        assertNotNull("completion timed out for " + id, result);
        if (expectedStatus == 200) {
            assertTrue(result.getFailureSummary(), result.isSuccess());
        } else {
            assertFalse(result.getFailureSummary(), result.isSuccess());
            assertEquals(expectedStatus, result.getHttpCode());
            assertFalse("ordinary HTTP 400 must not be retried", result.isRetryable());
        }
        byte[] body = server.requestBodies.poll(15, TimeUnit.SECONDS);
        assertNotNull("request body missing for " + id, body);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("event_id", id);
        expected.put("payload", "payload-" + id);
        assertEquals("unexpected decoded request body", Collections.singletonList(expected), decodeLogs(body));
        return result;
    }

    // Decode the uncompressed LogGroupList protobuf sent over the actual JNI HTTP bridge.
    private static List<Map<String, String>> decodeLogs(byte[] body) throws IOException {
        List<Map<String, String>> logs = new ArrayList<>();
        for (byte[] group : protobufBytes(body, 1)) {
            for (byte[] log : protobufBytes(group, 1)) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (byte[] content : protobufBytes(log, 2)) {
                    List<byte[]> keys = protobufBytes(content, 1);
                    List<byte[]> values = protobufBytes(content, 2);
                    if (keys.size() != 1 || values.size() != 1) {
                        throw new IOException("invalid LogContent in request body");
                    }
                    String key = new String(keys.get(0), "UTF-8");
                    String value = new String(values.get(0), "UTF-8");
                    if (fields.put(key, value) != null) {
                        throw new IOException("duplicate LogContent key in request body");
                    }
                }
                logs.add(fields);
            }
        }
        return logs;
    }

    private static List<byte[]> protobufBytes(byte[] message, int wantedField) throws IOException {
        List<byte[]> values = new ArrayList<>();
        ByteArrayInputStream input = new ByteArrayInputStream(message);
        while (input.available() > 0) {
            long tag = readVarint(input);
            int wireType = (int) (tag & 7);
            if (wireType == 0) {
                readVarint(input);
            } else if (wireType == 2) {
                long length = readVarint(input);
                if (length < 0 || length > input.available()) {
                    throw new IOException("invalid protobuf field length");
                }
                byte[] value = new byte[(int) length];
                if (value.length > 0 && input.read(value, 0, value.length) != value.length) {
                    throw new IOException("truncated protobuf field");
                }
                if ((tag >>> 3) == wantedField) values.add(value);
            } else if (wireType == 1 || wireType == 5) {
                int length = wireType == 1 ? 8 : 4;
                if (input.skip(length) != length) throw new IOException("truncated protobuf field");
            } else {
                throw new IOException("unsupported protobuf wire type: " + wireType);
            }
        }
        return values;
    }

    private static long readVarint(ByteArrayInputStream input) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int next = input.read();
            if (next < 0) throw new IOException("truncated protobuf varint");
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IOException("protobuf varint is too long");
    }

    public void testMemoryBudgetExhaustionStillReportsFailure() throws Exception {
        try (TestServer server = new TestServer((attempt, headers, body) -> 503)) {
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(
                    config(server, null).setPersistent(false), completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue(completion.done.await(10, TimeUnit.SECONDS));
                assertFalse(completion.result.get().isSuccess());
                assertTrue(completion.result.get().isRetryable());
            } finally {
                stop(client);
            }
            server.assertHealthy();
            assertEquals(1, server.requests.get());
            assertEquals(1, completion.calls.get());
        }
    }

    public void testSocketTimeoutIsRetryableThroughJni() throws Exception {
        // Accept no requests: TCP connects, but the HTTP read must time out in Java.
        try (ServerSocket stalled = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            Completion completion = new Completion();
            LogProducerConfig config = new LogProducerConfig()
                    .setEndpoint("http://127.0.0.1:" + stalled.getLocalPort())
                    .setRegion("cn-beijing").setTopicId("recovery-test")
                    .setAccessKeyId("test-ak").setAccessKeySecret("test-sk")
                    .setCallbackFromSenderThread(true).setRetryMaxAttempts(1)
                    .setConnectTimeoutMs(1000).setRequestTimeoutMs(500)
                    .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
            LogProducerClient client = new LogProducerClient(config, completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue(completion.done.await(10, TimeUnit.SECONDS));
                assertFalse(completion.result.get().isSuccess());
                assertEquals("JavaHttpBridgeError", completion.result.get().getErrorCode());
                assertTrue("cached JNI classifier must return true for transient IO",
                        completion.result.get().isRetryable());
            } finally {
                stop(client);
            }
            assertEquals(1, completion.calls.get());
        }
    }

    public void testMalformedUrlIsNonRetryableThroughJni() throws Exception {
        Completion completion = new Completion();
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("http://[invalid")
                .setRegion("cn-beijing").setTopicId("recovery-test")
                .setAccessKeyId("test-ak").setAccessKeySecret("test-sk")
                .setCallbackFromSenderThread(true).setRetryMaxAttempts(3)
                .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
        LogProducerClient client = new LogProducerClient(config, completion::accept);
        try {
            client.addLog(log(), 1);
            assertTrue(completion.done.await(10, TimeUnit.SECONDS));
            assertFalse(completion.result.get().isSuccess());
            assertEquals("JavaHttpBridgeError", completion.result.get().getErrorCode());
            assertFalse("JNI must honor terminal transport errors: "
                    + completion.result.get().getFailureSummary(), completion.result.get().isRetryable());
        } finally {
            stop(client);
        }
        assertEquals(1, completion.calls.get());
    }

    private static LogProducerConfig config(TestServer server, File dir) {
        return config(server.endpoint(), dir);
    }

    private static LogProducerConfig config(String endpoint, File dir) {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(endpoint).setRegion("cn-beijing").setTopicId("recovery-test")
                .setAccessKeyId("test-ak").setAccessKeySecret("test-sk").setSecurityToken("old-test-token")
                .setCallbackFromSenderThread(true).setSendThreadCount(1)
                .setRetryMaxAttempts(1).setRetryTotalTimeoutMs(1000)
                .setRetryInitialIntervalMs(100).setRetryMaxIntervalMs(1000)
                .setPacketTimeoutMs(100).setConnectTimeoutMs(1000).setRequestTimeoutMs(1000)
                .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
        if (dir != null) {
            config.setPersistent(true).setPersistentFilePath(dir.getAbsolutePath())
                    .setPersistentMaxFileCount(4).setPersistentMaxFileSize(65536)
                    .setPersistentMaxLogCount(64);
        }
        return config;
    }

    private static Log log() {
        return new Log().putContent("case", "core-032-recovery");
    }

    private static File testDir() {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "core-032-test-" + System.nanoTime());
    }

    private static void stop(LogProducerClient client) {
        client.destroyLogProducer();
        assertTrue("destroy must not wait for cross-cycle backoff", client.awaitDestroy(5000));
    }

    private static void deleteTestDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteTestDir(file);
                } else {
                    assertTrue(file.delete());
                }
            }
        }
        if (dir.exists()) {
            assertTrue(dir.delete());
        }
    }

    private static final class Completion {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<LogProducerResult> result = new AtomicReference<>();

        void accept(LogProducerResult value) {
            calls.incrementAndGet();
            result.set(value);
            done.countDown();
        }

        void assertSuccess() throws InterruptedException {
            assertTrue("terminal success timed out", done.await(15, TimeUnit.SECONDS));
            assertTrue(result.get().getFailureSummary(), result.get().isSuccess());
            assertTrue(result.get().hasLogIdRange());
        }
    }

    private interface Responder {
        int status(int attempt, String headers, byte[] body) throws IOException;
    }

    private static final class TestServer implements AutoCloseable {
        final AtomicInteger requests = new AtomicInteger();
        final BlockingQueue<byte[]> requestBodies = new LinkedBlockingQueue<>();
        final CountDownLatch firstFailure = new CountDownLatch(1);
        final CountDownLatch repeatedFailure = new CountDownLatch(3);
        final AtomicBoolean bodyChanged = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final ServerSocket server;
        final Thread worker;
        volatile boolean closed;
        byte[] firstBody;

        TestServer(Responder responder) throws IOException {
            this(responder, null);
        }

        TestServer(Responder responder, String errorCode) throws IOException {
            byte[] errorBody = (errorCode == null ? "" : "{\"ErrorCode\":\"" + errorCode + "\"}")
                    .getBytes("US-ASCII");
            byte[] emptyBody = new byte[0];
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (!closed) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5000);
                        InputStream input = socket.getInputStream();
                        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                        int suffix = 0;
                        while (suffix != 0x0d0a0d0a) {
                            int value = input.read();
                            if (value < 0 || headerBytes.size() >= 32768) {
                                throw new IOException("invalid loopback request headers");
                            }
                            headerBytes.write(value);
                            suffix = (suffix << 8) | value;
                        }
                        String headers = new String(headerBytes.toByteArray(), "US-ASCII")
                                .toLowerCase(Locale.ROOT);
                        int length = -1;
                        for (String line : headers.split("\r\n")) {
                            if (line.startsWith("content-length:")) {
                                length = Integer.parseInt(line.substring(15).trim());
                            }
                        }
                        if (length < 0 || length > 1024 * 1024) {
                            throw new IOException("invalid loopback request length");
                        }
                        byte[] body = new byte[length];
                        int offset = 0;
                        while (offset < length) {
                            int count = input.read(body, offset, length - offset);
                            if (count < 0) {
                                throw new IOException("truncated loopback body");
                            }
                            offset += count;
                        }
                        if (firstBody == null) {
                            firstBody = body;
                        } else if (!Arrays.equals(firstBody, body)) {
                            bodyChanged.set(true);
                        }
                        requestBodies.add(body);
                        int status = responder.status(requests.incrementAndGet(), headers, body);
                        byte[] responseBody = status >= 400 ? errorBody : emptyBody;
                        byte[] response = ("HTTP/1.1 " + status + " Test\r\n"
                                + "Content-Length: " + responseBody.length + "\r\nX-Tls-Requestid: loopback-test\r\n"
                                + "Connection: close\r\n\r\n").getBytes("US-ASCII");
                        socket.getOutputStream().write(response);
                        socket.getOutputStream().write(responseBody);
                        socket.getOutputStream().flush();
                        if (status >= 400) {
                            firstFailure.countDown();
                            repeatedFailure.countDown();
                        }
                    } catch (Throwable failure) {
                        if (!closed) {
                            error.compareAndSet(null, failure);
                        }
                        return;
                    }
                }
            }, "producer-loopback-test");
            worker.setDaemon(true);
            worker.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void assertHealthy() {
            assertNull("loopback server failed: " + error.get(), error.get());
        }

        @Override
        public void close() throws Exception {
            closed = true;
            server.close();
            worker.join(6000);
            assertFalse("loopback server did not stop", worker.isAlive());
        }
    }
}
