package com.volcengine.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TlsLoggerFactoryTest {
    @Before
    public void setUp() {
        android.util.Log.reset();
        TlsLoggerFactory.setProvider(null);
    }

    @After
    public void tearDown() {
        android.util.Log.reset();
        TlsLoggerFactory.setProvider(null);
    }

    @Test
    public void defaultAndroidLogger_usesFixedTagAndNamePrefix() {
        TlsLogger logger = TlsLoggerFactory.getLogger("MyLogger");
        logger.info("hello");

        assertEquals("TLS-SDK", android.util.Log.lastTag);
        assertEquals("[MyLogger] hello", android.util.Log.lastMsg);
        assertEquals("i", android.util.Log.lastMethod);
        assertNull(android.util.Log.lastThrowable);
    }

    @Test
    public void errorWithThrowable_usesAndroidThrowableOverload() {
        TlsLogger logger = TlsLoggerFactory.getLogger("MyLogger");
        RuntimeException ex = new RuntimeException("boom");
        logger.error("failed", ex);

        assertEquals("TLS-SDK", android.util.Log.lastTag);
        assertEquals("[MyLogger] failed", android.util.Log.lastMsg);
        assertEquals("et", android.util.Log.lastMethod);
        assertSame(ex, android.util.Log.lastThrowable);
    }

    @Test
    public void providerOverridesDefault_andCachesPerName() {
        AtomicInteger created = new AtomicInteger();
        TlsLoggerProvider provider = name -> {
            created.incrementAndGet();
            return new CapturingLogger(name);
        };
        TlsLoggerFactory.setProvider(provider);

        TlsLogger a1 = TlsLoggerFactory.getLogger("A");
        TlsLogger a2 = TlsLoggerFactory.getLogger("A");
        TlsLogger b1 = TlsLoggerFactory.getLogger("B");

        assertSame(a1, a2);
        assertNotSame(a1, b1);
        assertEquals(2, created.get());
    }

    @Test
    public void providerChange_clearsCache() {
        TlsLoggerProvider provider1 = name -> new CapturingLogger("p1:" + name);
        TlsLoggerProvider provider2 = name -> new CapturingLogger("p2:" + name);

        TlsLoggerFactory.setProvider(provider1);
        TlsLogger a1 = TlsLoggerFactory.getLogger("A");

        TlsLoggerFactory.setProvider(provider2);
        TlsLogger a2 = TlsLoggerFactory.getLogger("A");

        assertNotSame(a1, a2);
        assertTrue(((CapturingLogger) a1).name.startsWith("p1:"));
        assertTrue(((CapturingLogger) a2).name.startsWith("p2:"));
    }

    @Test
    public void providerReturningNull_fallsBackToDefaultAndroid() {
        TlsLoggerFactory.setProvider(name -> null);

        TlsLogger logger = TlsLoggerFactory.getLogger("MyLogger");
        assertTrue(logger.getClass().getName().endsWith("TlsLoggerFactory$AndroidTlsLogger"));

        logger.warn("hi");
        assertEquals("w", android.util.Log.lastMethod);
        assertEquals("[MyLogger] hi", android.util.Log.lastMsg);
    }

    private static final class CapturingLogger implements TlsLogger {
        private final String name;

        private CapturingLogger(String name) {
            this.name = name;
        }

        @Override public void debug(String msg) {}
        @Override public void debug(String format, Object... args) {}
        @Override public void info(String msg) {}
        @Override public void info(String format, Object... args) {}
        @Override public void warn(String msg) {}
        @Override public void warn(String format, Object... args) {}
        @Override public void error(String msg) {}
        @Override public void error(String msg, Throwable t) {}
        @Override public void error(String format, Object... args) {}
    }
}
