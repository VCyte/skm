package io.github.skm.server.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Strict bounded JSON parsing for untrusted plugin-message bytes. */
public final class JsonCodec {
    private static final int MAX_NESTING_DEPTH = 32;

    private JsonCodec() {
    }

    public static Optional<JsonObject> parseObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > Protocol.MAX_PAYLOAD_BYTES
                || !hasSafeNesting(bytes)) {
            return Optional.empty();
        }
        try {
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasSafeNesting(byte[] bytes) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (byte value : bytes) {
            int current = value & 0xff;
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                if (++depth > MAX_NESTING_DEPTH) return false;
            } else if (current == '}' || current == ']') {
                if (--depth < 0) return false;
            }
        }
        return !inString && depth == 0;
    }

    public static byte[] encode(JsonObject object) {
        byte[] data = object.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > Protocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload exceeds protocol limit");
        }
        return data;
    }

    public static String boundedString(JsonObject object, String field, int maxLength) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            return null;
        }
        try {
            String value = object.get(field).getAsString();
            return value.length() <= maxLength ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static int boundedInt(JsonObject object, String field, int fallback, int minimum, int maximum) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            int value = object.get(field).getAsInt();
            return value >= minimum && value <= maximum ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
