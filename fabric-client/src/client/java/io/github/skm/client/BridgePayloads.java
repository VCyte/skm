package io.github.skm.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Function;

/**
 * Typed Fabric payloads carrying the exact raw byte layout used by Bukkit/Paper plugin messaging.
 * Do not use ByteBufCodecs.byteArray here: it prepends a VarInt length that Paper's
 * Messenger API neither writes nor expects.
 */
public final class BridgePayloads {
    public static final int MAX_PAYLOAD_BYTES = 128 * 1024;

    private BridgePayloads() { }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Ack.TYPE, Ack.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Handshake.TYPE, Handshake.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Feedback.TYPE, Feedback.CODEC);
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> rawCodec(
            Function<T, byte[]> getter, Function<byte[], T> factory) {
        return StreamCodec.of((buffer, payload) -> {
            byte[] data = getter.apply(payload);
            if (data == null || data.length == 0 || data.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("SKM payload must contain 1.." + MAX_PAYLOAD_BYTES + " raw bytes");
            }
            buffer.writeBytes(data);
        }, buffer -> {
            int length = buffer.readableBytes();
            if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Invalid SKM raw payload length: " + length);
            }
            byte[] data = new byte[length];
            buffer.readBytes(data);
            return factory.apply(data);
        });
    }

    public record Hello(byte[] data) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(SKMClient.id("hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = rawCodec(Hello::data, Hello::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Ack(byte[] data) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = new Type<>(SKMClient.id("ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ack> CODEC = rawCodec(Ack::data, Ack::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Input(byte[] data) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(SKMClient.id("input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = rawCodec(Input::data, Input::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Handshake(byte[] data) implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = new Type<>(SKMClient.id("handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Handshake> CODEC = rawCodec(Handshake::data, Handshake::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(byte[] data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(SKMClient.id("sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = rawCodec(Sync::data, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Feedback(byte[] data) implements CustomPacketPayload {
        public static final Type<Feedback> TYPE = new Type<>(SKMClient.id("feedback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Feedback> CODEC = rawCodec(Feedback::data, Feedback::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
