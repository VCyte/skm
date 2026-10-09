package io.github.skm.api;

import org.bukkit.entity.Player;

/** Optional integration contract for a server-side game progression plugin. */
public interface LevelRequirementService {
    /**
     * Checks whether a player satisfies the action's configured required level.
     *
     * @param player player attempting the action
     * @param requiredLevel configured minimum level
     * @return true when the requirement is met
     */
    boolean meets(Player player, int requiredLevel);
}
