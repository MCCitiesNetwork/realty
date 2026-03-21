package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ExpiredLeaseView(
        int leaseContractId,
        @NotNull UUID landlordId,
        @NotNull UUID tenantId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId
) {
}
