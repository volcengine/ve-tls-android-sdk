package com.volcengine.tls.android.producer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;
import java.nio.channels.FileLock;

final class ProducerWalDirectory {
    private static final String DEFAULT_DIRECTORY = "default";

    private ProducerWalDirectory() {}

    // Within a process, open runs on the SDK's single control executor. This file lock
    // serializes the directory selection with other processes in the same app.
    static String resolve(File filesDirectory, String producerId, String legacyWalDirectory) {
        if (filesDirectory == null) {
            throw failure(LogProducerInitializationException.Reason.WAL_OPEN_IO_ERROR,
                    new IOException("application files directory is unavailable"));
        }
        File defaultDirectory = new File(filesDirectory, "com.volcengine.tls/producer/" + producerId);
        File bindings = new File(filesDirectory, "com.volcengine.tls/producer-bindings");
        try {
            File requested = null;
            if (legacyWalDirectory != null) {
                requested = new File(legacyWalDirectory);
                if (!requested.isAbsolute()) {
                    throw new IllegalArgumentException("legacyWalDirectory must be an absolute WAL directory");
                }
                requested = requested.getCanonicalFile();
            }
            if (!bindings.isDirectory() && !bindings.mkdirs() && !bindings.isDirectory()) {
                throw new IOException("cannot create producer bindings directory");
            }
            File binding = new File(bindings, producerId + ".path");
            File lockFile = new File(bindings, producerId + ".lock");
            try (RandomAccessFile lock = new RandomAccessFile(lockFile, "rw")) {
                FileLock fileLock = lock.getChannel().lock();
                try {
                    if (binding.exists()) {
                        return resolveBoundDirectory(binding, defaultDirectory, requested);
                    }
                    if (requested == null) {
                        writeBinding(binding, DEFAULT_DIRECTORY);
                        return defaultDirectory.getAbsolutePath();
                    }
                    if (!hasWalManifest(requested)) {
                        throw new IllegalArgumentException("legacyWalDirectory must contain the existing WAL manifest");
                    }
                    writeBinding(binding, requested.getPath());
                    return requested.getPath();
                } finally {
                    fileLock.release();
                }
            }
        } catch (IOException error) {
            throw failure(LogProducerInitializationException.Reason.WAL_OPEN_IO_ERROR, error);
        }
    }

    private static String resolveBoundDirectory(File binding, File defaultDirectory, File requested) throws IOException {
        final String directory;
        try (DataInputStream input = new DataInputStream(new FileInputStream(binding))) {
            directory = input.readUTF();
            if (input.read() != -1) {
                throw failure(LogProducerInitializationException.Reason.WAL_OPEN_FAILED,
                        new IOException("invalid producer ID binding"));
            }
        } catch (EOFException | UTFDataFormatException error) {
            throw failure(LogProducerInitializationException.Reason.WAL_OPEN_FAILED, error);
        }

        boolean isDefault = DEFAULT_DIRECTORY.equals(directory);
        File boundDirectory = isDefault ? defaultDirectory : new File(directory);
        if (!isDefault && (!boundDirectory.isAbsolute() || !hasWalManifest(boundDirectory))) {
            throw failure(LogProducerInitializationException.Reason.WAL_OPEN_FAILED,
                    new IOException("bound legacy WAL directory is unavailable"));
        }
        if (requested != null && !requested.equals(boundDirectory.getCanonicalFile())) {
            throw new IllegalArgumentException("producerId is already bound to a different WAL directory");
        }
        return boundDirectory.getAbsolutePath();
    }

    private static boolean hasWalManifest(File directory) {
        return new File(directory, "manifest").isFile();
    }

    private static void writeBinding(File binding, String directory) throws IOException {
        File pending = new File(binding.getPath() + ".tmp");
        try (FileOutputStream file = new FileOutputStream(pending)) {
            DataOutputStream output = new DataOutputStream(file);
            output.writeUTF(directory);
            output.flush();
            file.getFD().sync();
        }
        if (!pending.renameTo(binding)) {
            throw new IOException("cannot publish producer ID binding");
        }
    }

    private static LogProducerInitializationException failure(
            LogProducerInitializationException.Reason reason, IOException cause) {
        LogProducerInitializationException error = new LogProducerInitializationException(reason);
        error.initCause(cause);
        return error;
    }
}
