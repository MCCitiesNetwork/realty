package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code SaleContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractEntity
 */
public interface SaleContractMapper {

    void insertSale(int regionId, double price, @NotNull UUID authority);

}
