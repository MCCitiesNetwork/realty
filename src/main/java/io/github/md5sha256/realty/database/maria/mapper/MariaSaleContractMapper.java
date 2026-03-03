package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code SaleContract} table.
 *
 * <p>The {@code SaleContract} table stores the authority, title-holder, and agreed price for a
 * direct-sale contract. Its association with a {@code RealtyRegion} is tracked through the
 * {@code Contract} table (managed by {@link MariaContractMapper}); callers must insert the
 * corresponding {@code Contract} row <em>before</em> invoking {@link #insertSale} so that
 * referential integrity is maintained at the application level.
 *
 * @see SaleContractEntity
 */
public interface MariaSaleContractMapper extends SaleContractMapper {

    /**
     * {@inheritDoc}
     *
     * <p>Inserts a single row into the {@code SaleContract} table. The {@code regionId} parameter
     * is accepted for API consistency (and may be used by callers to look up the region) but is
     * not written to the {@code SaleContract} table itself — that linkage is recorded in the
     * {@code Contract} table.
     *
     * <p>The generated {@code saleContractId} is set back onto the parameter map by MyBatis via
     * {@code useGeneratedKeys}.
     */
    @Override
    @Insert("""
            INSERT INTO SaleContract (authorityId, titleHolderId, price)
            VALUES (#{authority}, #{titleHolder}, #{price})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "saleContractId", keyColumn = "saleContractId")
    int insertSale(@Param("regionId") int regionId,
                   @Param("price") double price,
                   @Param("authority") @NotNull UUID authority,
                   @Param("titleHolder") @NotNull UUID titleHolder);

}
