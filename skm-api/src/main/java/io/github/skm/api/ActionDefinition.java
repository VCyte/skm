package io.github.skm.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable definition of one server-owned action exposed to SKM clients.
 *
 * @param id stable lowercase action identifier, for example {@code skill.fireball}
 * @param displayName name shown in the Minecraft controls list
 * @param defaultKey Minecraft key token, for example {@code key.keyboard.z}
 * @param cooldownMillis server cooldown in milliseconds
 * @param category server-owned grouping label
 * @param holdable whether press and release states are both meaningful
 * @param permission optional Paper permission required to run the action
 * @param requiredLevel optional progression level requirement
 */
public record ActionDefinition(
        String id,
        String displayName,
        String defaultKey,
        long cooldownMillis,
        String category,
        boolean holdable,
        String permission,
        int requiredLevel
) {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern KEY = Pattern.compile("key\\.(keyboard|mouse)\\.[a-z0-9._-]{1,48}");

    /** Validates all field constraints when an action model is created. */
    public ActionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(defaultKey, "defaultKey");
        Objects.requireNonNull(category, "category");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("invalid action id: " + id);
        if (displayName.length() > 128) throw new IllegalArgumentException("displayName exceeds 128 characters");
        if (!("key.keyboard.unknown".equals(defaultKey) || KEY.matcher(defaultKey).matches())) {
            throw new IllegalArgumentException("invalid defaultKey: " + defaultKey);
        }
        if (cooldownMillis < 0 || cooldownMillis > 3_600_000L) {
            throw new IllegalArgumentException("cooldownMillis must be between 0 and 3600000");
        }
        if (category.length() > 64) throw new IllegalArgumentException("category exceeds 64 characters");
        if (requiredLevel < 0 || requiredLevel > 1_000_000) {
            throw new IllegalArgumentException("requiredLevel must be between 0 and 1000000");
        }
        if (permission != null && permission.isBlank()) permission = null;
    }
}
