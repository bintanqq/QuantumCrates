package me.bintanq.quantumcrates.api.event;

import me.bintanq.quantumcrates.api.dto.RewardSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a reward is about to be delivered to a player.
 * Cancel to prevent delivery (the reward is still "rolled" but not given).
 *
 * @since 1.4.0
 */
public class CrateRewardEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String crateId;
    private final RewardSnapshot reward;
    private boolean cancelled;

    public CrateRewardEvent(@NotNull Player player, @NotNull String crateId,
                            @NotNull RewardSnapshot reward) {
        super(player);
        this.crateId = crateId;
        this.reward = reward;
    }

    /** @return the crate identifier */
    public @NotNull String getCrateId() { return crateId; }

    /** @return snapshot of the reward being delivered */
    public @NotNull RewardSnapshot getReward() { return reward; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
