package io.github.skm.api;

import org.bukkit.entity.Player;

/** Implement this service in a Paper plugin to handle validated SKM input. */
public interface ActionExecutionService {
    /**
     * Handles an action after SKM has checked the configured ID, permission, level, and cooldown.
     * Game-specific target, range, resource, and effect rules remain the consumer's responsibility.
     *
     * @param player player who pressed or released the bound key
     * @param action server-owned action definition
     * @param state whether the key was pressed or released
     * @return result sent back to the client; successful presses start the configured cooldown
     */
    ActionResult execute(Player player, ActionDefinition action, InputState state);

    /** Input edge delivered by SKM. */
    enum InputState {
        /** Key-down edge. */
        PRESS,
        /** Key-up edge; only sent for actions configured as holdable. */
        RELEASE;

        /** Parses a protocol state, defaulting non-release values to press.
         * @param value protocol state token
         * @return matching input state
         */
        public static InputState parse(String value) {
            return "release".equals(value) ? RELEASE : PRESS;
        }

        /** Returns the lowercase protocol representation.
         * @return {@code press} or {@code release}
         */
        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
