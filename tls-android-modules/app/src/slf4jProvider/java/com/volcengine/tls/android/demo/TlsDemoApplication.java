package com.volcengine.tls.android.demo;

import android.app.Application;

import com.volcengine.util.TlsLogger;
import com.volcengine.util.TlsLoggerFactory;
import com.volcengine.util.TlsLoggerProvider;

import org.slf4j.LoggerFactory;

public final class TlsDemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TlsLoggerFactory.setProvider(new Slf4jProvider());
        TlsLoggerFactory.getLogger(TlsDemoApplication.class).info("slf4j provider enabled");
    }

    private static final class Slf4jProvider implements TlsLoggerProvider {
        @Override
        public TlsLogger getLogger(String name) {
            return new Slf4jTlsLogger(LoggerFactory.getLogger(name));
        }
    }
}
