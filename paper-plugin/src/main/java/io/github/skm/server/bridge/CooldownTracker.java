package io.github.skm.server.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses only the server monotonic clock; client clocks are never considered. */
public final class CooldownTracker {
    private final Map<UUID, Map<String, Long>> untilByPlayer = new HashMap<>();

    public long remaining(UUID playerId, String actionId, long nowMillis) {
        long until = untilByPlayer.getOrDefault(playerId, Map.of()).getOrDefault(actionId, 0L);
        return Math.max(0L, until - nowMillis);
    }

    public void start(UUID playerId, String actionId, long cooldownMillis, long nowMillis) {
        if (cooldownMillis <= 0) {
            return;
        }
        untilByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(actionId, nowMillis + cooldownMillis);
    }

    public void remove(UUID playerId) {
        untilByPlayer.remove(playerId);
    }

    public void removeAction(String actionId) {
        untilByPlayer.values().forEach(cooldowns -> cooldowns.remove(actionId));
        untilByPlayer.values().removeIf(Map::isEmpty);
    }
}
