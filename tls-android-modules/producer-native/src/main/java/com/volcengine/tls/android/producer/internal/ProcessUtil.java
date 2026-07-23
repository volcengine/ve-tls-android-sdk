package com.volcengine.tls.android.producer.internal;

import android.app.Application;
import android.os.Build;

import java.io.FileInputStream;
import java.io.IOException;

public final class ProcessUtil {
    private ProcessUtil() {
    }

    public static String getCurrentProcessName() {
        String processName = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                processName = Application.getProcessName();
            } catch (RuntimeException | NoSuchMethodError ignored) {
                // in local JVM tests, Android runtime methods may not be available
            } catch (LinkageError ignored) {
                // in some runtime environments, API compatibility linkage can fail
            }
        }

        if (processName != null && !processName.isEmpty()) {
            return processName;
        }

        return getProcessNameByPid();
    }

    public static boolean isMainProcess(String processName) {
        return processName == null || processName.trim().isEmpty() || !processName.contains(":");
    }

    public static String rewritePersistentPath(String path, String processName) {
        if (path == null || path.isEmpty() || isMainProcess(processName)) {
            return path;
        }
        String normalizedProcessName = processName.replace(':', '_');
        return path + FilePath.SEPARATOR + normalizedProcessName;
    }

    private static String getProcessNameByPid() {
        int pid = android.os.Process.myPid();
        String cmdlinePath = "/proc/" + pid + "/cmdline";
        byte[] buffer = new byte[128];
        int read;

        try (FileInputStream stream = new FileInputStream(cmdlinePath)) {
            read = stream.read(buffer);
        } catch (IOException ignored) {
            return null;
        }

        if (read <= 0) {
            return null;
        }

        int length = read;
        for (int i = 0; i < read; i++) {
            if (buffer[i] == 0) {
                length = i;
                break;
            }
        }
        return new String(buffer, 0, length).trim();
    }

    private static final class FilePath {
        private static final String SEPARATOR = "/";
    }
}
