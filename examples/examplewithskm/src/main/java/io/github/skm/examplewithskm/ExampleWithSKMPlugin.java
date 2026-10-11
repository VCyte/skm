package io.github.skm.examplewithskm;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Demonstrates plugin-owned SKM key registration, persistence and removal at runtime. */
public final class ExampleWithSKMPlugin extends JavaPlugin implements ActionExecutionService, CommandExecutor {
    private static final String ACTION_ID = "examplewithskm.ping";
    private static final String ENABLED_PATH = "action.enabled";
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
        saveDefaultConfig();
        actions = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (actions == null) {
            throw new IllegalStateException("SKM ActionRegistrationService is not available");
        }

        PluginCommand command = getCommand("examplewithskm");
        if (command == null) {
            throw new IllegalStateException("examplewithskm command is missing from plugin.yml");
        }
        command.setExecutor(this);

        if (getConfig().getBoolean(ENABLED_PATH, false)) {
            String error = registerAction();
            if (error == null) {
                getLogger().info("Restored saved runtime action " + ACTION_ID + ".");
            } else {
                // Keep the desired state so a later restart can retry after the conflict is fixed.
                getLogger().warning("Could not restore " + ACTION_ID + ": " + error);
            }
        }
        getLogger().info("ExampleWithSKM enabled. Use /examplewithskm add|remove to manage its key.");
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
            String error = registerAction();
            if (error != null) {
                sender.sendMessage("Could not register ExampleWithSKM key: " + error);
                return true;
            }
            saveDesiredState(true);
            sender.sendMessage("ExampleWithSKM key added. It will be restored after restart.");
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            boolean removed = actions.unregister(this, ACTION_ID);
            boolean desiredEnabled = getConfig().getBoolean(ENABLED_PATH, false);
            if (!removed && !desiredEnabled) {
                sender.sendMessage(registered
                        ? "Could not remove the ExampleWithSKM key."
                        : "ExampleWithSKM key is not registered.");
                return true;
            }

            registered = false;
            saveDesiredState(false);
            sender.sendMessage(removed
                    ? "ExampleWithSKM key removed; it will stay disabled after restart."
                    : "Saved enable state cleared; the key was not active.");
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

    private String registerAction() {
        try {
            actions.register(this, ACTION, this);
            registered = true;
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    private void saveDesiredState(boolean enabled) {
        getConfig().set(ENABLED_PATH, enabled);
        saveConfig();
    }
}
