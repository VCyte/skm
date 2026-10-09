package io.github.skm.server.bridge;

import io.github.skm.server.SKMPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/** Polls only file metadata on the main thread and atomically reloads a small YAML config. */
public final class ActionFileWatcher {
    private final SKMPlugin plugin;
    private final File file;
    private BukkitTask task;
    private long lastModified;

    public ActionFileWatcher(SKMPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.lastModified = file.lastModified();
    }

    public void start(long intervalTicks) {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long observed = file.lastModified();
            if (observed != lastModified) {
                lastModified = observed;
                plugin.reloadActionsAndBroadcast("파일 변경");
            }
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
