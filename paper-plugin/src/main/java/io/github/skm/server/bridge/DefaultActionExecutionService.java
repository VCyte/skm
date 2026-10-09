package io.github.skm.server.bridge;

import io.github.skm.api.*;

import org.bukkit.entity.Player;

/** Safe no-op fallback: it validates the bridge without granting client authority. */
public final class DefaultActionExecutionService implements ActionExecutionService {
    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        return ActionResult.SUCCESS;
    }
}
