package io.github.skm.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class CacheFilePrunerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removesOldestSnapshotsAndAlwaysKeepsActiveSnapshot() throws Exception {
        Path active = null;
        for (int index = 0; index < 5; index++) {
            Path file = temporaryDirectory.resolve("server-" + index + ".json");
            Files.writeString(file, "{}");
            Files.setLastModifiedTime(file, FileTime.fromMillis(index + 1L));
            if (index == 4) active = file;
        }

        CacheFilePruner.prune(temporaryDirectory, active, 3);

        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertEquals(3, files.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
        assertTrue(Files.exists(active));
        assertFalse(Files.exists(temporaryDirectory.resolve("server-0.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("server-1.json")));
    }

    @Test
    void leavesUnrelatedFilesUntouched() throws Exception {
        Path active = temporaryDirectory.resolve("server-a.json");
        Files.writeString(active, "{}");
        Path unrelated = temporaryDirectory.resolve("notes.txt");
        Files.writeString(unrelated, "keep");

        CacheFilePruner.prune(temporaryDirectory, active, 1);

        assertTrue(Files.exists(active));
        assertTrue(Files.exists(unrelated));
    }
}
