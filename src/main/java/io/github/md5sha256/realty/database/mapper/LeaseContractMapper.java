package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code LeaseContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see LeaseContractEntity
 */
public interface LeaseContractMapper {

    void insertLease(
            int regionId,
            double price,
            @NotNull Duration period,
            int maxRenewals,
            @NotNull UUID landlord
    );
}
