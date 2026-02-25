package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * A Brigadier {@link CustomArgumentType} that parses human-readable duration strings
 * into {@link Duration} objects, with optional minimum and maximum bounds.
 *
 * <p>Delegates all parsing logic to {@link DurationParser}.</p>
 *
 * <p>Supported syntax examples: {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 1d3hr}, {@code 2w5d12h}.</p>
 *
 * <p>Factory methods mirror the pattern used by Brigadier's {@code DoubleArgumentType}:</p>
 * <ul>
 *   <li>{@link #duration()} — no bounds</li>
 *   <li>{@link #duration(Duration)} — minimum only</li>
 *   <li>{@link #duration(Duration, Duration)} — minimum and maximum</li>
 * </ul>
 */
public class DurationArgument implements CustomArgumentType<Duration, String> {

    private static final Duration MIN_DURATION = Duration.ZERO;
    private static final Duration MAX_DURATION = Duration.ofSeconds(Long.MAX_VALUE);

    private static final DynamicCommandExceptionType ERROR_INVALID_DURATION = new DynamicCommandExceptionType(
            input -> () -> "Invalid duration: " + input
    );

    private static final DynamicCommandExceptionType ERROR_TOO_LOW = new DynamicCommandExceptionType(
            min -> () -> "Duration must not be less than " + min
    );

    private static final DynamicCommandExceptionType ERROR_TOO_HIGH = new DynamicCommandExceptionType(
            max -> () -> "Duration must not be greater than " + max
    );

    private static final Collection<String> EXAMPLES = List.of(
            "30s",
            "5m",
            "1h30min",
            "1d3hr",
            "2w5d12h"
    );

    private final Duration minimum;
    private final Duration maximum;

    private DurationArgument(@NotNull Duration minimum, @NotNull Duration maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "Minimum duration (" + minimum + ") must not be greater than maximum duration (" + maximum + ")"
            );
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /**
     * Creates a {@link DurationArgument} with no bounds.
     *
     * @return a new unbounded duration argument
     */
    public static @NotNull DurationArgument duration() {
        return new DurationArgument(MIN_DURATION, MAX_DURATION);
    }

    /**
     * Creates a {@link DurationArgument} with a minimum bound.
     *
     * @param min the minimum allowed duration (inclusive)
     * @return a new duration argument with the given minimum
     */
    public static @NotNull DurationArgument duration(@NotNull Duration min) {
        return new DurationArgument(min, MAX_DURATION);
    }

    /**
     * Creates a {@link DurationArgument} with both minimum and maximum bounds.
     *
     * @param min the minimum allowed duration (inclusive)
     * @param max the maximum allowed duration (inclusive)
     * @return a new duration argument with the given bounds
     * @throws IllegalArgumentException if {@code min} is greater than {@code max}
     */
    public static @NotNull DurationArgument duration(@NotNull Duration min, @NotNull Duration max) {
        return new DurationArgument(min, max);
    }

    @Override
    public @NotNull Duration parse(@NotNull StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String input = reader.readUnquotedString();

        Duration parsed;
        try {
            parsed = DurationParser.parse(input);
        } catch (IllegalArgumentException ex) {
            reader.setCursor(start);
            throw ERROR_INVALID_DURATION.createWithContext(reader, input);
        }

        if (parsed.compareTo(this.minimum) < 0) {
            reader.setCursor(start);
            throw ERROR_TOO_LOW.createWithContext(reader, this.minimum);
        }
        if (parsed.compareTo(this.maximum) > 0) {
            reader.setCursor(start);
            throw ERROR_TOO_HIGH.createWithContext(reader, this.maximum);
        }

        return parsed;
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
