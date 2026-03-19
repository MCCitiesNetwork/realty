package io.github.md5sha256.realty.command.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

class DurationParserUtilTest {

    @Nested
    @DisplayName("Single unit parsing")
    class SingleUnit {

        @Test
        @DisplayName("parse seconds")
        void parseSeconds() {
            Assertions.assertEquals(Duration.ofSeconds(30), DurationParserUtil.parse("30s"));
        }

        @Test
        @DisplayName("parse minutes with 'm'")
        void parseMinutesShort() {
            Assertions.assertEquals(Duration.ofMinutes(5), DurationParserUtil.parse("5m"));
        }

        @Test
        @DisplayName("parse minutes with 'min'")
        void parseMinutesLong() {
            Assertions.assertEquals(Duration.ofMinutes(10), DurationParserUtil.parse("10min"));
        }

        @Test
        @DisplayName("parse hours with 'h'")
        void parseHoursShort() {
            Assertions.assertEquals(Duration.ofHours(2), DurationParserUtil.parse("2h"));
        }

        @Test
        @DisplayName("parse hours with 'hr'")
        void parseHoursLong() {
            Assertions.assertEquals(Duration.ofHours(3), DurationParserUtil.parse("3hr"));
        }

        @Test
        @DisplayName("parse days")
        void parseDays() {
            Assertions.assertEquals(Duration.ofDays(7), DurationParserUtil.parse("7d"));
        }

        @Test
        @DisplayName("parse weeks with 'w'")
        void parseWeeksShort() {
            Assertions.assertEquals(Duration.ofDays(14), DurationParserUtil.parse("2w"));
        }

        @Test
        @DisplayName("parse weeks with 'wk'")
        void parseWeeksLong() {
            Assertions.assertEquals(Duration.ofDays(21), DurationParserUtil.parse("3wk"));
        }
    }

    @Nested
    @DisplayName("Combined unit parsing")
    class CombinedUnits {

        @Test
        @DisplayName("days and hours: 1d3hr")
        void parseDaysAndHours() {
            Duration expected = Duration.ofDays(1).plusHours(3);
            Assertions.assertEquals(expected, DurationParserUtil.parse("1d3hr"));
        }

        @Test
        @DisplayName("hours and minutes: 2h30m")
        void parseHoursAndMinutes() {
            Duration expected = Duration.ofHours(2).plusMinutes(30);
            Assertions.assertEquals(expected, DurationParserUtil.parse("2h30m"));
        }

        @Test
        @DisplayName("weeks and days: 1w2d")
        void parseWeeksAndDays() {
            Duration expected = Duration.ofDays(9); // 7 + 2
            Assertions.assertEquals(expected, DurationParserUtil.parse("1w2d"));
        }

        @Test
        @DisplayName("all units: 1w2d3h15min30s")
        void parseAllUnits() {
            Duration expected = Duration.ofDays(9)
                    .plusHours(3)
                    .plusMinutes(15)
                    .plusSeconds(30);
            Assertions.assertEquals(expected, DurationParserUtil.parse("1w2d3h15min30s"));
        }

        @Test
        @DisplayName("days, hours, and minutes: 2d12h45m")
        void parseDaysHoursMinutes() {
            Duration expected = Duration.ofDays(2).plusHours(12).plusMinutes(45);
            Assertions.assertEquals(expected, DurationParserUtil.parse("2d12h45m"));
        }
    }

    @Nested
    @DisplayName("Case insensitivity")
    class CaseInsensitivity {

        @ParameterizedTest
        @CsvSource({
                "5S,   5",
                "5s,   5",
                "10M,  600",
                "3H,   10800",
                "2HR,  7200",
                "1D,   86400",
                "1W,   604800",
                "1WK,  604800",
                "5MIN, 300",
        })
        @DisplayName("unit suffixes are case-insensitive")
        void caseInsensitive(String input, long expectedSeconds) {
            Assertions.assertEquals(Duration.ofSeconds(expectedSeconds), DurationParserUtil.parse(input));
        }
    }

    @Nested
    @DisplayName("Invalid input handling")
    class InvalidInput {

        @Test
        @DisplayName("empty string throws IllegalArgumentException")
        void emptyString() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "hello", "xyz"})
        @DisplayName("non-numeric input throws IllegalArgumentException")
        void nonNumericInput(String input) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"5", "100", "0"})
        @DisplayName("number without unit throws IllegalArgumentException")
        void numberWithoutUnit(String input) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"5x", "10y", "3z"})
        @DisplayName("unknown unit throws IllegalArgumentException")
        void unknownUnit(String input) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse(input));
        }

        @Test
        @DisplayName("zero duration throws IllegalArgumentException")
        void zeroDuration() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse("0s"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"1d abc", "5m 3h", " 1d"})
        @DisplayName("input with spaces throws IllegalArgumentException")
        void inputWithSpaces(String input) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> DurationParserUtil.parse(input));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("large values are supported")
        void largeValues() {
            Duration expected = Duration.ofDays(365);
            Assertions.assertEquals(expected, DurationParserUtil.parse("365d"));
        }

        @Test
        @DisplayName("single unit with value 1")
        void singleUnitValue1() {
            Assertions.assertEquals(Duration.ofSeconds(1), DurationParserUtil.parse("1s"));
        }

        @Test
        @DisplayName("mixed long and short suffixes: 1wk2d3hr30min10s")
        void mixedLongAndShortSuffixes() {
            Duration expected = Duration.ofDays(9)
                    .plusHours(3)
                    .plusMinutes(30)
                    .plusSeconds(10);
            Assertions.assertEquals(expected, DurationParserUtil.parse("1wk2d3hr30min10s"));
        }
    }
}
