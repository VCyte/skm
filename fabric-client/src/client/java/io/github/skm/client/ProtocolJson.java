package io.github.skm.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class ProtocolJson {
    private ProtocolJson() {
    }

    static Optional<JsonObject> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > BridgePayloads.MAX_PAYLOAD_BYTES) {
            return Optional.empty();
        }
        try {
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static byte[] encode(JsonObject object) {
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BridgePayloads.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("bridge payload exceeds limit");
        }
        return bytes;
    }

    static String string(JsonObject object, String field, int maxLength) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) return null;
        try {
            String value = object.get(field).getAsString();
            return value.length() <= maxLength ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static int integer(JsonObject object, String field, int fallback, int min, int max) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) return fallback;
        try {
            int value = object.get(field).getAsInt();
            return value >= min && value <= max ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static long longValue(JsonObject object, String field, long fallback, long min, long max) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) return fallback;
        try {
            long value = object.get(field).getAsLong();
            return value >= min && value <= max ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
