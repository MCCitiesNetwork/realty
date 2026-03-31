package io.github.md5sha256.realty.command.util;

import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class DurationParser implements ArgumentParser<Source, Duration> {

    private static final Duration MIN_DURATION = Duration.ZERO;
    private static final Duration MAX_DURATION = Duration.ofSeconds(Long.MAX_VALUE);

    private final Duration minimum;
    private final Duration maximum;

    private DurationParser(@NotNull Duration minimum, @NotNull Duration maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "Minimum duration (" + minimum + ") must not be greater than maximum duration (" + maximum + ")"
            );
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static @NotNull ParserDescriptor<Source, Duration> duration() {
        return ParserDescriptor.of(new DurationParser(MIN_DURATION, MAX_DURATION), Duration.class);
    }

    public static @NotNull ParserDescriptor<Source, Duration> duration(@NotNull Duration min) {
        return ParserDescriptor.of(new DurationParser(min, MAX_DURATION), Duration.class);
    }

    public static @NotNull ParserDescriptor<Source, Duration> duration(@NotNull Duration min,
                                                                                   @NotNull Duration max) {
        return ParserDescriptor.of(new DurationParser(min, max), Duration.class);
    }

    @Override
    public @NotNull ArgumentParseResult<Duration> parse(
            @NotNull CommandContext<Source> ctx,
            @NotNull CommandInput input
    ) {
        String raw = input.readString();
        Duration parsed;
        try {
            parsed = DurationParserUtil.parse(raw);
        } catch (IllegalArgumentException ex) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Invalid duration: " + raw));
        }

        if (parsed.compareTo(this.minimum) < 0) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Duration must not be less than " + this.minimum));
        }
        if (parsed.compareTo(this.maximum) > 0) {
            return ArgumentParseResult.failure(
                    new IllegalArgumentException("Duration must not be greater than " + this.maximum));
        }

        return ArgumentParseResult.success(parsed);
    }
}
