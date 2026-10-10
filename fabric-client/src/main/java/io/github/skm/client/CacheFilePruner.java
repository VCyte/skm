package io.github.skm.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Bounds only disposable action snapshots; player key overrides are stored elsewhere and untouched. */
final class CacheFilePruner {
    static final int MAX_CACHE_FILES = 128;
    private static final String CACHE_FILE = "[a-z0-9][a-z0-9._-]{0,63}\\.json";

    private CacheFilePruner() { }

    static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException | SecurityException ignored) {
            // Cache recency is best-effort; cache contents are disposable.
        }
    }

    static void prune(Path directory, Path activeFile) {
        prune(directory, activeFile, MAX_CACHE_FILES);
    }

    static void prune(Path directory, Path activeFile, int maximumFiles) {
        if (maximumFiles < 1) throw new IllegalArgumentException("maximumFiles must be positive");
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> files = entries
                    .filter(path -> path.getFileName().toString().matches(CACHE_FILE))
                    .filter(Files::isRegularFile)
                    .toList();
            int excess = files.size() - maximumFiles;
            if (excess <= 0) return;

            Path active = activeFile.toAbsolutePath().normalize();
            List<Path> oldestFirst = new ArrayList<>(files);
            oldestFirst.sort(Comparator
                    .comparingLong(CacheFilePruner::lastModifiedMillis)
                    .thenComparing(path -> path.getFileName().toString()));
            for (Path file : oldestFirst) {
                if (excess == 0) break;
                if (file.toAbsolutePath().normalize().equals(active)) continue;
                try {
                    if (Files.deleteIfExists(file)) excess--;
                } catch (IOException | SecurityException ignored) {
                    // A cache cleanup failure must never disrupt the live connection.
                }
            }
        } catch (IOException | SecurityException ignored) {
            // Cache cleanup is best-effort and never affects the authoritative live action list.
        }
    }

    private static long lastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | SecurityException ignored) {
            return Long.MIN_VALUE;
        }
    }
}
