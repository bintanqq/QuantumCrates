package me.bintanq.quantumcrates.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a crate is opened (single or mass).
 * Cancel to prevent the opening entirely.
 *
 * @since 1.4.0
 */
public class CratePreOpenEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String crateId;
    private boolean cancelled;

    public CratePreOpenEvent(@NotNull Player player, @NotNull String crateId) {
        super(player);
        this.crateId = crateId;
    }

    /** @return the crate being opened */
    public @NotNull String getCrateId() { return crateId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
