package io.github.skm.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class JsonNestingGuardTest {
    @Test
    void acceptsProtocolDepthAndIgnoresBracketsInsideStrings() {
        byte[] payload = "{\"actions\":[{\"name\":\"[{\\\"still text\\\"}]\"}]}".getBytes(StandardCharsets.UTF_8);

        assertTrue(JsonNestingGuard.isSafe(payload));
    }

    @Test
    void rejectsNestingBeyondProtocolLimit() {
        String payload = "[".repeat(JsonNestingGuard.MAX_DEPTH + 1)
                + "0" + "]".repeat(JsonNestingGuard.MAX_DEPTH + 1);

        assertFalse(JsonNestingGuard.isSafe(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsUnbalancedOrUnterminatedStructures() {
        assertFalse(JsonNestingGuard.isSafe("{}]".getBytes(StandardCharsets.UTF_8)));
        assertFalse(JsonNestingGuard.isSafe("{\"x\":\"unterminated}".getBytes(StandardCharsets.UTF_8)));
    }
}
