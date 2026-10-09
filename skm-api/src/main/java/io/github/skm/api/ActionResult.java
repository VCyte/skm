package io.github.skm.api;

/** Result returned to SKM after an action handler runs. */
public enum ActionResult {
    /** Action was accepted and executed; a press starts the configured cooldown. */
    SUCCESS,
    /** The player lacks a game resource needed by the action. */
    NO_RESOURCE,
    /** The game-specific target validation failed. */
    INVALID_TARGET,
    /** The consumer rejected the action for another reason. */
    DENIED
}
