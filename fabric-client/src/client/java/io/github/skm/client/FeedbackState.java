package io.github.skm.client;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/** Keeps local cooldown display state as a prediction; the server result always wins. */
public final class FeedbackState {
    private final Map<String, Long> cooldownEnds = new HashMap<>();

    public void predict(ClientAction action) {
        if (action.cooldownMillis() > 0) {
            cooldownEnds.put(action.id(), System.currentTimeMillis() + action.cooldownMillis());
        }
    }

    public void accept(JsonObject payload) {
        String action = ProtocolJson.string(payload, "action", 64);
        String result = ProtocolJson.string(payload, "result", 32);
        long remaining = ProtocolJson.longValue(payload, "remainingMs", 0L, 0L, 3_600_000L);
        if (action == null || result == null) return;

        if ("success".equals(result)) return;
        if (remaining > 0) {
            cooldownEnds.put(action, System.currentTimeMillis() + remaining);
        } else {
            cooldownEnds.remove(action);
        }
        // Server feedback stays silent; actionbar/chat notices are intentionally not shown.
    }

    public void clear() {
        cooldownEnds.clear();
    }
}
