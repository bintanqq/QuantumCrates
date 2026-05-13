package me.bintanq.quantumcrates.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when keys are about to be taken from a player via the API.
 * Cancel to prevent the key removal.
 *
 * @since 1.4.0
 */
public class CrateKeyTakeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String keyId;
    private int amount;
    private boolean cancelled;

    public CrateKeyTakeEvent(@NotNull Player player, @NotNull String keyId, int amount) {
        super(player);
        this.keyId = keyId;
        this.amount = amount;
    }

    /** @return the key identifier */
    public @NotNull String getKeyId() { return keyId; }

    /** @return the number of keys being taken */
    public int getAmount() { return amount; }

    /**
     * Modifies the amount of keys to take.
     *
     * @param amount the new amount (must be positive)
     */
    public void setAmount(int amount) { this.amount = Math.max(1, amount); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
