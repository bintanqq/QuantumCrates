package me.bintanq.quantumcrates.animation;

import me.bintanq.quantumcrates.QuantumCrates;
import me.bintanq.quantumcrates.animation.impl.*;
import me.bintanq.quantumcrates.model.Crate;
import me.bintanq.quantumcrates.model.reward.RewardResult;
import me.bintanq.quantumcrates.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimationManager {

    private final QuantumCrates plugin;
    private final Map<UUID, CrateSession> sessions = new ConcurrentHashMap<>();
    private final EnumMap<Crate.GuiAnimationType, CrateAnimation> animationCache;

    public AnimationManager(QuantumCrates plugin) {
        this.plugin = plugin;
        this.animationCache = new EnumMap<>(Crate.GuiAnimationType.class);
        animationCache.put(Crate.GuiAnimationType.ROULETTE,    new RouletteAnimation(plugin));
        animationCache.put(Crate.GuiAnimationType.SHUFFLER,    new ShufflerAnimation(plugin));
        animationCache.put(Crate.GuiAnimationType.BOUNDARY,    new BoundaryAnimation(plugin));
        animationCache.put(Crate.GuiAnimationType.SINGLE_SPIN, new SingleSpinAnimation(plugin));
        animationCache.put(Crate.GuiAnimationType.FLICKER,     new FlickerAnimation(plugin));
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

    /**
     * Completes the session atomically. Returns true if THIS caller was the one
     * who completed it (i.e., won the CAS). The caller should only deliver the
     * reward if this returns true, preventing double delivery.
     */
    public boolean completeSession(CrateSession session) {
        if (!session.tryComplete()) {
            return false;
        }
        sessions.remove(session.getPlayer().getUniqueId());
        session.setRunning(false);
        session.cancelAllTasks();
        return true;
    }

    private CrateAnimation resolveAnimation(Crate crate) {
        return animationCache.getOrDefault(crate.getGuiAnimation(),
                animationCache.get(Crate.GuiAnimationType.ROULETTE));
    }

    public void shutdown() {
        java.util.List<CrateSession> pending = new java.util.ArrayList<>(sessions.values());
        sessions.clear();

        for (CrateSession s : pending) {
            s.setRunning(false);
            s.cancelAllTasks();
            if (!s.tryComplete())
                continue;

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