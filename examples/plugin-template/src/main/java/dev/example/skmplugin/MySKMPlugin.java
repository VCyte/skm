package dev.example.skmplugin;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import io.github.skm.api.PlayerActionEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Low-config Paper starter: config parsing, SKM lifecycle and admin commands are ready to use. */
public final class MySKMPlugin extends JavaPlugin implements ActionExecutionService, CommandExecutor, Listener {
    private static final String ADMIN_PERMISSION = "myskmplugin.admin";
    private static final Set<String> SUPPORTED_HANDLERS = Set.of("ability", "charge");

    private ActionManager actionManager;
    private Map<String, String> handlerByAction = Map.of();
    private final Set<UUID> chargingPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ActionRegistrationService registrationService = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (registrationService == null) {
            throw new IllegalStateException("SKM is required; add depend: [SKM] to plugin.yml");
        }
        actionManager = new ActionManager(this, registrationService, this);

        PluginCommand command = getCommand("myskmplugin");
        if (command == null) {
            actionManager.close();
            throw new IllegalStateException("myskmplugin command is missing from plugin.yml");
        }
        command.setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        try {
            int count = loadAndRegisterActions(loadConfigStrict());
            getLogger().info("Registered " + count + " configured SKM action(s).");
        } catch (RuntimeException exception) {
            actionManager.close();
            throw new IllegalStateException("Could not load SKM actions; check config.yml", exception);
        }
    }

    @Override
    public void onDisable() {
        clearHoldState();
        if (actionManager != null) {
            actionManager.close();
        }
        handlerByAction = Map.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("You do not have permission to manage this plugin.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("Usage: /myskmplugin <status|reload>");
            return true;
        }

        if ("status".equalsIgnoreCase(args[0])) {
            List<ActionDefinition> actions = actionManager.actions();
            sender.sendMessage("Active SKM actions: " + actions.size());
            for (ActionDefinition action : actions) {
                sender.sendMessage("- " + action.id() + " [handler=" + handlerByAction.get(action.id())
                        + ", key=" + action.defaultKey() + ", holdable=" + action.holdable() + "]");
            }
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            try {
                int count = loadAndRegisterActions(loadConfigStrict());
                sender.sendMessage("Reloaded " + count + " SKM action(s).");
            } catch (RuntimeException exception) {
                getLogger().warning("Action reload failed; previous registrations were restored when possible: "
                        + exception.getMessage());
                sender.sendMessage("Reload failed; see the server log. Previous actions were kept when possible.");
            }
            return true;
        }

        sender.sendMessage("Usage: /myskmplugin <status|reload>");
        return true;
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        String handler = handlerByAction.get(action.id());
        if (handler == null) return ActionResult.DENIED;

        return switch (handler) {
            case "ability" -> executeAbility(player, state);
            case "charge" -> executeCharge(player, state);
            default -> ActionResult.DENIED; // Add a route in this switch when adding a custom handler.
        };
    }

    /** Optional shared pre-handler hook; cancel here for game-wide conditions such as a locked arena. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAction(PlayerActionEvent event) {
        if (!actionManager.owns(event.getAction().id())) return;
        // TODO: Optional common checks. Keep action-specific validation in execute().
        // event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopCharge(event.getPlayer());
    }

    private int loadAndRegisterActions(FileConfiguration configuration) {
        List<ActionConfigLoader.ConfiguredAction> configured = ActionConfigLoader.load(configuration);
        for (ActionConfigLoader.ConfiguredAction item : configured) {
            if (!SUPPORTED_HANDLERS.contains(item.handler())) {
                throw new IllegalArgumentException("unknown handler route '" + item.handler()
                        + "' for " + item.definition().id() + "; implement it in execute() and SUPPORTED_HANDLERS");
            }
        }
        List<ActionDefinition> definitions = configured.stream()
                .map(ActionConfigLoader.ConfiguredAction::definition)
                .toList();
        Map<String, String> nextHandlers = new HashMap<>();
        configured.forEach(item -> nextHandlers.put(item.definition().id(), item.handler()));

        int count = actionManager.replace(definitions);
        clearHoldState();
        handlerByAction = Map.copyOf(nextHandlers);
        return count;
    }

    private FileConfiguration loadConfigStrict() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(new File(getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Could not parse config.yml: " + exception.getMessage(), exception);
        }
        return configuration;
    }

    private ActionResult executeAbility(Player player, InputState state) {
        if (state == InputState.RELEASE) return ActionResult.SUCCESS;
        if (!player.isOnline() || player.isDead()) return ActionResult.INVALID_TARGET;

        // TODO: Re-check game-specific target, range, resources, world and inventory on the server.
        player.sendMessage("MySKMPlugin: replace this message with your server-side ability.");
        return ActionResult.SUCCESS;
    }

    private ActionResult executeCharge(Player player, InputState state) {
        if (state == InputState.RELEASE) {
            stopCharge(player);
            return ActionResult.SUCCESS;
        }
        if (!player.isOnline() || player.isDead()) return ActionResult.INVALID_TARGET;

        if (!chargingPlayers.add(player.getUniqueId())) return ActionResult.DENIED;
        // TODO: Start hold work here. If you schedule a repeating task, store its BukkitTask by UUID.
        player.sendMessage("MySKMPlugin: hold started; add your server-side charge behavior here.");
        return ActionResult.SUCCESS;
    }

    private void stopCharge(Player player) {
        if (!chargingPlayers.remove(player.getUniqueId())) return;
        // TODO: Cancel this player's task and release any temporary resources/effects.
    }

    private void clearHoldState() {
        for (UUID playerId : Set.copyOf(chargingPlayers)) {
            Player player = getServer().getPlayer(playerId);
            if (player != null) {
                stopCharge(player);
            } else {
                chargingPlayers.remove(playerId);
            }
        }
    }
}
