package io.github.skm.server.command;

import io.github.skm.server.SKMPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class SKMCommand implements CommandExecutor, TabCompleter {
    private final SKMPlugin plugin;

    public SKMCommand(SKMPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("[SKM] signature=" + plugin.serverSignature() + ", revision=" + plugin.actionRevision()
                    + ", actions=" + plugin.actionCount());
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("skm.admin")) {
                sender.sendMessage("[SKM] 권한이 없습니다.");
                return true;
            }
            plugin.reloadActionsAndBroadcast("명령어");
            sender.sendMessage("[SKM] actions.yml 리로드를 요청했습니다. 콘솔 로그를 확인하세요.");
            return true;
        }
        sender.sendMessage("사용법: /skm <status|reload>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("status", "reload") : List.of();
    }
}
