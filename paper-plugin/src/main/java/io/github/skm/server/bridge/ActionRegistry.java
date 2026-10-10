package io.github.skm.server.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeMap;

/** Atomically replaces the action snapshot only after the complete YAML file validates. */
public final class ActionRegistry {
    private volatile Snapshot snapshot = new Snapshot("bootstrap", Map.of());
    private final File actionsFile;
    private final int maxActions;
    private Map<String, ActionDefinition> fileActions = Map.of();
    private Map<String, RuntimeAction> runtimeActions = new LinkedHashMap<>();
    private static final Set<String> ACTION_FIELDS = Set.of(
            "name", "default-key", "cooldown-ms", "category", "holdable", "permission", "required-level"
    );

    public ActionRegistry(File actionsFile, int maxActions) {
        this.actionsFile = actionsFile;
        if (maxActions < 1 || maxActions > Protocol.MAX_ACTIONS) {
            throw new IllegalArgumentException("max-actions must be between 1 and " + Protocol.MAX_ACTIONS);
        }
        this.maxActions = maxActions;
    }

    public synchronized boolean reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(actionsFile);
        ConfigurationSection root = yaml.getConfigurationSection("actions");
        Map<String, ActionDefinition> next = new TreeMap<>();
        if (root != null) {
            collectActions(root, "", next);
        }

        Snapshot candidate = buildSnapshot(next, runtimeActions);
        boolean changed = !candidate.revision().equals(snapshot.revision());
        fileActions = Map.copyOf(next);
        snapshot = candidate;
        return changed;
    }

    public synchronized void register(Plugin owner, ActionDefinition action, ActionExecutionService handler) {
        if (fileActions.containsKey(action.id()) || runtimeActions.containsKey(action.id())) {
            throw new IllegalArgumentException("action id is already registered: " + action.id());
        }
        Map<String, RuntimeAction> nextRuntimeActions = new LinkedHashMap<>(runtimeActions);
        nextRuntimeActions.put(action.id(), new RuntimeAction(owner, action, handler));
        Snapshot candidate = buildSnapshot(fileActions, nextRuntimeActions);
        runtimeActions = nextRuntimeActions;
        snapshot = candidate;
    }

    public synchronized boolean unregister(Plugin owner, String actionId) {
        RuntimeAction current = runtimeActions.get(actionId);
        if (current == null || current.owner() != owner) return false;
        Map<String, RuntimeAction> nextRuntimeActions = new LinkedHashMap<>(runtimeActions);
        nextRuntimeActions.remove(actionId);
        snapshot = buildSnapshot(fileActions, nextRuntimeActions);
        runtimeActions = nextRuntimeActions;
        return true;
    }

    public synchronized Set<String> unregisterAll(Plugin owner) {
        Map<String, RuntimeAction> nextRuntimeActions = new LinkedHashMap<>(runtimeActions);
        Set<String> removed = new LinkedHashSet<>();
        nextRuntimeActions.entrySet().removeIf(entry -> {
            if (entry.getValue().owner() != owner) return false;
            removed.add(entry.getKey());
            return true;
        });
        if (!removed.isEmpty()) {
            snapshot = buildSnapshot(fileActions, nextRuntimeActions);
            runtimeActions = nextRuntimeActions;
        }
        return removed;
    }

    public Optional<ActionExecutionService> handlerFor(String id) {
        RuntimeAction action = runtimeActions.get(id);
        return action == null ? Optional.empty() : Optional.of(action.handler());
    }

    private Snapshot buildSnapshot(Map<String, ActionDefinition> configured,
                                   Map<String, RuntimeAction> dynamic) {
        Map<String, ActionDefinition> combined = new TreeMap<>(configured);
        dynamic.forEach((id, action) -> {
            if (combined.putIfAbsent(id, action.definition()) != null) {
                throw new IllegalArgumentException("duplicate action id: " + id);
            }
        });
        if (combined.size() > maxActions) {
            throw new IllegalArgumentException("action count exceeds configured max-actions " + maxActions);
        }
        String revision = fingerprint(combined.values());
        JsonObject syncPreview = new JsonObject();
        syncPreview.addProperty("revision", revision);
        syncPreview.addProperty("maxActions", maxActions);
        JsonArray previewActions = new JsonArray();
        combined.values().forEach(action -> previewActions.add(ActionJsonCodec.toSyncJson(action)));
        syncPreview.add("actions", previewActions);
        JsonCodec.encode(syncPreview); // Reject oversized snapshots before replacing the last valid state.
        return new Snapshot(revision, Map.copyOf(combined));
    }

    public Optional<ActionDefinition> find(String id) {
        return Optional.ofNullable(snapshot.actions().get(id));
    }

    public Collection<ActionDefinition> all() {
        return snapshot.actions().values();
    }

    public String revision() {
        return snapshot.revision();
    }

    /**
     * Bukkit's YAML ConfigurationSection treats dots as path separators. Recover namespaced IDs
     * such as {@code skill.test} whether SnakeYAML retained the dotted key or Bukkit expanded it
     * into nested {@code skill -> test} sections.
     */
    private void collectActions(ConfigurationSection section, String prefix, Map<String, ActionDefinition> target) {
        Set<String> keys = section.getKeys(false);
        boolean isAction = keys.isEmpty() || keys.stream().anyMatch(ACTION_FIELDS::contains);
        if (isAction) {
            if (prefix.isEmpty()) {
                throw new IllegalArgumentException("each action must have an ID under actions:");
            }
            ActionDefinition definition = parseAction(prefix, section);
            if (target.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate action id after normalization: " + prefix);
            }
            if (target.size() > maxActions) {
                throw new IllegalArgumentException("action count exceeds configured max-actions " + maxActions);
            }
            return;
        }

        for (String child : section.getKeys(false)) {
            String id = prefix.isEmpty() ? child : prefix + "." + child;
            ConfigurationSection childSection = section.getConfigurationSection(child);
            if (childSection == null) {
                throw new IllegalArgumentException("actions." + id + " must be an action or namespace section");
            }
            collectActions(childSection, id, target);
        }
    }

    private static ActionDefinition parseAction(String id, ConfigurationSection section) {
        String normalizedId = id == null ? "" : id.toLowerCase(Locale.ROOT);
        String defaultKey = section.getString("default-key", "key.keyboard.unknown").toLowerCase(Locale.ROOT);
        long cooldown = section.getLong("cooldown-ms", 0);
        int requiredLevel = section.getInt("required-level", 0);
        String permission = section.getString("permission");
        if (permission != null && permission.isBlank()) permission = null;
        String displayName = section.getString("name", normalizedId);
        String category = section.getString("category", "misc");
        return new ActionDefinition(normalizedId, displayName, defaultKey, cooldown, category,
                section.getBoolean("holdable", false), permission, requiredLevel);
    }

    private static String fingerprint(Collection<ActionDefinition> actions) {
        StringBuilder canonical = new StringBuilder();
        actions.stream().sorted(Comparator.comparing(ActionDefinition::id)).forEach(action -> canonical
                .append(action.id()).append('|')
                .append(action.displayName()).append('|')
                .append(action.defaultKey()).append('|')
                .append(action.cooldownMillis()).append('|')
                .append(action.category()).append('|')
                .append(action.holdable()).append('|')
                .append(action.permission()).append('|')
                .append(action.requiredLevel()).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Snapshot(String revision, Map<String, ActionDefinition> actions) {
    }

    private record RuntimeAction(Plugin owner, ActionDefinition definition, ActionExecutionService handler) {
    }
}
