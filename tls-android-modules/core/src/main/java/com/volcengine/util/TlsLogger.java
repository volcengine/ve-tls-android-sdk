package com.volcengine.util;

public interface TlsLogger {
    void debug(String msg);
    void debug(String format, Object... args);
    void info(String msg);
    void info(String format, Object... args);
    void warn(String msg);
    void warn(String format, Object... args);
    void error(String msg);
    void error(String msg, Throwable t);
    void error(String format, Object... args);
}
