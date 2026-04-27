package me.bintanq.quantumcrates.util;

/**
 * TimeUtil — human-readable duration formatting.
 */
public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Formats a duration in milliseconds to a human-readable string.
     * e.g. 3661000ms → "1h 1m 1s"
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";

        long totalSeconds = millis / 1000;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600)  / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Formats a Unix epoch timestamp to "dd/MM/yyyy HH:mm:ss".
     */
    public static String formatTimestamp(long epochMillis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMillis);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(zdt);
    }
}
