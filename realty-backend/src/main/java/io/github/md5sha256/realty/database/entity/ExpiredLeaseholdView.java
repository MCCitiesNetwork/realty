package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ExpiredLeaseholdView(
        int leaseholdContractId,
        @NotNull Party landlord,
        @NotNull UUID tenantId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId
) {
}
