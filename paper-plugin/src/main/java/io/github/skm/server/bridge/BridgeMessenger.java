package io.github.skm.server.bridge;

import io.github.skm.api.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Registers a bounded, versioned plugin-message protocol. */
public final class BridgeMessenger implements PluginMessageListener {
    private final io.github.skm.server.SKMPlugin plugin;
    private final ActionRegistry registry;
    private final Map<UUID, ClientSession> sessions = new HashMap<>();
    private ActionRouter router;

    public BridgeMessenger(io.github.skm.server.SKMPlugin plugin, ActionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void setRouter(ActionRouter router) {
        this.router = router;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, Protocol.HELLO, this);
        messenger.registerIncomingPluginChannel(plugin, Protocol.ACK, this);
        messenger.registerIncomingPluginChannel(plugin, Protocol.INPUT, this);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.HANDSHAKE);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.SYNC);
        messenger.registerOutgoingPluginChannel(plugin, Protocol.FEEDBACK);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        sessions.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        JsonCodec.parseObject(message).ifPresent(payload -> {
            if (Protocol.HELLO.equals(channel)) {
                handleHello(player, payload);
            } else if (Protocol.ACK.equals(channel)) {
                handleAck(player, payload);
            } else if (Protocol.INPUT.equals(channel)) {
                handleInput(player, payload);
            }
        });
    }

    public void sendHandshake(Player player) {
        ClientSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.compatible()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", Protocol.VERSION);
        payload.addProperty("minClient", Protocol.MIN_CLIENT_VERSION);
        payload.addProperty("signature", plugin.serverSignature());
        payload.addProperty("revision", registry.revision());
        payload.addProperty("maxActions", plugin.maxActions());
        send(player, Protocol.HANDSHAKE, payload);
    }

    public void sendSync(Player player) {
        ClientSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.compatible()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("revision", registry.revision());
        payload.addProperty("maxActions", plugin.maxActions());
        JsonArray actions = new JsonArray();
        registry.all().forEach(action -> actions.add(ActionJsonCodec.toSyncJson(action)));
        payload.add("actions", actions);
        send(player, Protocol.SYNC, payload);
    }

    public void broadcastUpdate() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sessions.containsKey(player.getUniqueId())) {
                sendHandshake(player);
                sendSync(player);
            }
        }
    }

    public void sendFeedback(Player player, String actionId, ActionResult result, long remainingMillis, String reason) {
        if (!isCompatible(player)) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("action", actionId);
        payload.addProperty("result", result.name().toLowerCase());
        payload.addProperty("remainingMs", Math.max(0L, remainingMillis));
        if (reason != null && !reason.isBlank()) {
            payload.addProperty("reason", reason);
        }
        send(player, Protocol.FEEDBACK, payload);
    }

    public boolean isCompatible(Player player) {
        ClientSession session = sessions.get(player.getUniqueId());
        return session != null && session.compatible();
    }

    public void remove(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private void handleHello(Player player, JsonObject payload) {
        int protocol = JsonCodec.boundedInt(payload, "protocol", 0, 0, 10_000);
        boolean compatible = protocol >= Protocol.MIN_CLIENT_VERSION;
        sessions.put(player.getUniqueId(), new ClientSession(protocol, compatible));
        plugin.getLogger().info("Received SKM hello from " + player.getName() + " (client protocol " + protocol + ", compatible=" + compatible + ").");
        if (compatible) {
            sendHandshake(player);
        } else {
            sendFeedback(player, "", ActionResult.DENIED, 0, "client_protocol_too_old");
        }
    }

    private void handleAck(Player player, JsonObject payload) {
        if (!isCompatible(player)) {
            return;
        }
        String type = JsonCodec.boundedString(payload, "type", 32);
        if ("sync-request".equals(type)) {
            String actionIds = registry.all().stream().map(ActionDefinition::id).sorted().limit(10)
                    .collect(Collectors.joining(", "));
            String more = registry.all().size() > 10 ? " (and " + (registry.all().size() - 10) + " more)" : "";
            plugin.getLogger().info("Sending " + registry.all().size() + " SKM action(s) to " + player.getName()
                    + " (signature " + plugin.serverSignature() + ", ids: " + actionIds + more + ").");
            sendSync(player);
        }
    }

    private void handleInput(Player player, JsonObject payload) {
        if (!isCompatible(player) || router == null) {
            return;
        }
        String actionId = JsonCodec.boundedString(payload, "action", 64);
        String state = JsonCodec.boundedString(payload, "state", 16);
        if (actionId == null || !("press".equals(state) || "release".equals(state))) {
            return;
        }
        router.dispatch(player, actionId, ActionExecutionService.InputState.parse(state));
    }

    private void send(Player player, String channel, JsonObject payload) {
        try {
            player.sendPluginMessage(plugin, channel, JsonCodec.encode(payload));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipped oversized " + channel + " payload for " + player.getName());
        }
    }

    private record ClientSession(int protocol, boolean compatible) {
    }
}
