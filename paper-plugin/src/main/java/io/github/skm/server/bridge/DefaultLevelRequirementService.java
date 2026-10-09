package io.github.skm.server.bridge;

import io.github.skm.api.*;

import org.bukkit.entity.Player;

/** Fails closed: a configured level requirement needs an explicit server-side integration. */
public final class DefaultLevelRequirementService implements LevelRequirementService {
    @Override
    public boolean meets(Player player, int requiredLevel) {
        return requiredLevel <= 0;
    }
}
