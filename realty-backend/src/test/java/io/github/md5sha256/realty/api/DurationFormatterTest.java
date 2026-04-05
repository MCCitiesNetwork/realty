package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class DurationFormatterTest {

    @Test
    @DisplayName("formatTimeLeft returns N/A when no end date exists")
    void noEndDate() {
        String result = DurationFormatter.formatTimeLeft(null, LocalDateTime.of(2026, 3, 25, 1, 0, 0));
        Assertions.assertEquals("N/A", result);
    }

    @Test
    @DisplayName("formatTimeLeft returns Expired when end date has passed")
    void expired() {
        String result = DurationFormatter.formatTimeLeft(
                LocalDateTime.of(2026, 3, 25, 1, 0, 0),
                LocalDateTime.of(2026, 3, 25, 1, 0, 1)
        );
        Assertions.assertEquals("Expired", result);
    }

    @Test
    @DisplayName("formatTimeLeft formats remaining time using the standard duration formatter")
    void remainingTime() {
        String result = DurationFormatter.formatTimeLeft(
                LocalDateTime.of(2026, 3, 27, 4, 30, 15),
                LocalDateTime.of(2026, 3, 25, 1, 0, 0)
        );
        Assertions.assertEquals("2d 3h 30m 15s", result);
    }
}
