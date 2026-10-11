package io.github.skm.client;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Keeps local cooldown display state as a prediction; the server result always wins. */
public final class FeedbackState {
    private static final int MAX_ENTRIES = 256;
    private final Map<String, Long> cooldownEnds = new HashMap<>();

    public void predict(ClientAction action) {
        long now = System.currentTimeMillis();
        prune(now);
        if (action.cooldownMillis() > 0 && cooldownEnds.size() < MAX_ENTRIES) {
            cooldownEnds.put(action.id(), now + action.cooldownMillis());
        }
    }

    public void accept(JsonObject payload, Predicate<String> knownAction) {
        String action = ProtocolJson.string(payload, "action", 64);
        String result = ProtocolJson.string(payload, "result", 32);
        long remaining = ProtocolJson.longValue(payload, "remainingMs", 0L, 0L, 3_600_000L);
        if (action == null || result == null || knownAction == null || !knownAction.test(action)) return;

        long now = System.currentTimeMillis();
        prune(now);
        if ("success".equals(result)) return;
        if (remaining > 0) {
            cooldownEnds.put(action, now + remaining);
        } else {
            cooldownEnds.remove(action);
        }
        // Server feedback stays silent; actionbar/chat notices are intentionally not shown.
    }

    private void prune(long now) {
        cooldownEnds.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (cooldownEnds.size() > MAX_ENTRIES) cooldownEnds.clear();
    }

    public void clear() {
        cooldownEnds.clear();
    }
}
