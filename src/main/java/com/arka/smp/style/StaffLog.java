package com.arka.smp.style;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The staff action log: {@code logs/staff-actions.log} in the plugin folder, one line per action. It is its own file,
 * apart from errors.log. Written on its own thread (never slows the game), never throws, rotated at 5 MB with the 10
 * newest old files kept (staff-actions.log.1 is the newest). No server needed.
 */
final class StaffLog {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int KEEP = 10;

    private final File dir;
    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "staff-log-writer");
        t.setDaemon(true);
        return t;
    });

    StaffLog(File pluginFolder) {
        this.dir = new File(pluginFolder, "logs");
        this.file = new File(dir, "staff-actions.log");
    }

    File file() {
        return file;
    }

    /** Adds one line (a line break is added). */
    void write(String line) {
        String text = line + "\n";
        try {
            io.execute(() -> append(text));
        } catch (RuntimeException closed) {
            append(text);   // after close(): write it directly so nothing is lost
        }
    }

    /** Waits (up to 3 seconds) for the lines still waiting to be written. */
    void close() {
        io.shutdown();
        try {
            io.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void append(String text) {
        try {
            Files.createDirectories(dir.toPath());
            if (file.exists() && file.length() > MAX_BYTES) rotate();
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.err.println("[ArkaSMPStyle] could not write " + file + ": " + e);   // the log must never cause errors
        }
    }

    private void rotate() throws IOException {
        File last = new File(dir, "staff-actions.log." + KEEP);
        if (last.exists()) Files.delete(last.toPath());
        for (int i = KEEP - 1; i >= 1; i--) {
            File from = new File(dir, "staff-actions.log." + i);
            if (from.exists()) Files.move(from.toPath(), new File(dir, "staff-actions.log." + (i + 1)).toPath());
        }
        Files.move(file.toPath(), new File(dir, "staff-actions.log.1").toPath());
    }
}
