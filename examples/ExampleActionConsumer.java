package io.github.skm.server.example;

import io.github.skm.api.*;

import org.bukkit.entity.Player;

/**
 * Compile this class in a separate Paper plugin with the SKM server API as compileOnly.
 * It shows where real server-authoritative game logic belongs.
 */
public final class ExampleActionConsumer implements ActionExecutionService {
    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if ("skill.test".equals(action.id()) && state == InputState.PRESS) {
            // Validate target/range/resources here, then perform server-side gameplay effects.
            player.sendMessage("[SKM] 서버가 테스트 입력을 승인했습니다.");
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }
}
