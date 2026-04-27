package me.bintanq.quantumcrates.web;

import me.bintanq.quantumcrates.util.Logger;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebTokenManager — manages one-time / time-limited web session tokens.
 *
 * Flow:
 *  1. /qc web → generateToken() → returns "abc123xyz..."
 *  2. Plugin prints clickable link: http://ip:port/?token=abc123xyz
 *  3. Browser opens link → Javalin intercepts → validates token → sets JWT cookie
 *  4. Token is consumed (single-use) — cannot reuse same link
 *
 * Tokens expire after 5 minutes if unused.
 */
public class WebTokenManager {

    private static final int    TOKEN_BYTES   = 24;     // 32-char base64url
    private static final long   TOKEN_TTL_MS  = 5 * 60 * 1000; // 5 menit
    private static final int    MAX_TOKENS    = 20;     // max pending tokens

    private final SecureRandom rng = new SecureRandom();

    /** token → expiry epoch millis */
    private final ConcurrentHashMap<String, Long> pendingTokens = new ConcurrentHashMap<>();

    /* ─────────────────────── Generate ─────────────────────── */

    /**
     * Generate a new single-use magic link token.
     * Returns the raw token string (URL-safe base64, no padding).
     */
    public String generateToken() {
        // Purge expired tokens first
        purgeExpired();

        if (pendingTokens.size() >= MAX_TOKENS) {
            pendingTokens.clear(); // safety valve
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        rng.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        pendingTokens.put(token, System.currentTimeMillis() + TOKEN_TTL_MS);
        Logger.debug("[WebToken] Generated token (expires in 5min): " + token.substring(0, 8) + "...");
        return token;
    }

    /* ─────────────────────── Validate & Consume ─────────────────────── */

    /**
     * Validates and CONSUMES a token (single-use).
     * Returns true if valid, false if expired/not found.
     */
    public boolean consumeToken(String token) {
        if (token == null || token.isEmpty()) return false;
        Long expiry = pendingTokens.remove(token);
        if (expiry == null) {
            Logger.debug("[WebToken] Token not found: " + token.substring(0, Math.min(8, token.length())) + "...");
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            Logger.debug("[WebToken] Token expired.");
            return false;
        }
        Logger.debug("[WebToken] Token consumed successfully.");
        return true;
    }

    /* ─────────────────────── Cleanup ─────────────────────── */

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingTokens.entrySet().removeIf(e -> now > e.getValue());
    }

    public int getPendingCount() { return pendingTokens.size(); }
}
