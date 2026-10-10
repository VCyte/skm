package io.github.skm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Client treats every server-supplied packet as untrusted and sends only action ID + input intent. */
public final class BridgeNetwork {
    private static final int CLIENT_PROTOCOL = 4;
    private static final int MAX_ACTIONS = 256;
    private static final String REVISION_PATTERN = "[a-f0-9]{16}";

    private final ActionCache cache;
    private final FeedbackState feedback;
    private final ConnectionSignatureGuard signatureGuard = new ConnectionSignatureGuard();
    // High burst allowances preserve existing v4 plugins that register actions during onEnable;
    // sustained traffic is bounded, and the current Paper implementation coalesces updates per tick.
    private final PacketRateLimiter handshakeLimit = new PacketRateLimiter(256, 32.0);
    private final PacketRateLimiter syncLimit = new PacketRateLimiter(128, 24.0);
    private final PacketRateLimiter feedbackLimit = new PacketRateLimiter(128, 64.0);
    private final AtomicBoolean rateLimitWarningShown = new AtomicBoolean();

    private boolean active;
    private String signature = "";
    private String revision = "";
    private String appliedRevision = "";
    private int maxActions = 128;
    private boolean awaitingHandshake;
    private boolean signatureMismatchWarningShown;
    private int helloTicks;
    private int helloAttempts;

    BridgeNetwork(ActionCache cache, FeedbackState feedback) {
        this.cache = cache;
        this.feedback = feedback;
    }

    void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Handshake.TYPE, (payload, context) ->
                receive(payload.data(), handshakeLimit, context.client(), this::handleHandshake));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Sync.TYPE, (payload, context) ->
                receive(payload.data(), syncLimit, context.client(), this::handleSync));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Feedback.TYPE, (payload, context) ->
                receive(payload.data(), feedbackLimit, context.client(), feedback::accept));
    }

    private void receive(byte[] data, PacketRateLimiter limiter, Minecraft client,
                         Consumer<JsonObject> handler) {
        if (!limiter.tryAcquire()) {
            if (rateLimitWarningShown.compareAndSet(false, true)) {
                SKMClient.LOGGER.warn("Excessive SKM server packets; limiting further protocol work for this connection.");
            }
            return;
        }
        ProtocolJson.parse(data).ifPresent(json -> client.execute(() -> handler.accept(json)));
    }

    void registerLifecycle() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::beginConnection));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::disconnect));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!awaitingHandshake || client.player == null) return;
            if (helloTicks-- > 0) return;
            if (helloAttempts >= 5) {
                awaitingHandshake = false;
                SKMClient.LOGGER.warn("No SKM server handshake after 5 hello attempts. Ensure the SKM Paper plugin is installed and client/server versions match.");
                return;
            }
            sendHelloAttempt();
        });
    }

    boolean isActive() { return active; }

    void sendInput(String actionId, String state) {
        if (!active || actionId == null || !("press".equals(state) || "release".equals(state))) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", actionId);
        payload.addProperty("state", state);
        try {
            ClientPlayNetworking.send(new BridgePayloads.Input(ProtocolJson.encode(payload)));
        } catch (IllegalStateException ignored) {
            disconnect();
        }
    }

    private void beginConnection() {
        active = false;
        ActionKeyBindings.clearServerActions();
        signature = "";
        revision = "";
        appliedRevision = "";
        signatureGuard.reset();
        signatureMismatchWarningShown = false;
        rateLimitWarningShown.set(false);
        handshakeLimit.reset();
        syncLimit.reset();
        feedbackLimit.reset();
        feedback.clear();
        awaitingHandshake = true;
        helloAttempts = 0;
        helloTicks = 20;
        SKMClient.LOGGER.info("Connected to server; waiting to send SKM handshake.");
    }

    private void sendHelloAttempt() {
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", CLIENT_PROTOCOL);
        try {
            ClientPlayNetworking.send(new BridgePayloads.Hello(ProtocolJson.encode(payload)));
            helloAttempts++;
            helloTicks = 40;
            SKMClient.LOGGER.info("Sent SKM hello (protocol {}, attempt {}/5).", CLIENT_PROTOCOL, helloAttempts);
        } catch (IllegalStateException ignored) {
            helloAttempts++;
            helloTicks = 40;
            SKMClient.LOGGER.debug("Could not send SKM hello yet (attempt {}/5).", helloAttempts);
        }
    }

    private void handleHandshake(JsonObject payload) {
        if (!awaitingHandshake && !active) return;

        int serverProtocol = ProtocolJson.integer(payload, "protocol", 0, 0, 10_000);
        int minimumClient = ProtocolJson.integer(payload, "minClient", Integer.MAX_VALUE, 1, 10_000);
        String nextSignature = ProtocolJson.string(payload, "signature", 64);
        String nextRevision = ProtocolJson.string(payload, "revision", 64);
        int nextMaxActions = ProtocolJson.integer(payload, "maxActions", 0, 1, MAX_ACTIONS);
        boolean valid = nextSignature != null && nextRevision != null && nextRevision.matches(REVISION_PATTERN)
                && serverProtocol == CLIENT_PROTOCOL && CLIENT_PROTOCOL >= minimumClient && nextMaxActions > 0;
        if (!valid) {
            // A bad update after a valid handshake must not let another plugin disable the live session.
            if (awaitingHandshake && !active) disconnect();
            return;
        }

        if (!signatureGuard.bindOrMatch(nextSignature)) {
            if (!signatureMismatchWarningShown) {
                signatureMismatchWarningShown = true;
                SKMClient.LOGGER.warn("Rejected an SKM server-signature change during one connection; the first valid signature remains active.");
            }
            return;
        }

        boolean initialHandshake = !active;
        if (!initialHandshake && revision.equals(nextRevision) && maxActions == nextMaxActions) return;
        if (!initialHandshake && !awaitingHandshake && !signature.equals(nextSignature)) return;

        boolean revisionChanged = !revision.equals(nextRevision) || maxActions != nextMaxActions;
        if (revisionChanged) appliedRevision = "";
        awaitingHandshake = false;
        signature = nextSignature;
        revision = nextRevision;
        maxActions = nextMaxActions;
        active = true;

        if (initialHandshake) {
            SKMClient.LOGGER.info("Received SKM handshake from signature '{}' (protocol {}, revision {}).", nextSignature, serverProtocol, nextRevision);
        } else {
            SKMClient.LOGGER.debug("Accepted SKM action-list revision update for '{}'.", nextSignature);
        }

        if (cache.matches(signature, revision) && cache.actions().size() <= maxActions) {
            ActionKeyBindings.apply(signature, cache.actions());
            appliedRevision = revision;
            if (initialHandshake) sendAck("cache-hit");
        } else {
            ActionKeyBindings.clearServerActions();
            appliedRevision = "";
            // Initial handshakes need a client request. Revision updates are sent with a sync
            // immediately after the handshake, so requesting again would duplicate the full snapshot.
            if (initialHandshake) sendAck("sync-request");
        }
    }

    private void handleSync(JsonObject payload) {
        if (!active) return;
        String receivedRevision = ProtocolJson.string(payload, "revision", 64);
        int receivedMaxActions = ProtocolJson.integer(payload, "maxActions", 0, 1, MAX_ACTIONS);
        JsonArray array = payload.has("actions") && payload.get("actions").isJsonArray() ? payload.getAsJsonArray("actions") : null;
        if (receivedRevision == null || !receivedRevision.matches(REVISION_PATTERN)
                || !revision.equals(receivedRevision) || receivedMaxActions == 0 || receivedMaxActions > maxActions
                || array == null || array.size() > receivedMaxActions || revision.equals(appliedRevision)) return;

        List<ClientAction> actions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) return;
            ClientAction action = ClientAction.fromSync(element.getAsJsonObject());
            if (action == null || !ids.add(action.id())) return;
            actions.add(action);
        }
        ActionKeyBindings.apply(signature, actions);
        cache.update(signature, revision, actions);
        appliedRevision = revision;
        SKMClient.LOGGER.info("Synchronized {} SKM action(s) from server '{}'.", actions.size(), signature);
    }

    private void sendAck(String type) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        try {
            ClientPlayNetworking.send(new BridgePayloads.Ack(ProtocolJson.encode(payload)));
        } catch (IllegalStateException ignored) {
            disconnect();
        }
    }

    private void disconnect() {
        active = false;
        awaitingHandshake = false;
        helloTicks = 0;
        helloAttempts = 0;
        signature = "";
        revision = "";
        appliedRevision = "";
        signatureGuard.reset();
        signatureMismatchWarningShown = false;
        rateLimitWarningShown.set(false);
        handshakeLimit.reset();
        syncLimit.reset();
        feedbackLimit.reset();
        ActionKeyBindings.clearServerActions();
        feedback.clear();
    }
}
