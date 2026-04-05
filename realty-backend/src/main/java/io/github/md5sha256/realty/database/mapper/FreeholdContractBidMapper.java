package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface FreeholdContractBidMapper {

    @Nullable FreeholdContractBid selectHighestBid(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<UUID> selectDistinctBidders(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int performContractBid(@NotNull FreeholdContractBid bid);

}
