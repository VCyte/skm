package tmin.click.skm.example;

import io.github.skm.api.ActionDefinition;
import io.github.skm.api.ActionExecutionService;
import io.github.skm.api.ActionResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Example handler using only the SKM 1.1.0 ActionExecutionService API. */
public final class ExampleWithSKMPlugin extends JavaPlugin implements ActionExecutionService {
    private static final String ACTION_ID = "examplewithskm.ping";

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
                ActionExecutionService.class,
                this,
                this,
                ServicePriority.Normal
        );
        getLogger().info("ExampleWithSKM registered its ActionExecutionService.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(ActionExecutionService.class, this);
    }

    @Override
    public ActionResult execute(Player player, ActionDefinition action, InputState state) {
        if (ACTION_ID.equals(action.id()) && state == InputState.PRESS) {
            player.sendMessage("ExampleWithSKM: SKM input received.");
        }
        return ActionResult.SUCCESS;
    }
}
