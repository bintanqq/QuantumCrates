package me.bintanq.quantumcrates.api.dto;

/**
 * KeyRequirementSnapshot — an immutable view of a single key requirement.
 *
 * @param keyId  the key identifier
 * @param amount the required amount
 * @param type   the key type name (VIRTUAL, PHYSICAL, etc.)
 * @since 1.4.0
 */
public record KeyRequirementSnapshot(String keyId, int amount, String type) {}
