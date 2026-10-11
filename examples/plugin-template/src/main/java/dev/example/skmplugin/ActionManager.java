package dev.example.skmplugin;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns this plugin's runtime actions and safely replaces them after validated config reloads. */
public final class ActionManager {
    private final Plugin owner;
    private final ActionRegistrationService registrationService;
    private final ActionExecutionService handler;
    private final Map<String, ActionDefinition> active = new LinkedHashMap<>();

    public ActionManager(Plugin owner, ActionRegistrationService registrationService, ActionExecutionService handler) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Replaces this plugin's actions. Definitions are prevalidated for duplicate IDs; if SKM rejects a
     * new registration (for example due to a server-wide action limit), the previous set is restored.
     */
    public int replace(Collection<ActionDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, ActionDefinition> next = new LinkedHashMap<>();
        for (ActionDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            if (next.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate action ID: " + definition.id());
            }
        }
        if (active.equals(next)) return active.size();

        Map<String, ActionDefinition> previous = new LinkedHashMap<>(active);
        registrationService.unregisterAll(owner);
        active.clear();
        try {
            registerAll(next.values());
        } catch (RuntimeException exception) {
            registrationService.unregisterAll(owner);
            active.clear();
            try {
                registerAll(previous.values());
            } catch (RuntimeException rollbackFailure) {
                registrationService.unregisterAll(owner);
                active.clear();
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
        return active.size();
    }

    public List<ActionDefinition> actions() {
        return List.copyOf(active.values());
    }

    public boolean owns(String actionId) {
        return active.containsKey(actionId);
    }

    /** Removes all owned actions; call on the server's main thread during plugin shutdown. */
    public void close() {
        registrationService.unregisterAll(owner);
        active.clear();
    }

    private void registerAll(Collection<ActionDefinition> definitions) {
        for (ActionDefinition definition : definitions) {
            registrationService.register(owner, definition, handler);
            active.put(definition.id(), definition);
        }
    }
}
