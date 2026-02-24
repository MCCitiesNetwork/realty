package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code LeaseContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see LeaseContractEntity
 */
public interface LeaseContractMapper {

    void insert(@NotNull LeaseContractEntity entity);

    @Nullable LeaseContractEntity selectById(int id);

    @NotNull List<LeaseContractEntity> selectByTenantId(@NotNull UUID tenantId);

    @NotNull List<LeaseContractEntity> selectAll();

    void update(@NotNull LeaseContractEntity entity);

    void deleteById(int id);
}
