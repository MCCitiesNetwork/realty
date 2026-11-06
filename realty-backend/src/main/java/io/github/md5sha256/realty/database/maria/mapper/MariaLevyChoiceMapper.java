package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.LevyChoiceMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code RegionLevy} and {@code AuthorityLevy} tables.
 */
public interface MariaLevyChoiceMapper extends LevyChoiceMapper {

    @Override
    @Select("""
            SELECT levyName
            FROM RegionLevy
            WHERE realtyRegionId = #{realtyRegionId}
            """)
    @Nullable String selectRegionLevy(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Select("""
            SELECT levyName
            FROM AuthorityLevy
            WHERE authorityPartyId = #{authorityPartyId}
            """)
    @Nullable String selectAuthorityLevy(@Param("authorityPartyId") int authorityPartyId);

    @Override
    @Insert("""
            INSERT INTO RegionLevy (realtyRegionId, levyName, setBy)
            VALUES (#{realtyRegionId}, #{levyName}, #{setBy})
            ON DUPLICATE KEY UPDATE
                levyName = VALUE(levyName),
                setBy = VALUE(setBy),
                setAt = NOW()
            """)
    int upsertRegionLevy(@Param("realtyRegionId") int realtyRegionId,
                         @Param("levyName") @NotNull String levyName,
                         @Param("setBy") @Nullable UUID setBy);

    @Override
    @Delete("""
            DELETE FROM RegionLevy
            WHERE realtyRegionId = #{realtyRegionId}
            """)
    int deleteRegionLevy(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Insert("""
            INSERT INTO AuthorityLevy (authorityPartyId, levyName, setBy)
            VALUES (#{partyId}, #{levyName}, #{setBy})
            ON DUPLICATE KEY UPDATE
                levyName = VALUE(levyName),
                setBy = VALUE(setBy),
                setAt = NOW()
            """)
    int upsertAuthorityLevy(@Param("partyId") int partyId,
                            @Param("levyName") @NotNull String levyName,
                            @Param("setBy") @Nullable UUID setBy);

    @Override
    @Delete("""
            DELETE FROM AuthorityLevy
            WHERE authorityPartyId = #{partyId}
            """)
    int deleteAuthorityLevy(@Param("partyId") int partyId);

    @Override
    @Select("""
            SELECT levyName FROM RegionLevy
            UNION
            SELECT levyName FROM AuthorityLevy
            """)
    @NotNull List<String> selectAllChosenLevyNames();

}
