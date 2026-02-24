package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SaleContractBid(int saleContractId, @NotNull UUID bidderId, double bidAmount) {
}
