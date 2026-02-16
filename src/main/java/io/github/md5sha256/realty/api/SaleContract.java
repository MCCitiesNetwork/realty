package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public interface SaleContract extends Contract {

    @NotNull User authority();

    @NotNull User titleholder();

    @NotNull BigDecimal price();

    @NotNull Instant startDate();
}
