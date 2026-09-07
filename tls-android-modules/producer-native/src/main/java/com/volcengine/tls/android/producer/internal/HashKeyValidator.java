package com.volcengine.tls.android.producer.internal;

/** Validates the producer hash key using the native C producer contract. */
public final class HashKeyValidator {
    private static final int HASH_KEY_LENGTH = 32;

    private HashKeyValidator() {
    }

    public static boolean isValid(String hashKey) {
        if (hashKey == null || hashKey.length() == 0) {
            return true;
        }
        if (hashKey.length() != HASH_KEY_LENGTH) {
            return false;
        }

        boolean isExclusiveUpperBound = true;
        for (int i = 0; i < HASH_KEY_LENGTH; i++) {
            char value = hashKey.charAt(i);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                return false;
            }
            if (value != 'f') {
                isExclusiveUpperBound = false;
            }
        }
        return !isExclusiveUpperBound;
    }

    public static void requireValid(String hashKey) {
        if (!isValid(hashKey)) {
            throw new IllegalArgumentException(
                    "hashKey must be empty or exactly 32 lowercase hexadecimal characters below the exclusive upper bound");
        }
    }
}
