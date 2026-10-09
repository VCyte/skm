package io.github.skm.client;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.regex.Pattern;

/** Server-supplied display metadata; clients still send only the action ID and input state. */
public record ClientAction(String id, String name, String defaultKey, long cooldownMillis, String category, boolean holdable) {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern KEY = Pattern.compile("key\\.(keyboard|mouse)\\.[a-z0-9._-]{1,48}");

    static ClientAction fromSync(JsonObject object) {
        String id = ProtocolJson.string(object, "id", 64);
        String name = ProtocolJson.string(object, "name", 128);
        String key = ProtocolJson.string(object, "defaultKey", 64);
        long cooldown = ProtocolJson.longValue(object, "cooldownMs", 0L, 0L, 3_600_000L);
        String category = ProtocolJson.string(object, "category", 64);
        if (id == null || !ID.matcher(id).matches() || name == null || key == null
                || !KEY.matcher(key.toLowerCase(Locale.ROOT)).matches()) return null;
        boolean holdable = object.has("holdable") && object.get("holdable").isJsonPrimitive()
                && object.get("holdable").getAsBoolean();
        return new ClientAction(id, name, key.toLowerCase(Locale.ROOT), cooldown,
                category == null ? "misc" : category, holdable);
    }

    JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("name", name);
        object.addProperty("defaultKey", defaultKey);
        object.addProperty("cooldownMs", cooldownMillis);
        object.addProperty("category", category);
        object.addProperty("holdable", holdable);
        return object;
    }
}
