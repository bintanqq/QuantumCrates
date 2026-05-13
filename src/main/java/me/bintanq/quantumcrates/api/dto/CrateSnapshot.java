package me.bintanq.quantumcrates.api.dto;

import me.bintanq.quantumcrates.model.Crate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CrateSnapshot — an immutable, read-only view of a crate definition.
 *
 * <p>Returned by the public API. Never exposes internal Crate mutators.</p>
 *
 * @since 1.4.0
 */
public record CrateSnapshot(
        String id,
        String displayName,
        boolean enabled,
        boolean currentlyOpenable,
        long cooldownMs,
        int rewardCount,
        double totalWeight,
        boolean massOpenEnabled,
        int massOpenLimit,
        int lifetimeOpenLimit,
        boolean pityEnabled,
        int pityThreshold,
        int pitySoftStart,
        List<KeyRequirementSnapshot> requiredKeys
) {
    /**
     * Creates a snapshot from an internal Crate object.
     *
     * @param c the internal crate
     * @return a read-only snapshot
     */
    public static CrateSnapshot from(Crate c) {
        return new CrateSnapshot(
                c.getId(),
                c.getDisplayName(),
                c.isEnabled(),
                c.isCurrentlyOpenable(),
                c.getCooldownMs(),
                c.getRewards().size(),
                c.getTotalWeight(),
                c.isMassOpenEnabled(),
                c.getMassOpenLimit(),
                c.getLifetimeOpenLimit(),
                c.getPity().isEnabled(),
                c.getPity().getThreshold(),
                c.getPity().getSoftPityStart(),
                c.getRequiredKeys().stream()
                        .map(k -> new KeyRequirementSnapshot(k.getKeyId(), k.getAmount(), k.getType().name()))
                        .collect(Collectors.toUnmodifiableList())
        );
    }
}
