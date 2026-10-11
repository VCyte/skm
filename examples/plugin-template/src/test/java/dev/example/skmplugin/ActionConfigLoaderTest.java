package dev.example.skmplugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActionConfigLoaderTest {
    @Test
    void loadsEnabledActionsAndSkipsDisabledActions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("actions", List.of(
                Map.of("id", "myskmplugin.ability", "handler", "ability", "default-key", "key.keyboard.g"),
                Map.of("id", "myskmplugin.disabled", "handler", "ability", "enabled", false)
        ));

        List<ActionConfigLoader.ConfiguredAction> actions = ActionConfigLoader.load(config);

        assertEquals(1, actions.size());
        assertEquals("myskmplugin.ability", actions.getFirst().definition().id());
        assertEquals("key.keyboard.g", actions.getFirst().definition().defaultKey());
        assertEquals("ability", actions.getFirst().handler());
        assertFalse(actions.getFirst().definition().holdable());
    }

    @Test
    void appliesDocumentedDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("actions", List.of(Map.of("id", "myskmplugin.default", "handler", "ability")));

        ActionConfigLoader.ConfiguredAction action = ActionConfigLoader.load(config).getFirst();

        assertEquals("myskmplugin.default", action.definition().displayName());
        assertEquals("key.keyboard.unknown", action.definition().defaultKey());
        assertEquals(0L, action.definition().cooldownMillis());
        assertEquals("MySKMPlugin", action.definition().category());
        assertTrue(action.definition().permission() == null);
    }

    @Test
    void rejectsDuplicateIdsEvenWhenOneEntryIsDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("actions", List.of(
                Map.of("id", "myskmplugin.same", "handler", "ability"),
                Map.of("id", "myskmplugin.same", "handler", "charge", "enabled", false)
        ));

        assertThrows(IllegalArgumentException.class, () -> ActionConfigLoader.load(config));
    }

    @Test
    void rejectsMissingActionsList() {
        assertThrows(IllegalArgumentException.class, () -> ActionConfigLoader.load(new YamlConfiguration()));
    }

    @Test
    void rejectsBadTypesAndInvalidActionFields() {
        YamlConfiguration badCooldown = new YamlConfiguration();
        badCooldown.set("actions", List.of(Map.of(
                "id", "myskmplugin.ability", "handler", "ability", "cooldown-ms", "fast"
        )));
        assertThrows(IllegalArgumentException.class, () -> ActionConfigLoader.load(badCooldown));

        YamlConfiguration badKey = new YamlConfiguration();
        badKey.set("actions", List.of(Map.of(
                "id", "myskmplugin.ability", "handler", "ability", "default-key", "not-a-minecraft-key"
        )));
        assertThrows(IllegalArgumentException.class, () -> ActionConfigLoader.load(badKey));
    }
}
