package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ISO-8601 UTC timestamp formatting, shared by every JSON payload and by the
 * {@code RealtyBackend} construction in {@link RealtyRestMain}.
 *
 * <p>Stored timestamps are naive ({@link LocalDateTime}); the database records
 * everything in UTC, so formatting treats the value as already being UTC rather
 * than converting from the system default zone.</p>
 */
public final class IsoDates {

    private IsoDates() {
    }

    public static @NotNull String format(@NotNull LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * The inverse of {@link #format}, for reading an instant a caller sent.
     *
     * <p>An offset must be given -- a bare local date-time is rejected rather than
     * assumed to be UTC, since a caller who omitted it may well have meant their own
     * zone, and silently reading it as UTC would shift their query by hours without
     * saying so. The result is converted to UTC and returned naive, matching how the
     * database stores timestamps.</p>
     *
     * @throws IllegalArgumentException when the text is not an ISO-8601 instant
     */
    public static @NotNull LocalDateTime parse(@NotNull String text) {
        try {
            return OffsetDateTime.parse(text.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Not an ISO-8601 instant: " + text, ex);
        }
    }

}
