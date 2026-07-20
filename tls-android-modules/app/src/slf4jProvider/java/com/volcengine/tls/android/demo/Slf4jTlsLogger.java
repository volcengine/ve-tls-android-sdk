package com.volcengine.tls.android.demo;

import com.volcengine.util.TlsLogger;

import org.slf4j.Logger;

final class Slf4jTlsLogger implements TlsLogger {
    private final Logger logger;

    Slf4jTlsLogger(Logger logger) {
        this.logger = logger;
    }

    @Override public void debug(String msg) { logger.debug(msg); }
    @Override public void debug(String format, Object... args) { logger.debug(format, args); }
    @Override public void info(String msg) { logger.info(msg); }
    @Override public void info(String format, Object... args) { logger.info(format, args); }
    @Override public void warn(String msg) { logger.warn(msg); }
    @Override public void warn(String format, Object... args) { logger.warn(format, args); }
    @Override public void error(String msg) { logger.error(msg); }
    @Override public void error(String msg, Throwable t) { logger.error(msg, t); }
    @Override public void error(String format, Object... args) { logger.error(format, args); }
}
