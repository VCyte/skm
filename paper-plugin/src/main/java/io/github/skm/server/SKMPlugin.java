package io.github.skm.server;

import io.github.skm.api.*;

import io.github.skm.server.bridge.ActionFileWatcher;
import io.github.skm.server.bridge.ActionRateLimiter;
import io.github.skm.server.bridge.ActionRegistry;
import io.github.skm.server.bridge.ActionRegistrationServiceImpl;
import io.github.skm.server.bridge.ActionRouter;
import io.github.skm.server.bridge.BridgeMessenger;
import io.github.skm.server.bridge.CooldownTracker;
import io.github.skm.server.bridge.DefaultActionExecutionService;
import io.github.skm.server.bridge.DefaultLevelRequirementService;
import io.github.skm.server.bridge.Protocol;
import io.github.skm.server.command.SKMCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public final class SKMPlugin extends JavaPlugin implements Listener {
    private ActionRegistry registry;
    private BridgeMessenger messenger;
    private ActionRouter router;
    private ActionRegistrationServiceImpl actionRegistrationService;
    private ActionFileWatcher watcher;
    private String serverSignature;
    private int maxActions;

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        saveDefaultConfig();
        saveResource("actions.yml", false);
        serverSignature = getConfig().getString("server-signature", "skm-main");
        if (serverSignature == null || !serverSignature.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalStateException("server-signature must use lowercase letters, digits, '.', '_' or '-'");
        }
        maxActions = getConfig().getInt("max-actions", 128);
        if (maxActions < 1 || maxActions > Protocol.MAX_ACTIONS) {
            throw new IllegalStateException("max-actions must be between 1 and " + Protocol.MAX_ACTIONS);
        }

        File actionsFile = new File(getDataFolder(), "actions.yml");
        registry = new ActionRegistry(actionsFile, maxActions);
        registry.reload();

        messenger = new BridgeMessenger(this, registry);
        CooldownTracker cooldowns = new CooldownTracker();
        ActionRateLimiter rateLimiter = new ActionRateLimiter();
        router = new ActionRouter(this, registry, cooldowns, rateLimiter, messenger);
        messenger.setRouter(router);
        messenger.register();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getServicesManager().register(ActionExecutionService.class, new DefaultActionExecutionService(), this, ServicePriority.Lowest);
        getServer().getServicesManager().register(LevelRequirementService.class, new DefaultLevelRequirementService(), this, ServicePriority.Lowest);
        actionRegistrationService = new ActionRegistrationServiceImpl(registry, messenger, cooldowns, rateLimiter);
        getServer().getServicesManager().register(ActionRegistrationService.class, actionRegistrationService, this, ServicePriority.Normal);
        SKMCommand command = new SKMCommand(this);
        Objects.requireNonNull(getCommand("skm"), "command missing from plugin.yml").setExecutor(command);
        Objects.requireNonNull(getCommand("skm"), "command missing from plugin.yml").setTabCompleter(command);

        if (getConfig().getBoolean("watch-actions-file", true)) {
            long interval = Math.max(20L, getConfig().getLong("watch-interval-ticks", 100L));
            watcher = new ActionFileWatcher(this, actionsFile);
            watcher.start(interval);
        }
        getLogger().info("Enabled for Paper 26.2 / Java 25: " + registry.all().size() + " actions (max " + maxActions + "), revision " + registry.revision());
    }

    @Override
    public void onDisable() {
        if (watcher != null) watcher.stop();
        if (actionRegistrationService != null) {
            actionRegistrationService.unregisterAll(this);
            getServer().getServicesManager().unregister(ActionRegistrationService.class, actionRegistrationService);
        }
        if (messenger != null) messenger.unregister();
    }

    private void migrateLegacyDataFolder() {
        File current = getDataFolder();
        File legacy = new File(current.getParentFile(), "RpgBridge");
        if (!legacy.isDirectory()) return;
        for (String name : new String[]{"config.yml", "actions.yml"}) {
            File source = new File(legacy, name);
            File destination = new File(current, name);
            if (!source.isFile() || destination.exists()) continue;
            try {
                Files.createDirectories(current.toPath());
                Files.copy(source.toPath(), destination.toPath());
                getLogger().info("Migrated existing " + name + " into the SKM data folder.");
            } catch (IOException exception) {
                throw new IllegalStateException("Could not migrate " + source + " to " + destination, exception);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (messenger != null) messenger.remove(event.getPlayer());
        if (router != null) router.remove(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (actionRegistrationService == null || event.getPlugin() == this) return;
        int removed = actionRegistrationService.unregisterAll(event.getPlugin());
        if (removed > 0) {
            getLogger().info("Removed " + removed + " runtime action(s) owned by disabled plugin "
                    + event.getPlugin().getName() + ".");
        }
    }

    public void reloadActionsAndBroadcast(String source) {
        try {
            boolean changed = registry.reload();
            if (changed) {
                messenger.broadcastUpdate();
                getLogger().info("actions.yml reloaded from " + source + "; revision=" + registry.revision());
            } else {
                getLogger().info("actions.yml checked from " + source + "; no semantic changes.");
            }
        } catch (RuntimeException exception) {
            getLogger().severe("actions.yml was rejected; previous valid registry remains active: " + exception.getMessage());
        }
    }

    public String serverSignature() { return serverSignature; }
    public String actionRevision() { return registry.revision(); }
    public int actionCount() { return registry.all().size(); }
    public int maxActions() { return maxActions; }
}
