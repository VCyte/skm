package tmin.click.skm.example;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

/** Example consumer plugin using the published SKM API dependency. */
public final class FirstBroadcastPlugin extends JavaPlugin implements ActionExecutionService {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
                ActionExecutionService.class,
                this,
                this,
                ServicePriority.Normal
        );
        getLogger().info("Registered SKM ActionExecutionService.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(ActionExecutionService.class, this);
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
