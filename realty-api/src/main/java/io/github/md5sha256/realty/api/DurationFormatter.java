package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public final class DurationFormatter {

    private DurationFormatter() {}

    /**
     * Formats a duration as a human-readable string (e.g. "3d 2h 15m 30s").
     */
    public static @NotNull String format(@NotNull Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    /**
     * Formats a duration as a compact string without spaces (e.g. "3d2h15m30s"),
     * suitable for use in command arguments.
     */
    public static @NotNull String formatCompact(@NotNull Duration duration) {
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
        }
        if (hours > 0) {
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }
        return sb.toString();
    }
}
