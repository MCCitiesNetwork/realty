package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.ContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base mapper interface for CRUD operations on the {@code Contract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see ContractEntity
 */
public interface ContractMapper {

    void insert(@NotNull ContractEntity entity);

    @Nullable ContractEntity selectById(int id);

    @NotNull List<ContractEntity> selectByRealtyRegionId(int realtyRegionId);

    @NotNull List<ContractEntity> selectAll();

    void update(@NotNull ContractEntity entity);

    void deleteById(int id);
}
