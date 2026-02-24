package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code SaleContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractEntity
 */
public interface SaleContractMapper {

    void insert(@NotNull SaleContractEntity entity);

    @Nullable SaleContractEntity selectById(int id);

    @NotNull List<SaleContractEntity> selectByTitleHolderId(@NotNull UUID titleHolderId);

    @NotNull List<SaleContractEntity> selectAll();

    void update(@NotNull SaleContractEntity entity);

    void deleteById(int id);
}
