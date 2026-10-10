package io.github.skm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server action snapshots are disposable and bounded; player key overrides are stored separately. */
public final class ActionCache {
    private static final int MAX_ACTIONS = 256;
    private static final String SIGNATURE_PATTERN = "[a-z0-9][a-z0-9._-]{0,63}";
    private static final String REVISION_PATTERN = "[a-f0-9]{16}";
    private static final long MIN_WRITE_INTERVAL_NANOS = 1_000_000_000L;

    private String signature = "";
    private String revision = "";
    private List<ClientAction> actions = List.of();
    private final LinkedHashMap<String, Long> persistedWrites = new LinkedHashMap<>(16, 0.75f, true);

    public boolean matches(String requestedSignature, String requestedRevision) {
        if (!validSignature(requestedSignature) || requestedRevision == null
                || !requestedRevision.matches(REVISION_PATTERN)) return false;
        load(requestedSignature);
        return signature.equals(requestedSignature) && revision.equals(requestedRevision);
    }

    public List<ClientAction> actions() { return actions; }

    public void update(String signature, String revision, List<ClientAction> actions) {
        if (!validSignature(signature) || revision == null || !revision.matches(REVISION_PATTERN)
                || actions == null || actions.size() > MAX_ACTIONS || actions.stream().anyMatch(java.util.Objects::isNull)) {
            return;
        }
        this.signature = signature;
        this.revision = revision;
        this.actions = List.copyOf(actions);

        long now = System.nanoTime();
        Long previousWrite = persistedWrites.get(signature);
        if (previousWrite != null && now - previousWrite < MIN_WRITE_INTERVAL_NANOS) {
            return; // The live list is applied; a missed cache write only causes a later re-sync.
        }
        if (save()) {
            persistedWrites.put(signature, now);
            if (persistedWrites.size() > CacheFilePruner.MAX_CACHE_FILES) {
                Iterator<Map.Entry<String, Long>> iterator = persistedWrites.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    private void load(String nextSignature) {
        if (nextSignature.equals(signature)) return;
        signature = nextSignature;
        revision = "";
        actions = List.of();
        Path file = fileFor(nextSignature);
        if (!Files.isRegularFile(file)) return;
        try {
            long size = Files.size(file);
            if (size <= 0 || size > BridgePayloads.MAX_PAYLOAD_BYTES) {
                Files.deleteIfExists(file);
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (ProtocolJson.integer(root, "schemaVersion", 0, 2, 2) != 2
                    || !nextSignature.equals(ProtocolJson.string(root, "signature", 64))) return;
            String cachedRevision = ProtocolJson.string(root, "revision", 64);
            if (cachedRevision == null || !cachedRevision.matches(REVISION_PATTERN)) return;
            JsonElement actionElement = root.get("actions");
            if (actionElement == null || !actionElement.isJsonArray()) return;
            List<ClientAction> parsed = parseActions(actionElement.getAsJsonArray());
            if (parsed == null) return;
            revision = cachedRevision;
            actions = List.copyOf(parsed);
            CacheFilePruner.touch(file);
        } catch (RuntimeException | IOException exception) {
            // Corrupt cache is disposable; the next handshake requests an authoritative sync.
        }
    }

    private boolean save() {
        if (!validSignature(signature) || !revision.matches(REVISION_PATTERN)) return false;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("signature", signature);
        root.addProperty("revision", revision);
        JsonArray array = new JsonArray();
        actions.forEach(action -> array.add(action.toJson()));
        root.add("actions", array);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > BridgePayloads.MAX_PAYLOAD_BYTES) return false;
        try {
            Path file = fileFor(signature);
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            CacheFilePruner.prune(file.getParent(), file);
            return true;
        } catch (IOException | SecurityException exception) {
            SKMClient.LOGGER.warn("Could not save SKM action cache", exception);
            return false;
        }
    }

    private static List<ClientAction> parseActions(JsonArray array) {
        if (array == null || array.size() > MAX_ACTIONS) return null;
        List<ClientAction> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) return null;
            ClientAction action = ClientAction.fromSync(element.getAsJsonObject());
            if (action == null || !ids.add(action.id())) return null;
            parsed.add(action);
        }
        return parsed;
    }

    private static boolean validSignature(String value) {
        return value != null && value.matches(SIGNATURE_PATTERN);
    }

    private static Path fileFor(String signature) {
        return SKMStorage.file("cache", signature);
    }
}
