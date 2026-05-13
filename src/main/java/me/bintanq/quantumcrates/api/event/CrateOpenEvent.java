package me.bintanq.quantumcrates.api.event;

import me.bintanq.quantumcrates.api.dto.RewardSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a crate has been successfully opened and the reward rolled.
 * This event is informational and cannot be cancelled.
 *
 * @since 1.4.0
 */
public class CrateOpenEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String crateId;
    private final RewardSnapshot reward;
    private final boolean pityGuaranteed;
    private final int pityAtRoll;

    public CrateOpenEvent(@NotNull Player player, @NotNull String crateId,
                          @NotNull RewardSnapshot reward, boolean pityGuaranteed, int pityAtRoll) {
        super(player);
        this.crateId = crateId;
        this.reward = reward;
        this.pityGuaranteed = pityGuaranteed;
        this.pityAtRoll = pityAtRoll;
    }

    /** @return the crate that was opened */
    public @NotNull String getCrateId() { return crateId; }

    /** @return snapshot of the reward that was rolled */
    public @NotNull RewardSnapshot getReward() { return reward; }

    /** @return {@code true} if this reward was guaranteed by hard pity */
    public boolean isPityGuaranteed() { return pityGuaranteed; }

    /** @return the pity counter value at the time of the roll */
    public int getPityAtRoll() { return pityAtRoll; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
