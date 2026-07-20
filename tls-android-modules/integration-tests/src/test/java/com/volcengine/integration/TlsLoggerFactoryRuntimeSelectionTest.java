package com.volcengine.integration;

import com.volcengine.util.TlsLogger;
import com.volcengine.util.TlsLoggerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TlsLoggerFactoryRuntimeSelectionTest {
    @Test
    void pureJavaDefaultsToJul() {
        TlsLogger logger = TlsLoggerFactory.getLogger("MyLogger");
        assertTrue(logger.getClass().getName().endsWith("TlsLoggerFactory$JulTlsLogger"));
        assertDoesNotThrow(() -> logger.info("hello"));
    }
}
