package me.bintanq.quantumcrates.animation;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.impl.*;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimationManager {

    private final QuantumCrates plugin;
    private final Map<UUID, CrateSession> sessions = new ConcurrentHashMap<>();

    public AnimationManager(QuantumCrates plugin) {
        this.plugin = plugin;
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public CrateSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void startAnimation(Player player, Crate crate, RewardResult result) {
        if (sessions.containsKey(player.getUniqueId())) {
            Logger.warn("Player " + player.getName() + " already has an active crate session.");
            return;
        }
        CrateSession session = new CrateSession(player, crate, result);
        sessions.put(player.getUniqueId(), session);
        session.setRunning(true);
        resolveAnimation(crate).start(session);
    }

    public void completeSession(CrateSession session) {
        if (!session.tryComplete()) {
            return;
        }
        sessions.remove(session.getPlayer().getUniqueId());
        session.setRunning(false);
        session.cancelAllTasks();
    }

    private CrateAnimation resolveAnimation(Crate crate) {
        return switch (crate.getGuiAnimation()) {
            case SHUFFLER    -> new ShufflerAnimation(plugin);
            case BOUNDARY    -> new BoundaryAnimation(plugin);
            case SINGLE_SPIN -> new SingleSpinAnimation(plugin);
            case FLICKER     -> new FlickerAnimation(plugin);
            default          -> new RouletteAnimation(plugin);
        };
    }

    public void shutdown() {
        java.util.List<CrateSession> pending = new java.util.ArrayList<>(sessions.values());
        sessions.clear();

        for (CrateSession s : pending) {
            s.setRunning(false);
            s.cancelAllTasks();
            if (!s.tryComplete()) continue;

            Player player = s.getPlayer();
            RewardResult result = s.getResult();

            if (Bukkit.isPrimaryThread()) {
                plugin.getCrateManager().deliverRewardPublic(player, result);
            } else {
                // Should not normally happen — onDisable is main thread
                Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getCrateManager().deliverRewardPublic(player, result));
            }
        }
    }
}