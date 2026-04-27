package com.volcengine.tls.android.demo;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BenchmarkPayloadTest {
    @Test
    public void payloadChangesPerLogButKeepsCompressionRatioNearFour() {
        BenchmarkPayload.warmUp();

        String[][] first = BenchmarkPayload.entries("tls700", 1);
        String[][] second = BenchmarkPayload.entries("tls700", 2);

        assertNotEquals(first[0][1], second[0][1]);
        double ratio = compressionRatio("tls700", 1024);
        assertTrue("ratio=" + ratio, ratio >= 3.0 && ratio <= 5.5);
    }

    private static double compressionRatio(String profile, int logs) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < logs; i++) {
            for (String[] entry : BenchmarkPayload.entries(profile, i + 1)) {
                body.append(entry[0]).append('=').append(entry[1]).append('\n');
            }
        }
        byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(1);
        deflater.setInput(raw);
        deflater.finish();
        byte[] compressed = new byte[raw.length];
        int compressedBytes = deflater.deflate(compressed);
        deflater.end();
        return raw.length / (double) compressedBytes;
    }
}
