package io.github.skm.client;

/** Rejects deeply nested JSON before a recursive parser can consume excessive stack. */
final class JsonNestingGuard {
    static final int MAX_DEPTH = 32;

    private JsonNestingGuard() { }

    static boolean isSafe(byte[] bytes) {
        if (bytes == null) return false;
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
                if (++depth > MAX_DEPTH) return false;
            } else if (current == '}' || current == ']') {
                if (--depth < 0) return false;
            }
        }
        return !inString && depth == 0;
    }
}
