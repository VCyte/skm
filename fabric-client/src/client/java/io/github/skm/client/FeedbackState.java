package io.github.skm.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** Keeps local cooldown display state as a prediction; the server result always wins. */
public final class FeedbackState {
    private final Map<String, Long> cooldownEnds = new HashMap<>();
    private long lastNoticeAt;

    public void predict(ClientAction action) {
        if (action.cooldownMillis() > 0) {
            cooldownEnds.put(action.id(), System.currentTimeMillis() + action.cooldownMillis());
        }
    }

    public void accept(JsonObject payload) {
        String action = ProtocolJson.string(payload, "action", 64);
        String result = ProtocolJson.string(payload, "result", 32);
        long remaining = ProtocolJson.longValue(payload, "remainingMs", 0L, 0L, 3_600_000L);
        String reason = ProtocolJson.string(payload, "reason", 64);
        if (action == null || result == null) return;

        if ("success".equals(result)) return;
        if (remaining > 0) {
            cooldownEnds.put(action, System.currentTimeMillis() + remaining);
        } else {
            cooldownEnds.remove(action);
        }
        // Cooldowns remain enforced by the server and tracked locally, but are intentionally silent.
        if ("cooldown".equals(reason)) return;
        if (("denied".equals(result) || "no_resource".equals(result) || "invalid_target".equals(result))
                && System.currentTimeMillis() - lastNoticeAt > 500L) {
            lastNoticeAt = System.currentTimeMillis();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.literal("[SKM] 스킬 사용 불가"));
            }
        }
    }

    public void clear() {
        cooldownEnds.clear();
    }
}
