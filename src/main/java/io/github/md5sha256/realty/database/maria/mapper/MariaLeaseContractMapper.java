package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import org.apache.ibatis.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code LeaseContract} table.
 *
 * @see LeaseContractEntity
 */
public interface MariaLeaseContractMapper extends LeaseContractMapper {


}
