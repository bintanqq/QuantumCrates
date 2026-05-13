package me.bintanq.quantumcrates.util;

import me.bintanq.quantumcrates.model.Crate;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Applies knockback + sound effect when a player is denied crate access.
 * Must ONLY be called from the main thread.
 */
public final class KnockbackUtil {

    private KnockbackUtil() {}

    /**
     * Repels the player away from the nearest crate location.
     *
     * @param player    the player to push
     * @param crate     the crate that denied access
     * @param crateBlockLocation the block the player clicked
     */
    public static void applyDenied(Player player, Crate crate,
                                   Location crateBlockLocation) {
        if (!crate.isAccessDeniedKnockback()) return;

        Location playerLoc    = player.getLocation();
        Location crateCenterLoc = crateBlockLocation.clone().add(0.5, 0.5, 0.5);

        Vector direction = playerLoc.toVector().subtract(crateCenterLoc.toVector());
        if (direction.lengthSquared() < 0.001) {
            direction = player.getLocation().getDirection().multiply(-1);
        }
        direction = direction.normalize();

        double strength  = crate.getKnockbackStrength();
        Vector velocity  = direction.multiply(strength).setY(0.35 * strength);
        player.setVelocity(velocity);

        World world = player.getWorld();
        playDeniedSound(world, playerLoc);
    }

    private static final String[] DENIED_SOUND_NAMES = {
            "ENTITY_WIND_CHARGE_WIND_BURST",  // 1.21.2+
            "ENTITY_BREEZE_WIND_BURST",        // 1.21 snapshot names
            "UI_BUTTON_CLICK",                 // safe universal fallback
    };

    private static void playDeniedSound(World world, Location loc) {
        for (String name : DENIED_SOUND_NAMES) {
            try {
                Sound sound = Sound.valueOf(name);
                world.playSound(loc, sound, 0.8f, 1.1f);
                // Also add a secondary knock thud
                world.playSound(loc, Sound.BLOCK_PISTON_EXTEND, 0.4f, 0.6f);
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
        world.playSound(loc, Sound.BLOCK_PISTON_EXTEND, 0.9f, 0.5f);
    }
}