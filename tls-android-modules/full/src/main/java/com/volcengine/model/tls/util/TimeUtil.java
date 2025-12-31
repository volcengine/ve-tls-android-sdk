package com.volcengine.model.tls.util;

public class TimeUtil {
  public static long calcDefaultBackOffMs(int retryCounter, int intervalMs, long expectedQuitTimestamp) {
    long remain = expectedQuitTimestamp - System.currentTimeMillis();
    if (remain <= 0) return 0;
    long sleep = (long) Math.min(intervalMs * (1 + Math.max(0, retryCounter)), remain);
    return Math.max(0, sleep);
  }
}

