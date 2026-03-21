package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record FreeholdContractBid(int freeholdContractId, @NotNull UUID bidderId, double bidAmount, @NotNull LocalDateTime bidTime) {
}
