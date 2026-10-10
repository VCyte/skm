package io.github.skm.api;

import org.bukkit.plugin.Plugin;

/** Registers server actions at runtime and routes their validated input to the owning plugin. */
public interface ActionRegistrationService {
    /**
     * Adds an action to the live server list and sends the updated key bindings to connected SKM clients.
     * The action ID must not already exist in actions.yml or another runtime registration.
     * Calls must be made on the server's primary thread. The owner is automatically unregistered when disabled.
     *
     * @param owner plugin that owns the action
     * @param action definition sent to connected clients
     * @param handler plugin that executes validated press and release input
     * @throws IllegalArgumentException if the ID conflicts with another action or the server action limit is reached
     * @throws IllegalStateException if called off the server's primary thread
     */
    void register(Plugin owner, ActionDefinition action, ActionExecutionService handler);

    /**
     * Removes an action owned by the given plugin and updates connected clients.
     *
     * @param owner plugin that registered the action
     * @param actionId stable action ID
     * @return true if an action owned by this plugin was removed
     * @throws IllegalStateException if called off the server's primary thread
     */
    boolean unregister(Plugin owner, String actionId);

    /**
     * Removes all runtime actions belonging to a plugin and updates connected clients when anything changed.
     * Calls must be made on the server's primary thread.
     *
     * @param owner plugin that registered the actions
     * @return number of removed actions
     * @throws IllegalStateException if called off the server's primary thread
     */
    int unregisterAll(Plugin owner);
}
