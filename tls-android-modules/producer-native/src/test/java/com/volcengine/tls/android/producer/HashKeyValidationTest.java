package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.ConfigSnapshot;
import com.volcengine.tls.android.producer.internal.HashKeyValidator;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HashKeyValidationTest {
    @Test
    public void validator_acceptsNullEmptyAndLowerEndpointBoundaries() {
        assertTrue(HashKeyValidator.isValid(null));
        assertTrue(HashKeyValidator.isValid(""));
        assertTrue(HashKeyValidator.isValid(repeated('0', 32)));
        assertTrue(HashKeyValidator.isValid(repeated('f', 31) + "e"));
        assertFalse(HashKeyValidator.isValid(repeated('f', 32)));
    }

    @Test
    public void validator_rejectsInvalidFormat() {
        String[] invalidValues = {
                repeated('0', 31),
                repeated('0', 33),
                repeated('A', 32),
                repeated('g', 32),
                repeated('0', 31) + " ",
                repeated('f', 32)
        };

        for (String invalidValue : invalidValues) {
            assertFalse(invalidValue, HashKeyValidator.isValid(invalidValue));
        }
    }

    @Test
    public void setter_rejectsInvalidValueAndPreservesPreviousValue() {
        String validValue = repeated('0', 32);
        LogProducerConfig config = new LogProducerConfig().setHashKey(validValue);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> config.setHashKey(repeated('f', 32)));

        assertTrue(error.getMessage().contains("hashKey"));
        assertEquals(validValue, config.getHashKey());
    }

    @Test
    public void createValidation_rejectsInvalidHashKeyInConfigAndSnapshot() throws Exception {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("endpoint")
                .setRegion("region")
                .setTopicId("topic");
        setHashKeyWithoutSetter(config, repeated('f', 32));

        assertFalse(config.isValid());
        assertThrows(IllegalArgumentException.class, () -> config.validateForCreate("demo"));

        ConfigSnapshot snapshot = new ConfigSnapshot(config, "demo");
        assertThrows(IllegalArgumentException.class, snapshot::validateForCreate);
    }

    @Test
    public void snapshotValidation_acceptsNullEmptyAndValidHashKeys() {
        for (String hashKey : Arrays.asList(null, "", repeated('0', 32), repeated('f', 31) + "e")) {
            ConfigSnapshot snapshot = new ConfigSnapshot(baseConfig().setHashKey(hashKey), "demo");
            snapshot.validateForCreate();
            assertEquals(hashKey, snapshot.getHashKey());
        }
    }

    private static LogProducerConfig baseConfig() {
        return new LogProducerConfig()
                .setEndpoint("endpoint")
                .setRegion("region")
                .setTopicId("topic");
    }

    private static void setHashKeyWithoutSetter(LogProducerConfig config, String hashKey)
            throws ReflectiveOperationException {
        Field field = LogProducerConfig.class.getDeclaredField("hashKey");
        field.setAccessible(true);
        field.set(config, hashKey);
    }

    private static String repeated(char value, int count) {
        char[] values = new char[count];
        Arrays.fill(values, value);
        return new String(values);
    }
}
