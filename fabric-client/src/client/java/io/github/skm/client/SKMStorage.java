package io.github.skm.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** SKM client config paths with one-time, per-file migration from the previous folder name. */
final class SKMStorage {
    private SKMStorage() { }

    static Path file(String area, String signature) {
        String safe = signature.matches("[a-z0-9][a-z0-9._-]{0,63}") ? signature : "invalid";
        Path config = FabricLoader.getInstance().getConfigDir();
        Path current = config.resolve("skm").resolve(area).resolve(safe + ".json");
        Path legacy = config.resolve("rpgbridge").resolve(area).resolve(safe + ".json");
        if (!Files.exists(current) && Files.isRegularFile(legacy)) {
            try {
                Files.createDirectories(current.getParent());
                Files.copy(legacy, current, StandardCopyOption.COPY_ATTRIBUTES);
                SKMClient.LOGGER.info("Migrated existing {} settings to the SKM config folder.", area);
            } catch (IOException exception) {
                SKMClient.LOGGER.warn("Could not migrate legacy {} settings; the original file was preserved.", area, exception);
            }
        }
        return current;
    }
}
