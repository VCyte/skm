package io.github.skm.server.bridge;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sliding-window limiter that drops floods before any gameplay action runs. */
public final class ActionRateLimiter {
    private static final long WINDOW_MILLIS = 1_000L;
    private static final int MAX_INPUTS_PER_SECOND = 20;
    private final Map<UUID, Map<String, ArrayDeque<Long>>> samples = new HashMap<>();

    public boolean allow(UUID playerId, String actionId, long nowMillis) {
        ArrayDeque<Long> timestamps = samples
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(actionId, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && nowMillis - timestamps.peekFirst() >= WINDOW_MILLIS) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= MAX_INPUTS_PER_SECOND) {
            return false;
        }
        timestamps.addLast(nowMillis);
        return true;
    }

    public void remove(UUID playerId) {
        samples.remove(playerId);
    }

    public void removeAction(String actionId) {
        samples.values().forEach(actions -> actions.remove(actionId));
        samples.values().removeIf(Map::isEmpty);
    }
}
