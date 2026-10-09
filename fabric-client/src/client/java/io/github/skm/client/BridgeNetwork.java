package io.github.skm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client trusts only the action list from the current server signature and sends action ID + intent. */
public final class BridgeNetwork {
    private static final int CLIENT_PROTOCOL = 4;
    private static final int MAX_ACTIONS = 256;
    private final ActionCache cache;
    private final FeedbackState feedback;
    private boolean active;
    private String signature = "";
    private String revision = "";
    private int maxActions = 128;
    private boolean awaitingHandshake;
    private int helloTicks;
    private int helloAttempts;

    BridgeNetwork(ActionCache cache, FeedbackState feedback) {
        this.cache = cache;
        this.feedback = feedback;
    }

    void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Handshake.TYPE, (payload, context) ->
                ProtocolJson.parse(payload.data()).ifPresent(json -> context.client().execute(() -> handleHandshake(json))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Sync.TYPE, (payload, context) ->
                ProtocolJson.parse(payload.data()).ifPresent(json -> context.client().execute(() -> handleSync(json))));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Feedback.TYPE, (payload, context) ->
                ProtocolJson.parse(payload.data()).ifPresent(json -> context.client().execute(() -> feedback.accept(json))));
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
        int serverProtocol = ProtocolJson.integer(payload, "protocol", 0, 0, 10_000);
        int minimumClient = ProtocolJson.integer(payload, "minClient", Integer.MAX_VALUE, 1, 10_000);
        String nextSignature = ProtocolJson.string(payload, "signature", 64);
        String nextRevision = ProtocolJson.string(payload, "revision", 64);
        int nextMaxActions = ProtocolJson.integer(payload, "maxActions", 0, 1, MAX_ACTIONS);
        if (nextSignature == null || nextRevision == null || !nextSignature.matches("[a-z0-9][a-z0-9._-]{0,63}")
                || CLIENT_PROTOCOL < minimumClient || serverProtocol != CLIENT_PROTOCOL || nextMaxActions == 0) {
            disconnect();
            return;
        }
        awaitingHandshake = false;
        SKMClient.LOGGER.info("Received SKM handshake from signature '{}' (protocol {}, revision {}).", nextSignature, serverProtocol, nextRevision);
        if (!signature.equals(nextSignature)) {
            active = false;
            ActionKeyBindings.clearServerActions();
        }
        signature = nextSignature;
        revision = nextRevision;
        maxActions = nextMaxActions;
        active = true;
        if (cache.matches(signature, revision) && cache.actions().size() <= maxActions) {
            ActionKeyBindings.apply(signature, cache.actions());
            sendAck("cache-hit");
        } else {
            ActionKeyBindings.clearServerActions();
            sendAck("sync-request");
        }
    }

    private void handleSync(JsonObject payload) {
        if (!active) return;
        String receivedRevision = ProtocolJson.string(payload, "revision", 64);
        int receivedMaxActions = ProtocolJson.integer(payload, "maxActions", 0, 1, MAX_ACTIONS);
        JsonArray array = payload.has("actions") && payload.get("actions").isJsonArray() ? payload.getAsJsonArray("actions") : null;
        if (!revision.equals(receivedRevision) || receivedMaxActions == 0 || receivedMaxActions > maxActions
                || array == null || array.size() > receivedMaxActions) return;

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
        ActionKeyBindings.clearServerActions();
        feedback.clear();
    }
}
