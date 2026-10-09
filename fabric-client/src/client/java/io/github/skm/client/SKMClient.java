package io.github.skm.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SKMClient implements ClientModInitializer {
    public static final String MOD_ID = "skm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ActionCache CACHE = new ActionCache();
    private static final FeedbackState FEEDBACK = new FeedbackState();
    private static final BridgeNetwork NETWORK = new BridgeNetwork(CACHE, FEEDBACK);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        BridgePayloads.register();
        ActionKeyBindings.initialize(NETWORK, FEEDBACK);
        NETWORK.registerReceivers();
        NETWORK.registerLifecycle();
        LOGGER.info("SKM client initialized for Minecraft 26.2 / Java 25");
    }
}
