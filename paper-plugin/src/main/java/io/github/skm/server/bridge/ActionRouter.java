package io.github.skm.server.bridge;

import io.github.skm.api.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** The only route from client intent to server-side action execution. */
public final class ActionRouter {
    private final io.github.skm.server.SKMPlugin plugin;
    private final ActionRegistry registry;
    private final CooldownTracker cooldowns;
    private final ActionRateLimiter rateLimiter;
    private final BridgeMessenger messenger;

    public ActionRouter(io.github.skm.server.SKMPlugin plugin, ActionRegistry registry, CooldownTracker cooldowns, ActionRateLimiter rateLimiter, BridgeMessenger messenger) {
        this.plugin = plugin;
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.rateLimiter = rateLimiter;
        this.messenger = messenger;
    }

    public void dispatch(Player player, String actionId, ActionExecutionService.InputState state) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> dispatch(player, actionId, state));
            return;
        }

        long now = System.currentTimeMillis();
        if (!rateLimiter.allow(player.getUniqueId(), actionId, now)) {
            messenger.sendFeedback(player, actionId, ActionResult.DENIED, 0, "rate_limited");
            return;
        }

        ActionDefinition action = registry.find(actionId).orElse(null);
        if (action == null) {
            return;
        }
        if (state == ActionExecutionService.InputState.RELEASE && !action.holdable()) {
            messenger.sendFeedback(player, actionId, ActionResult.DENIED, 0, "not_holdable");
            return;
        }
        if (action.permission() != null && !player.hasPermission(action.permission())) {
            messenger.sendFeedback(player, actionId, ActionResult.DENIED, 0, "missing_permission");
            return;
        }
        if (action.requiredLevel() > 0 && !meetsLevel(player, action.requiredLevel())) {
            messenger.sendFeedback(player, actionId, ActionResult.DENIED, 0, "level_requirement");
            return;
        }
        if (state == ActionExecutionService.InputState.PRESS) {
            long remaining = cooldowns.remaining(player.getUniqueId(), actionId, now);
            if (remaining > 0) {
                messenger.sendFeedback(player, actionId, ActionResult.DENIED, remaining, "cooldown");
                return;
            }
        }

        PlayerActionEvent event = new PlayerActionEvent(player, action, state);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            messenger.sendFeedback(player, actionId, ActionResult.DENIED, 0, "cancelled");
            return;
        }

        ActionExecutionService handler = registry.handlerFor(actionId).orElseGet(this::executionService);
        ActionResult result = handler.execute(player, action, state);
        if (result == ActionResult.SUCCESS && state == ActionExecutionService.InputState.PRESS) {
            cooldowns.start(player.getUniqueId(), actionId, action.cooldownMillis(), now);
        }
        messenger.sendFeedback(player, actionId, result, 0, null);
    }

    public void remove(Player player) {
        cooldowns.remove(player.getUniqueId());
        rateLimiter.remove(player.getUniqueId());
    }

    private ActionExecutionService executionService() {
        RegisteredServiceProvider<ActionExecutionService> provider = Bukkit.getServicesManager().getRegistration(ActionExecutionService.class);
        return provider == null ? new DefaultActionExecutionService() : provider.getProvider();
    }

    private boolean meetsLevel(Player player, int requiredLevel) {
        RegisteredServiceProvider<LevelRequirementService> provider = Bukkit.getServicesManager().getRegistration(LevelRequirementService.class);
        return provider != null && provider.getProvider().meets(player, requiredLevel);
    }
}
