package io.github.skm.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after SKM validation and before the selected action service executes. */
public final class PlayerActionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ActionDefinition action;
    private final ActionExecutionService.InputState state;
    private boolean cancelled;

    /** Creates an event for a validated action input.
     * @param player player who generated the input
     * @param action server-owned action definition
     * @param state press or release input
     */
    public PlayerActionEvent(Player player, ActionDefinition action, ActionExecutionService.InputState state) {
        this.player = player;
        this.action = action;
        this.state = state;
    }

    /**
     * Returns the player who generated the input.
     * @return player
     */
    public Player getPlayer() { return player; }
    /**
     * Returns the configured server action.
     * @return action definition
     */
    public ActionDefinition getAction() { return action; }
    /**
     * Returns whether this is a press or release input.
     * @return input state
     */
    public ActionExecutionService.InputState getState() { return state; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    /**
     * Returns Bukkit's handler list for this event class.
     * @return event handlers
     */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
