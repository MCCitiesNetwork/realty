package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only view of a pending leasehold modification, joined back to its region for inbox/outbox listings.
 * {@code null} term fields mean "unchanged".
 *
 * @param worldGuardRegionId WG region ID (from RealtyRegion)
 * @param worldId            World UUID (from RealtyRegion)
 * @param proposerRole       {@code "landlord"} or {@code "tenant"}
 * @param proposerId         UUID of the player who proposed the change
 * @param newPrice           Proposed price, or {@code null}
 * @param newDurationSeconds Proposed period length, or {@code null}
 * @param newMaxExtensions   Proposed extension cap, or {@code null}
 * @param status             Lifecycle status
 * @param createdAt          When the proposal was created
 */
public record LeaseholdModificationView(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String proposerRole,
        @NotNull UUID proposerId,
        @Nullable Double newPrice,
        @Nullable Long newDurationSeconds,
        @Nullable Integer newMaxExtensions,
        @NotNull String status,
        @NotNull LocalDateTime createdAt
) {
}
