package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A pending change to a leasehold's terms. At most one non-terminal ({@code AWAITING_LANDLORD} or
 * {@code ACTIVE}) row exists per contract. {@code null} term fields mean "keep the contract's current
 * value" — on application the new terms are {@code COALESCE(modification, contract)} per field.
 *
 * @param modificationId      Auto-increment primary key
 * @param leaseholdContractId Owning leasehold contract
 * @param proposerRole        {@code "landlord"} or {@code "tenant"}
 * @param proposerId          UUID of the player who proposed the change
 * @param newPrice            Proposed price, or {@code null} to leave unchanged
 * @param newDurationSeconds  Proposed period length, or {@code null} to leave unchanged
 * @param newMaxExtensions    Proposed extension cap, or {@code null} to leave unchanged
 * @param status              Lifecycle status (see the {@code status} ENUM)
 * @param createdAt           When the proposal was created
 * @param resolvedAt          When the proposal reached a terminal status, or {@code null}
 */
public record LeaseholdModificationEntity(
        int modificationId,
        int leaseholdContractId,
        @NotNull String proposerRole,
        @NotNull UUID proposerId,
        @Nullable Double newPrice,
        @Nullable Long newDurationSeconds,
        @Nullable Integer newMaxExtensions,
        @NotNull String status,
        @NotNull LocalDateTime createdAt,
        @Nullable LocalDateTime resolvedAt
) {
}
