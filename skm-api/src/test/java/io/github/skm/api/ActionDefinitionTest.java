package io.github.skm.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionDefinitionTest {
    @Test
    void acceptsNamespacedIdentifiersAndSupportedKeyTokens() {
        ActionDefinition keyboard = action("myplugin.dash", "key.keyboard.g", 0, 0, "Dash", "myplugin", null);
        ActionDefinition mouse = action("myplugin.attack", "key.mouse.1", 3_600_000, 1_000_000, "Attack", "combat", "myplugin.attack");
        ActionDefinition namedMouse = action("myplugin.mouse", "key.mouse.left", 0, 0, "Mouse", "misc", null);
        ActionDefinition unbound = action("myplugin.unbound", "key.keyboard.unknown", 0, 0, "Unbound", "misc", null);

        assertEquals("myplugin.dash", keyboard.id());
        assertEquals("key.mouse.1", mouse.defaultKey());
        assertEquals("key.mouse.left", namedMouse.defaultKey());
        assertEquals("key.keyboard.unknown", unbound.defaultKey());
    }

    @Test
    void rejectsMalformedOrOversizedIdentifiers() {
        for (String id : new String[]{"", "Upper.case", ".leading", "my plugin", "a".repeat(65)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> action(id, "key.keyboard.g", 0, 0, "Action", "misc", null), id);
        }
        assertEquals(64, action("a" + "b".repeat(63), "key.keyboard.g", 0, 0, "Action", "misc", null).id().length());
    }

    @Test
    void acceptsIdentifierAndMetadataLengthBoundaries() {
        action("a" + "b".repeat(63), "key.keyboard.g", 0, 0,
                "n".repeat(128), "c".repeat(64), null);

        assertThrows(IllegalArgumentException.class,
                () -> action("myplugin.action", "key.keyboard.g", 0, 0, "n".repeat(129), "misc", null));
        assertThrows(IllegalArgumentException.class,
                () -> action("myplugin.action", "key.keyboard.g", 0, 0, "Action", "c".repeat(65), null));
    }

    @Test
    void acceptsOnlySupportedDefaultKeyTokens() {
        for (String key : new String[]{"", "keyboard.g", "key.keyboard.", "key.keyboard.G", "key.mouse.", "key.keyboard." + "x".repeat(49)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> action("myplugin.action", key, 0, 0, "Action", "misc", null), key);
        }
    }

    @Test
    void enforcesCooldownAndRequiredLevelBounds() {
        for (long cooldown : new long[]{-1, 3_600_001}) {
            assertThrows(IllegalArgumentException.class,
                    () -> action("myplugin.action", "key.keyboard.g", cooldown, 0, "Action", "misc", null));
        }
        for (int level : new int[]{-1, 1_000_001}) {
            assertThrows(IllegalArgumentException.class,
                    () -> action("myplugin.action", "key.keyboard.g", 0, level, "Action", "misc", null));
        }
    }

    @Test
    void normalizesBlankOptionalPermissionToNull() {
        ActionDefinition definition = action("myplugin.action", "key.keyboard.g", 0, 0, "Action", "misc", "   ");
        assertNull(definition.permission());
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new ActionDefinition(null, "Action", "key.keyboard.g", 0, "misc", false, null, 0));
        assertThrows(NullPointerException.class, () -> new ActionDefinition("myplugin.action", null, "key.keyboard.g", 0, "misc", false, null, 0));
        assertThrows(NullPointerException.class, () -> new ActionDefinition("myplugin.action", "Action", null, 0, "misc", false, null, 0));
        assertThrows(NullPointerException.class, () -> new ActionDefinition("myplugin.action", "Action", "key.keyboard.g", 0, null, false, null, 0));
    }

    private static ActionDefinition action(String id, String key, long cooldown, int level,
                                           String displayName, String category, String permission) {
        return new ActionDefinition(id, displayName, key, cooldown, category, false, permission, level);
    }
}
