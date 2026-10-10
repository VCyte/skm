package io.github.skm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ConnectionSignatureGuardTest {
    @Test
    void pinsFirstSignatureForConnection() {
        ConnectionSignatureGuard guard = new ConnectionSignatureGuard();

        assertTrue(guard.bindOrMatch("skm-main"));
        assertTrue(guard.bindOrMatch("skm-main"));
        assertFalse(guard.bindOrMatch("attacker-1"));
        assertFalse(guard.bindOrMatch("attacker-2"));
        assertTrue(guard.bindOrMatch("skm-main"));
    }

    @Test
    void rejectsMalformedSignatureWithoutPinningIt() {
        ConnectionSignatureGuard guard = new ConnectionSignatureGuard();

        assertFalse(guard.bindOrMatch("../../outside"));
        assertTrue(guard.bindOrMatch("skm-main"));
    }

    @Test
    void resetAllowsNextConnectionToUseAnotherSignature() {
        ConnectionSignatureGuard guard = new ConnectionSignatureGuard();

        assertTrue(guard.bindOrMatch("server-a"));
        guard.reset();
        assertTrue(guard.bindOrMatch("server-b"));
    }
}
