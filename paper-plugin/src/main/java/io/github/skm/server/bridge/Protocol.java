package io.github.skm.server.bridge;

/** Wire-level invariants shared by the Paper plugin and Fabric client. */
public final class Protocol {
    /** Version 4 uses the SKM channel namespace and raw Paper plugin-message JSON bytes. */
    public static final int VERSION = 4;
    public static final int MIN_CLIENT_VERSION = 4;
    public static final int MAX_PAYLOAD_BYTES = 128 * 1024;
    public static final int MAX_ACTIONS = 256;
    public static final String HELLO = "skm:hello";
    public static final String HANDSHAKE = "skm:handshake";
    public static final String SYNC = "skm:sync";
    public static final String INPUT = "skm:input";
    public static final String ACK = "skm:ack";
    public static final String FEEDBACK = "skm:feedback";

    private Protocol() {
    }
}
