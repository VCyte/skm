package io.github.skm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PacketRateLimiterTest {
    @Test
    void allowsConfiguredBurstThenRefillsAtConfiguredRate() {
        PacketRateLimiter limiter = new PacketRateLimiter(2, 2.0, 0L);

        assertTrue(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(0L));
        assertFalse(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(500_000_000L));
        assertFalse(limiter.tryAcquire(500_000_000L));
        assertTrue(limiter.tryAcquire(1_000_000_000L));
    }

    @Test
    void resetRestoresFullBurst() {
        PacketRateLimiter limiter = new PacketRateLimiter(1, 1.0, 0L);

        assertTrue(limiter.tryAcquire(0L));
        assertFalse(limiter.tryAcquire(0L));
        limiter.reset(10L);
        assertTrue(limiter.tryAcquire(10L));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PacketRateLimiter(0, 1.0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PacketRateLimiter(1, 0.0, 0L));
    }
}
