package io.github.skm.client;

/** A small synchronized token bucket for bounding untrusted per-connection packets. */
final class PacketRateLimiter {
    private final int burst;
    private final double tokensPerNanosecond;
    private double tokens;
    private long lastRefillNanos;

    PacketRateLimiter(int burst, double tokensPerSecond) {
        this(burst, tokensPerSecond, System.nanoTime());
    }

    PacketRateLimiter(int burst, double tokensPerSecond, long initialNanos) {
        if (burst < 1 || !Double.isFinite(tokensPerSecond) || tokensPerSecond <= 0) {
            throw new IllegalArgumentException("burst and refill rate must be positive");
        }
        this.burst = burst;
        this.tokensPerNanosecond = tokensPerSecond / 1_000_000_000.0;
        this.tokens = burst;
        this.lastRefillNanos = initialNanos;
    }

    synchronized boolean tryAcquire() {
        return tryAcquire(System.nanoTime());
    }

    synchronized boolean tryAcquire(long nowNanos) {
        if (nowNanos > lastRefillNanos) {
            tokens = Math.min(burst, tokens + (nowNanos - lastRefillNanos) * tokensPerNanosecond);
            lastRefillNanos = nowNanos;
        }
        if (tokens < 1.0) return false;
        tokens -= 1.0;
        return true;
    }

    synchronized void reset() {
        reset(System.nanoTime());
    }

    synchronized void reset(long nowNanos) {
        tokens = burst;
        lastRefillNanos = nowNanos;
    }
}
