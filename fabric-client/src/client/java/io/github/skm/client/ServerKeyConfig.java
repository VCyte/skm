package io.github.skm.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Local key overrides are stored per server signature and never accepted from the network. */
final class ServerKeyConfig {
    private String signature = "";
    private final Map<String, String> overrides = new HashMap<>();

    void load(String nextSignature) {
        if (nextSignature.equals(signature)) return;
        signature = nextSignature;
        overrides.clear();
        Path file = fileFor(nextSignature);
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (ProtocolJson.integer(root, "schemaVersion", 0, 1, 1) != 1) return;
            if (!nextSignature.equals(ProtocolJson.string(root, "signature", 64))) return;
            JsonElement element = root.get("keyOverrides");
            if (element == null || !element.isJsonObject()) return;
            JsonObject keys = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : keys.entrySet()) {
                String id = entry.getKey();
                if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}") || !entry.getValue().isJsonPrimitive()) continue;
                String key = entry.getValue().getAsString();
                if (validKey(key)) overrides.put(id, key);
            }
        } catch (RuntimeException | IOException exception) {
            SKMClient.LOGGER.warn("Could not read per-server key config for {}", nextSignature, exception);
        }
    }

    String keyFor(ClientAction action) {
        return overrides.getOrDefault(action.id(), action.defaultKey());
    }

    void capture(Map<String, ClientAction> actions, Map<String, net.minecraft.client.KeyMapping> bindings) {
        if (signature.isEmpty()) return;
        boolean changed = false;
        for (Map.Entry<String, ClientAction> entry : actions.entrySet()) {
            net.minecraft.client.KeyMapping binding = bindings.get(entry.getKey());
            if (binding == null) continue;
            String actual = binding.saveString();
            String serverDefault = binding.getDefaultKey().getName();
            String old = overrides.get(entry.getKey());
            if (actual.equals(serverDefault)) {
                changed |= overrides.remove(entry.getKey()) != null;
            } else if (!actual.equals(old)) {
                overrides.put(entry.getKey(), actual);
                changed = true;
            }
        }
        if (changed) save();
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("signature", signature);
        JsonObject keys = new JsonObject();
        overrides.forEach(keys::addProperty);
        root.add("keyOverrides", keys);
        try {
            Path file = fileFor(signature);
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SKMClient.LOGGER.warn("Could not save per-server key config for {}", signature, exception);
        }
    }

    private static boolean validKey(String value) {
        if (value == null || value.length() > 64 || !value.matches("key\\.(keyboard|mouse)\\.[a-z0-9._-]{1,48}")) return false;
        try {
            com.mojang.blaze3d.platform.InputConstants.getKey(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Path fileFor(String signature) {
        return SKMStorage.file("servers", signature);
    }
}
