package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** A lease marked for deletion that no longer has a tenant, due to be deleted by the sweep. */
public record MarkedVacantLeaseholdView(
        int leaseholdContractId,
        @NotNull Party landlord,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId
) {
}
