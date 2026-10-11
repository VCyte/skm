package dev.example.skmplugin;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Copyable starting point for a Paper plugin that owns an SKM action. */
public final class MySKMPlugin extends JavaPlugin implements ActionExecutionService {
    private ActionRegistrationService actionRegistration;
    private ActionDefinition action;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        actionRegistration = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (actionRegistration == null) {
            throw new IllegalStateException("SKM is required; add depend: [SKM] to plugin.yml");
        }

        action = new ActionDefinition(
                getConfig().getString("action.id", "myskmplugin.ability"),
                getConfig().getString("action.display-name", "Example Ability"),
                getConfig().getString("action.default-key", "key.keyboard.g"),
                getConfig().getLong("action.cooldown-ms", 1000L),
                getConfig().getString("action.category", "MySKMPlugin"),
                getConfig().getBoolean("action.holdable", false),
                getConfig().getString("action.permission"),
                getConfig().getInt("action.required-level", 0)
        );

        // onEnable runs on Paper's main thread, as required by ActionRegistrationService.
        actionRegistration.register(this, action, this);
        getLogger().info("Registered SKM action " + action.id());
    }

    @Override
    public void onDisable() {
        if (actionRegistration != null) {
            // Explicit cleanup is good lifecycle hygiene; SKM also auto-cleans disabled owners.
            actionRegistration.unregisterAll(this);
        }
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition definition, InputState state) {
        if (action == null || !action.id().equals(definition.id())) {
            return ActionResult.DENIED;
        }

        // holdable=false normally delivers PRESS only. For holdable actions, handle RELEASE
        // separately and keep long-running work bounded/cleaned up when the player leaves.
        if (state == InputState.RELEASE) {
            return ActionResult.SUCCESS;
        }
        if (!player.isOnline() || player.isDead()) {
            return ActionResult.INVALID_TARGET;
        }

        // TODO: Re-check game-specific state on the server (target, range, resources, world,
        // inventory, etc.) and apply the real effect here. Never trust the client with game rules.
        player.sendMessage("MySKMPlugin: replace this message with your server-side ability.");
        return ActionResult.SUCCESS;
    }
}
