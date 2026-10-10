package io.github.skm.client;

/** Pins the server-provided namespace for the lifetime of one play connection. */
final class ConnectionSignatureGuard {
    private String acceptedSignature;

    boolean bindOrMatch(String candidate) {
        if (candidate == null || !candidate.matches("[a-z0-9][a-z0-9._-]{0,63}")) return false;
        if (acceptedSignature == null) {
            acceptedSignature = candidate;
            return true;
        }
        return acceptedSignature.equals(candidate);
    }

    void reset() {
        acceptedSignature = null;
    }
}
