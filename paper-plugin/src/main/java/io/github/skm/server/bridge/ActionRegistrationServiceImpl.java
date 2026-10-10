package io.github.skm.server.bridge;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;

/** Runtime registration facade exposed to dependent plugins through Bukkit services. */
public final class ActionRegistrationServiceImpl implements ActionRegistrationService {
    private final ActionRegistry registry;
    private final BridgeMessenger messenger;
    private final CooldownTracker cooldowns;
    private final ActionRateLimiter rateLimiter;

    public ActionRegistrationServiceImpl(ActionRegistry registry, BridgeMessenger messenger,
                                         CooldownTracker cooldowns, ActionRateLimiter rateLimiter) {
        this.registry = registry;
        this.messenger = messenger;
        this.cooldowns = cooldowns;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(Plugin owner, ActionDefinition action, ActionExecutionService handler) {
        requirePrimaryThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(handler, "handler");
        registry.register(owner, action, handler);
        cooldowns.removeAction(action.id());
        rateLimiter.removeAction(action.id());
        messenger.broadcastUpdate();
    }

    @Override
    public boolean unregister(Plugin owner, String actionId) {
        requirePrimaryThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(actionId, "actionId");
        if (!registry.unregister(owner, actionId)) return false;
        clearActionState(actionId);
        messenger.broadcastUpdate();
        return true;
    }

    @Override
    public int unregisterAll(Plugin owner) {
        requirePrimaryThread();
        Objects.requireNonNull(owner, "owner");
        Set<String> removed = registry.unregisterAll(owner);
        removed.forEach(this::clearActionState);
        if (!removed.isEmpty()) messenger.broadcastUpdate();
        return removed.size();
    }

    private void clearActionState(String actionId) {
        cooldowns.removeAction(actionId);
        rateLimiter.removeAction(actionId);
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("SKM actions must be registered or removed on the server primary thread");
        }
    }
}
