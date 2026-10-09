package io.github.skm.server.bridge;

import com.google.gson.JsonObject;
import io.github.skm.api.ActionDefinition;

/** Keeps protocol serialization inside the SKM server implementation, not in the public API. */
final class ActionJsonCodec {
    private ActionJsonCodec() { }

    static JsonObject toSyncJson(ActionDefinition action) {
        JsonObject object = new JsonObject();
        object.addProperty("id", action.id());
        object.addProperty("name", action.displayName());
        object.addProperty("defaultKey", action.defaultKey());
        object.addProperty("cooldownMs", action.cooldownMillis());
        object.addProperty("category", action.category());
        object.addProperty("holdable", action.holdable());
        return object;
    }
}
