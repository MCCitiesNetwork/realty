package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface SaleContractAuction {

    int realtyRegionId();

    @NotNull Instant startTime();

    @NotNull Duration duration();

    @NotNull Duration paymentDuration();

    @NotNull BigDecimal minBid();

    @NotNull BigDecimal minPriceStep();

    @Nullable Bid currentBid();

    record Bid(@NotNull UUID bidder, @NotNull BigDecimal amount) {

    }

}
