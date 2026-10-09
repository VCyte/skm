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
import java.util.List;
import java.util.Set;

/** Server action caches are isolated by the exact server signature. */
public final class ActionCache {
    private static final int MAX_ACTIONS = 256;
    private String signature = "";
    private String revision = "";
    private List<ClientAction> actions = List.of();

    public boolean matches(String requestedSignature, String requestedRevision) {
        load(requestedSignature);
        return signature.equals(requestedSignature) && revision.equals(requestedRevision);
    }

    public List<ClientAction> actions() { return actions; }

    public void update(String signature, String revision, List<ClientAction> actions) {
        this.signature = signature;
        this.revision = revision;
        this.actions = List.copyOf(actions);
        save();
    }

    private void load(String nextSignature) {
        if (nextSignature.equals(signature)) return;
        signature = nextSignature;
        revision = "";
        actions = List.of();
        Path file = fileFor(nextSignature);
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!nextSignature.equals(ProtocolJson.string(root, "signature", 64))) return;
            String cachedRevision = ProtocolJson.string(root, "revision", 64);
            if (cachedRevision == null) return;
            List<ClientAction> parsed = parseActions(root.getAsJsonArray("actions"));
            if (parsed == null) return;
            revision = cachedRevision;
            actions = List.copyOf(parsed);
        } catch (RuntimeException | IOException ignored) {
            // Corrupt cache is disposable; the next handshake requests an authoritative sync.
        }
    }

    private void save() {
        if (signature.isEmpty()) return;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("signature", signature);
        root.addProperty("revision", revision);
        JsonArray array = new JsonArray();
        actions.forEach(action -> array.add(action.toJson()));
        root.add("actions", array);
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
            SKMClient.LOGGER.warn("Could not save SKM action cache", exception);
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

    private static Path fileFor(String signature) {
        return SKMStorage.file("cache", signature);
    }
}
