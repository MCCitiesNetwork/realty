package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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

}
