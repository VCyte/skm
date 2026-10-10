package tmin.click.skm.example;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionRegistrationService;
import io.github.skm.api.ActionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

/** Example consumer plugin using the published SKM API dependency. */
public final class FirstBroadcastPlugin extends JavaPlugin implements ActionExecutionService {
    private ActionRegistrationService actions;

    @Override
    public void onEnable() {
        actions = getServer().getServicesManager().load(ActionRegistrationService.class);
        if (actions == null) {
            throw new IllegalStateException("SKM ActionRegistrationService is not available");
        }
        actions.register(this, new ActionDefinition(
                "skill.first", "첫 인사", "key.keyboard.z", 1000, "general", false, null, 0
        ), this);
        getLogger().info("Registered runtime SKM action skill.first.");
    }

    @Override
    public void onDisable() {
        if (actions != null) actions.unregisterAll(this);
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if (!"skill.first".equals(action.id()) || state != InputState.PRESS) {
            return ActionResult.SUCCESS;
        }

        Bukkit.broadcast(Component.text("안녕하세요! " + player.getName() + " 님!"));
        return ActionResult.SUCCESS;
    }
}
