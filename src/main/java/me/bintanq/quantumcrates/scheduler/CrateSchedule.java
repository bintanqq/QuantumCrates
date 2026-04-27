package me.bintanq.quantumcrates.scheduler;

import com.google.gson.annotations.SerializedName;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CrateSchedule — defines WHEN a crate can be opened.
 *
 * Supports three modes:
 *  - ALWAYS: No restriction (schedule = null in Crate is preferred for this).
 *  - TIME_WINDOW: Openable between startTime and endTime each day.
 *  - DAYS_OF_WEEK: Openable only on specific days (optionally within a time window).
 *  - EVENT: Openable only between two absolute epoch-millis timestamps.
 */
public class CrateSchedule {

    @SerializedName("mode")
    private ScheduleMode mode = ScheduleMode.ALWAYS;

    /** Time zone for time-based comparisons, e.g. "Asia/Jakarta". */
    @SerializedName("timezone")
    private String timezone = "UTC";

    /** Daily start time in "HH:mm" format. Used by TIME_WINDOW and DAYS_OF_WEEK. */
    @SerializedName("startTime")
    private String startTime;

    /** Daily end time in "HH:mm" format. Used by TIME_WINDOW and DAYS_OF_WEEK. */
    @SerializedName("endTime")
    private String endTime;

    /**
     * Days of week (1=Monday … 7=Sunday, ISO-8601).
     * Used by DAYS_OF_WEEK mode.
     */
    @SerializedName("daysOfWeek")
    private List<Integer> daysOfWeek;

    /** Absolute start timestamp in epoch-millis. Used by EVENT mode. */
    @SerializedName("eventStart")
    private long eventStart;

    /** Absolute end timestamp in epoch-millis. Used by EVENT mode. */
    @SerializedName("eventEnd")
    private long eventEnd;

    /* ─────────────────────── Core Logic ─────────────────────── */

    /**
     * Returns true if the crate should be openable right now.
     * All time comparisons use the configured timezone.
     */
    public boolean isCurrentlyActive() {
        return switch (mode) {
            case ALWAYS       -> true;
            case TIME_WINDOW  -> checkTimeWindow();
            case DAYS_OF_WEEK -> checkDaysOfWeek();
            case EVENT        -> checkEvent();
        };
    }

    private boolean checkTimeWindow() {
        if (startTime == null || endTime == null) return true;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        LocalTime nowTime = now.toLocalTime();
        LocalTime start   = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime end     = LocalTime.parse(endTime,   DateTimeFormatter.ofPattern("HH:mm"));

        // Handle overnight windows (e.g. 22:00 – 02:00)
        if (start.isBefore(end)) {
            return !nowTime.isBefore(start) && nowTime.isBefore(end);
        } else {
            return !nowTime.isBefore(start) || nowTime.isBefore(end);
        }
    }

    private boolean checkDaysOfWeek() {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return true;
        ZonedDateTime now     = ZonedDateTime.now(ZoneId.of(timezone));
        int           today   = now.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
        if (!daysOfWeek.contains(today)) return false;

        // If no time window configured, entire day counts
        if (startTime == null || endTime == null) return true;
        return checkTimeWindow();
    }

    private boolean checkEvent() {
        long now = System.currentTimeMillis();
        return now >= eventStart && now <= eventEnd;
    }

    /* ─────────────────────── Human-Readable Description ─────────────────────── */

    /**
     * Returns the next activation time as a human-readable string.
     * Used in the "crate not available" message.
     */
    public String getNextOpenDescription() {
        return switch (mode) {
            case ALWAYS       -> "Now";
            case TIME_WINDOW  -> "Daily " + startTime + " – " + endTime + " (" + timezone + ")";
            case DAYS_OF_WEEK -> "Selected days " + daysOfWeek + " " +
                                 (startTime != null ? startTime + "–" + endTime : "") +
                                 " (" + timezone + ")";
            case EVENT        -> "Event ends: " + Instant.ofEpochMilli(eventEnd).toString();
        };
    }

    /* ─────────────────────── Enum ─────────────────────── */

    public enum ScheduleMode {
        ALWAYS,
        TIME_WINDOW,
        DAYS_OF_WEEK,
        EVENT
    }

    /* ─────────────────────── Getters / Setters ─────────────────────── */

    public ScheduleMode getMode() { return mode; }
    public void setMode(ScheduleMode mode) { this.mode = mode; }
    public String getTimezone() { return timezone; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public List<Integer> getDaysOfWeek() { return daysOfWeek; }
    public long getEventStart() { return eventStart; }
    public long getEventEnd() { return eventEnd; }
}
