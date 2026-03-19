package io.github.md5sha256.realty.command.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility class for parsing human-readable duration strings into {@link Duration} objects.
 *
 * <p>Supported time units:</p>
 * <ul>
 *   <li>{@code s} — seconds</li>
 *   <li>{@code m}, {@code min} — minutes</li>
 *   <li>{@code h}, {@code hr} — hours</li>
 *   <li>{@code d} — days</li>
 *   <li>{@code w}, {@code wk} — weeks</li>
 * </ul>
 *
 * <p>Units can be combined in a single string (e.g. {@code 1d3hr}, {@code 2w5d12h30m10s}).</p>
 */
public final class DurationParserUtil {

    /**
     * Ordered map of unit suffix → multiplier in seconds, longest suffix first so that
     * greedy matching picks multi-char suffixes (e.g. "min") before single-char ones (e.g. "m").
     */
    private static final Map<String, Long> UNIT_TO_SECONDS;

    static {
        // Use a LinkedHashMap so iteration order is insertion order (longest suffixes first).
        UNIT_TO_SECONDS = new LinkedHashMap<>();
        UNIT_TO_SECONDS.put("min", 60L);
        UNIT_TO_SECONDS.put("wk", 7L * 24 * 60 * 60);
        UNIT_TO_SECONDS.put("hr", 60L * 60);
        UNIT_TO_SECONDS.put("w", 7L * 24 * 60 * 60);
        UNIT_TO_SECONDS.put("d", 24L * 60 * 60);
        UNIT_TO_SECONDS.put("h", 60L * 60);
        UNIT_TO_SECONDS.put("m", 60L);
        UNIT_TO_SECONDS.put("s", 1L);
    }

    /**
     * Pattern that matches one or more segments of {@code <digits><unit>}.
     * Used for full-string validation.
     */
    private static final Pattern FULL_PATTERN;

    /**
     * Pattern that captures a single {@code <digits><unit>} segment.
     */
    private static final Pattern SEGMENT_PATTERN;

    static {
        // Build a group that matches any known unit suffix (longest first for correct greedy match).
        String unitGroup = String.join("|", UNIT_TO_SECONDS.keySet()); // min|wk|hr|w|d|h|m|s
        SEGMENT_PATTERN = Pattern.compile("(\\d+)(" + unitGroup + ")", Pattern.CASE_INSENSITIVE);
        FULL_PATTERN = Pattern.compile("^(?:" + SEGMENT_PATTERN.pattern() + ")+$", Pattern.CASE_INSENSITIVE);
    }

    private DurationParserUtil() {
        throw new AssertionError("Utility class");
    }

    /**
     * Parse a human-readable duration string into a {@link Duration}.
     *
     * @param input the duration string (e.g. {@code "1d3hr"}, {@code "30m"}, {@code "2w5d"})
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if the input is null, empty, or contains invalid segments
     */
    public static @NotNull Duration parse(@NotNull String input) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException("Duration string must not be empty");
        }

        String normalized = input.toLowerCase();

        if (!FULL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid duration format: '" + input + "'");
        }

        long totalSeconds = 0;
        Matcher matcher = SEGMENT_PATTERN.matcher(normalized);

        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            Long multiplier = UNIT_TO_SECONDS.get(unit);
            // multiplier should never be null here since we already validated against FULL_PATTERN
            totalSeconds += amount * multiplier;
        }

        if (totalSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be positive: '" + input + "'");
        }

        return Duration.ofSeconds(totalSeconds);
    }
}
