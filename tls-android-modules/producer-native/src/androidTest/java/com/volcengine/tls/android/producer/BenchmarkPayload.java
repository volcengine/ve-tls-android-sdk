package com.volcengine.tls.android.producer;

final class BenchmarkPayload {
    private static final int UNIQUE_RING_SIZE = 4096;
    private static final int TLS200_FIELD_COUNT = 4;
    private static final int TLS700_FIELD_COUNT = 10;
    private static final int TLS200_REPEAT_CHARS = 38;
    private static final int TLS200_UNIQUE_CHARS = 13;
    private static final int TLS700_REPEAT_CHARS = 58;
    private static final int TLS700_UNIQUE_CHARS = 20;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final String REPEAT_UNIT = "stable-benchmark-payload-";

    private static volatile String[][] tls200Unique;
    private static volatile String[][] tls700Unique;
    private static final String[] TLS200_KEYS = new String[] {
            "Interconnection",
            "LogHub",
            "Search/Analytics",
            "Visualized"
    };
    private static final String[] TLS700_KEYS = new String[] {
            "content_key_1",
            "content_key_2",
            "content_key_3",
            "content_key_4",
            "content_key_5",
            "content_key_6",
            "content_key_7",
            "content_key_8",
            "content_key_9",
            "index"
    };

    private BenchmarkPayload() {
    }

    static void warmUp() {
        ensureUniqueValues();
    }

    static String[][] entries(String profileName, long index) {
        ensureUniqueValues();
        boolean tls700 = "tls700".equals(profileName);
        String[] keys = tls700 ? TLS700_KEYS : TLS200_KEYS;
        String[][] uniqueValues = tls700 ? tls700Unique : tls200Unique;
        int repeatChars = tls700 ? TLS700_REPEAT_CHARS : TLS200_REPEAT_CHARS;
        String[][] entries = new String[keys.length][2];
        int uniqueIndex = (int) Math.floorMod(index, UNIQUE_RING_SIZE);
        for (int i = 0; i < keys.length; i++) {
            entries[i][0] = keys[i];
            entries[i][1] = repeatText(i, repeatChars) + uniqueValues[i][uniqueIndex];
        }
        return entries;
    }

    private static void ensureUniqueValues() {
        if (tls200Unique != null && tls700Unique != null) {
            return;
        }
        synchronized (BenchmarkPayload.class) {
            if (tls200Unique == null) {
                tls200Unique = uniqueTable(TLS200_FIELD_COUNT, TLS200_UNIQUE_CHARS);
            }
            if (tls700Unique == null) {
                tls700Unique = uniqueTable(TLS700_FIELD_COUNT, TLS700_UNIQUE_CHARS);
            }
        }
    }

    private static String[][] uniqueTable(int fields, int chars) {
        String[][] table = new String[fields][UNIQUE_RING_SIZE];
        for (int field = 0; field < fields; field++) {
            for (int index = 0; index < UNIQUE_RING_SIZE; index++) {
                table[field][index] = uniqueText(field, index, chars);
            }
        }
        return table;
    }

    private static String repeatText(int field, int chars) {
        StringBuilder builder = new StringBuilder(chars);
        while (builder.length() < chars) {
            builder.append(REPEAT_UNIT).append(field).append('-');
        }
        return builder.substring(0, chars);
    }

    private static String uniqueText(int field, int index, int chars) {
        long state = mix64((((long) field) << 32) ^ index ^ 0x9E3779B97F4A7C15L);
        StringBuilder builder = new StringBuilder(chars);
        while (builder.length() < chars) {
            state = mix64(state + 0x9E3779B97F4A7C15L);
            long value = state;
            for (int i = 0; i < 10 && builder.length() < chars; i++) {
                int alphabetIndex = (int) Math.floorMod(value, ALPHABET.length);
                builder.append(ALPHABET[alphabetIndex]);
                value /= ALPHABET.length;
            }
        }
        return builder.toString();
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
