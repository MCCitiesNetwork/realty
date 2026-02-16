package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;

public interface LeaseContract extends Contract {

    @NotNull User landlord();

    @NotNull User tenant();

    @NotNull BigDecimal price();

    @NotNull Duration duration();

    @NotNull Instant startDate();

    int currentExtensionCount();

    @NotNull OptionalInt maxExtensions();

    default boolean extensionAllowed() {
        return currentExtensionCount() <= maxExtensions().orElse(Integer.MAX_VALUE);
    }

    default boolean lapsed() {
        return Instant.now().isAfter(startDate().plus(duration()));
    }

}
