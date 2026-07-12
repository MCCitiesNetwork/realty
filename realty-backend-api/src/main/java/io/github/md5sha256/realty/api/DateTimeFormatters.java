package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;

/**
 * Shared {@link DateTimeFormatter} constants for user-facing date/time rendering, so command and
 * listener classes format timestamps consistently instead of each declaring their own.
 */
public final class DateTimeFormatters {

    /** {@code yyyy-MM-dd HH:mm} — minute-precision local date-time used in messages and notifications. */
    public static final @NotNull DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateTimeFormatters() {}
}
