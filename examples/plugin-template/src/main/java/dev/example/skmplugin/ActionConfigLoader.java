package dev.example.skmplugin;

import io.github.skm.api.ActionDefinition;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Parses and validates the starter plugin's config.yml action list before registration changes. */
public final class ActionConfigLoader {
    private ActionConfigLoader() {
    }

    public static List<ConfiguredAction> load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        if (!config.isList("actions")) {
            throw new IllegalArgumentException("config.yml must contain an actions: list (use actions: [] to disable all actions)");
        }
        List<?> rows = config.getList("actions", List.of());
        List<ConfiguredAction> enabledActions = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (int index = 0; index < rows.size(); index++) {
            String path = "actions[" + index + "]";
            Object row = rows.get(index);
            if (!(row instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException(path + " must be a YAML map");
            }

            String id = string(values, "id", null, path, true);
            String handler = string(values, "handler", null, path, true);
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException(path + " duplicates action ID " + id);
            }

            boolean enabled = bool(values, "enabled", true, path);
            String displayName = string(values, "display-name", id, path, false);
            String defaultKey = string(values, "default-key", "key.keyboard.unknown", path, false);
            String category = string(values, "category", "MySKMPlugin", path, false);
            String permission = string(values, "permission", null, path, false);
            long cooldownMillis = integer(values, "cooldown-ms", 0L, path);
            long requiredLevelValue = integer(values, "required-level", 0L, path);
            if (requiredLevelValue < Integer.MIN_VALUE || requiredLevelValue > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(path + ".required-level is outside the integer range");
            }

            ActionDefinition definition;
            try {
                definition = new ActionDefinition(
                        id,
                        displayName,
                        defaultKey,
                        cooldownMillis,
                        category,
                        bool(values, "holdable", false, path),
                        permission,
                        (int) requiredLevelValue
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + " has an invalid action definition: " + exception.getMessage(), exception);
            }

            if (enabled) {
                enabledActions.add(new ConfiguredAction(definition, handler));
            }
        }
        return List.copyOf(enabledActions);
    }

    private static String string(Map<?, ?> values, String key, String fallback, String path, boolean required) {
        Object raw = values.get(key);
        if (raw == null) {
            if (required) throw new IllegalArgumentException(path + "." + key + " is required");
            return fallback;
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(path + "." + key + " must be a string");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            if (required) throw new IllegalArgumentException(path + "." + key + " cannot be blank");
            return fallback;
        }
        return normalized;
    }

    private static boolean bool(Map<?, ?> values, String key, boolean fallback, String path) {
        Object raw = values.get(key);
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        throw new IllegalArgumentException(path + "." + key + " must be true or false");
    }

    private static long integer(Map<?, ?> values, String key, long fallback, String path) {
        Object raw = values.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + "." + key + " must be an integer");
        }
        double asDouble = number.doubleValue();
        long asLong = number.longValue();
        if (!Double.isFinite(asDouble) || asDouble != (double) asLong) {
            throw new IllegalArgumentException(path + "." + key + " must be an integer");
        }
        return asLong;
    }

    public record ConfiguredAction(ActionDefinition definition, String handler) {
        public ConfiguredAction {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(handler, "handler");
            if (handler.isBlank()) throw new IllegalArgumentException("handler cannot be blank");
        }
    }
}
