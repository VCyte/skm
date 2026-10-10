package io.github.skm.examplewithskm;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Demonstrates plugin-owned SKM key registration and removal at runtime. */
public final class ExampleWithSKMPlugin extends JavaPlugin implements ActionExecutionService, CommandExecutor {
    private static final String ACTION_ID = "examplewithskm.ping";
    private static final ActionDefinition ACTION = new ActionDefinition(
            ACTION_ID,
            "ExampleWithSKM Ping",
            "key.keyboard.j",
            750,
            "ExampleWithSKM",
            false,
            null,
            0
    );

    private ActionRegistrationService actions;
    private boolean registered;

    @Override
    public void onEnable() {
        actions = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (actions == null) {
            throw new IllegalStateException("SKM ActionRegistrationService is not available");
        }

        if (getCommand("examplewithskm") == null) {
            throw new IllegalStateException("examplewithskm command is missing from plugin.yml");
        }
        getCommand("examplewithskm").setExecutor(this);
        getLogger().info("ExampleWithSKM enabled. Use /examplewithskm add to register its key.");
    }

    @Override
    public void onDisable() {
        if (actions != null) {
            actions.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /examplewithskm <add|remove>");
            return true;
        }

        if ("add".equalsIgnoreCase(args[0])) {
            if (registered) {
                sender.sendMessage("ExampleWithSKM key is already registered.");
                return true;
            }
            try {
                actions.register(this, ACTION, this);
                registered = true;
                sender.sendMessage("ExampleWithSKM key added for connected SKM clients.");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage("Could not register ExampleWithSKM key: " + exception.getMessage());
            }
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            if (!registered) {
                sender.sendMessage("ExampleWithSKM key is not registered.");
                return true;
            }
            registered = !actions.unregister(this, ACTION_ID);
            sender.sendMessage(registered
                    ? "Could not remove the ExampleWithSKM key."
                    : "ExampleWithSKM key removed from connected SKM clients.");
            return true;
        }

        sender.sendMessage("Usage: /examplewithskm <add|remove>");
        return true;
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if (ACTION_ID.equals(action.id()) && state == InputState.PRESS) {
            // Replace this private confirmation with server-side gameplay logic in a real plugin.
            player.sendMessage("ExampleWithSKM: SKM input received.");
        }
        return ActionResult.SUCCESS;
    }
}
