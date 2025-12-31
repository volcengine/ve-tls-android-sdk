package com.volcengine.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class HashUtils {
    public static String hashSHA256(byte[] content) throws Exception {
        try { MessageDigest md = MessageDigest.getInstance("SHA-256"); return toHex(md.digest(content)); } catch (Exception e) { throw new Exception("Unable to compute hash while signing request: " + e.getMessage(), e); }
    }
    public static byte[] hmacSHA256(byte[] key, String content) throws Exception {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(content.getBytes()); } catch (Exception e) { throw new Exception("Unable to calculate a request signature: " + e.getMessage(), e); }
    }
    public static String toHex(byte[] bytes) {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
