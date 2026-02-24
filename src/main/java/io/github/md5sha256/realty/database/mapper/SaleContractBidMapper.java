package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractBid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface SaleContractBidMapper {

    @Nullable SaleContractBid selectCurrentBid(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int performContractBid(@NotNull SaleContractBid bid);

}
