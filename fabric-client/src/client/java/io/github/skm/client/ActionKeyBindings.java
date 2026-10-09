package io.github.skm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import io.github.skm.client.mixin.KeyMappingAccessor;
import io.github.skm.client.mixin.KeyMappingCategoryAccessor;
import io.github.skm.client.mixin.OptionsAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates exactly the mappings in the current server's action list and hides them outside that connection. */
public final class ActionKeyBindings {
    private static final Map<String, KeyMapping> BINDINGS = new LinkedHashMap<>();
    private static final Map<String, ClientAction> ACTIONS = new LinkedHashMap<>();
    private static final Map<String, String> DYNAMIC_TRANSLATIONS = new HashMap<>();
    private static final Map<String, Boolean> HELD = new HashMap<>();
    private static final Set<Screen> OPEN_SCREENS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static KeyMapping[] BASE_MAPPINGS = new KeyMapping[0];
    private static KeyMapping.Category CATEGORY;
    private static boolean optionsReady;
    private static String pendingSignature = "";
    private static List<ClientAction> pendingActions = List.of();
    private static BridgeNetwork network;
    private static FeedbackState feedback;
    private static final ServerKeyConfig KEY_CONFIG = new ServerKeyConfig();
    private static String activeSignature = "";

    private ActionKeyBindings() {
    }

    static void initialize(BridgeNetwork nextNetwork, FeedbackState nextFeedback) {
        network = nextNetwork;
        feedback = nextFeedback;
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            OPEN_SCREENS.add(screen);
            ScreenEvents.remove(screen).register(removed -> OPEN_SCREENS.remove(removed));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!optionsReady && client.options != null) {
                BASE_MAPPINGS = client.options.keyMappings.clone();
                registerTopCategory();
                optionsReady = true;
            }
            if (optionsReady && !pendingSignature.isEmpty()) {
                String signature = pendingSignature;
                List<ClientAction> actions = pendingActions;
                pendingSignature = "";
                pendingActions = List.of();
                apply(signature, actions);
            }
            tick(OPEN_SCREENS.isEmpty() && client.player != null);
        });
    }

    static void apply(String signature, List<ClientAction> actions) {
        if (!optionsReady && Minecraft.getInstance().options != null) {
            Minecraft minecraft = Minecraft.getInstance();
            BASE_MAPPINGS = minecraft.options.keyMappings.clone();
            registerTopCategory();
            optionsReady = true;
        }
        if (!optionsReady) {
            pendingSignature = signature;
            pendingActions = List.copyOf(actions);
            return;
        }
        clearServerActions();
        if (signature == null || !signature.matches("[a-z0-9][a-z0-9._-]{0,63}") || actions.size() > 256) return;
        activeSignature = signature;
        KEY_CONFIG.load(signature);
        Map<String, ClientAction> nextActions = new LinkedHashMap<>();
        for (ClientAction action : actions) {
            if (nextActions.putIfAbsent(action.id(), action) != null) {
                clearServerActions();
                return;
            }
        }

        for (ClientAction action : nextActions.values()) {
            String mappingName = mappingName(signature, action.id());
            InputConstants.Key serverDefault = parseKey(action.defaultKey());
            KeyMapping binding = new KeyMapping(mappingName, serverDefault.getType(), serverDefault.getValue(), CATEGORY);
            ((KeyMappingAccessor) binding).skm$setDefaultKey(serverDefault);
            InputConstants.Key localKey = parseKey(KEY_CONFIG.keyFor(action));
            binding.setKey(localKey);
            BINDINGS.put(action.id(), binding);
            ACTIONS.put(action.id(), action);
        }
        KEY_CONFIG.capture(ACTIONS, BINDINGS);
        refreshVisibleMappings();
        refreshTranslations();
        KeyMapping.resetMapping();
        SKMClient.LOGGER.info("Registered {} SKM server-specific key binding(s) for '{}'.", BINDINGS.size(), signature);
    }

    static void clearServerActions() {
        releaseAll();
        KEY_CONFIG.capture(ACTIONS, BINDINGS);
        pendingSignature = "";
        pendingActions = List.of();
        activeSignature = "";
        ACTIONS.clear();
        DYNAMIC_TRANSLATIONS.clear();
        HELD.clear();
        hideMappings();
        removeDynamicMappings();
    }

    private static void tick(boolean inWorldWithoutScreen) {
        if (!ACTIONS.isEmpty() && !activeSignature.isEmpty()) {
            KEY_CONFIG.capture(ACTIONS, BINDINGS);
        }
        if (!inWorldWithoutScreen || network == null || !network.isActive()) {
            releaseAll();
            return;
        }
        for (Map.Entry<String, ClientAction> entry : ACTIONS.entrySet()) {
            String id = entry.getKey();
            ClientAction action = entry.getValue();
            KeyMapping binding = BINDINGS.get(id);
            if (binding == null) continue;
            if (action.holdable()) {
                boolean down = binding.isDown();
                boolean wasDown = HELD.getOrDefault(id, false);
                if (down && !wasDown) {
                    network.sendInput(id, "press");
                    feedback.predict(action);
                } else if (!down && wasDown) {
                    network.sendInput(id, "release");
                }
                HELD.put(id, down);
            } else {
                while (binding.consumeClick()) {
                    network.sendInput(id, "press");
                    feedback.predict(action);
                }
            }
        }
    }

    private static void releaseAll() {
        if (network != null && network.isActive()) {
            for (Map.Entry<String, Boolean> entry : HELD.entrySet()) {
                if (entry.getValue() && ACTIONS.containsKey(entry.getKey())) {
                    network.sendInput(entry.getKey(), "release");
                }
            }
        }
        HELD.clear();
    }

    private static void refreshVisibleMappings() {
        if (!optionsReady) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<KeyMapping> mappings = new ArrayList<>(List.of(BASE_MAPPINGS));
        mappings.addAll(BINDINGS.values());
        ((OptionsAccessor) minecraft.options).skm$setKeyMappings(mappings.toArray(KeyMapping[]::new));
    }

    private static void hideMappings() {
        Minecraft minecraft = Minecraft.getInstance();
        if (optionsReady && minecraft.options != null) {
            ((OptionsAccessor) minecraft.options).skm$setKeyMappings(BASE_MAPPINGS.clone());
        }
    }

    private static void removeDynamicMappings() {
        if (BINDINGS.isEmpty()) return;
        Map<String, KeyMapping> all = KeyMappingAccessor.skm$getAll();
        for (Map.Entry<String, KeyMapping> entry : BINDINGS.entrySet()) {
            all.remove(entry.getValue().getName(), entry.getValue());
        }
        Map<InputConstants.Key, List<KeyMapping>> byKey = KeyMappingAccessor.skm$getByKey();
        byKey.values().forEach(list -> list.removeIf(binding -> BINDINGS.containsValue(binding)));
        byKey.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        BINDINGS.clear();
        KeyMapping.resetMapping();
    }

    private static void refreshTranslations() {
        DYNAMIC_TRANSLATIONS.clear();
        ACTIONS.forEach((id, action) -> DYNAMIC_TRANSLATIONS.put(mappingName(activeSignature, id), action.name()));
        if (!activeSignature.isEmpty()) {
        DYNAMIC_TRANSLATIONS.put("key.category.skm.actions", "SKM · " + activeSignature);
        }
    }

    private static void registerTopCategory() {
        CATEGORY = KeyMapping.Category.register(SKMClient.id("actions"));
        List<KeyMapping.Category> order = KeyMappingCategoryAccessor.skm$getSortOrder();
        order.remove(CATEGORY);
        order.add(0, CATEGORY);
    }

    /** Called by a read-only language lookup mixin; Minecraft's storage map stays immutable. */
    public static String translationFor(String key) {
        return DYNAMIC_TRANSLATIONS.get(key);
    }

    private static String mappingName(String signature, String actionId) {
        return "key.skm." + signature + "." + actionId;
    }

    private static InputConstants.Key parseKey(String value) {
        try {
            return InputConstants.getKey(value);
        } catch (RuntimeException exception) {
            SKMClient.LOGGER.warn("Ignored invalid key token {}", value);
            return InputConstants.UNKNOWN;
        }
    }
}
