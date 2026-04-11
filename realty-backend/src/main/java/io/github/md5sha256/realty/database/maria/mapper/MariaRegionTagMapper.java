package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface MariaRegionTagMapper extends RegionTagMapper {

    @Override
    @Insert("""
            INSERT INTO RegionTag (tagId, worldGuardRegionId)
            VALUES (#{tagId}, #{worldGuardRegionId})
            """)
    int insert(@Param("tagId") @NotNull String tagId,
               @Param("worldGuardRegionId") @NotNull String worldGuardRegionId);

    @Override
    @Select("""
            SELECT worldGuardRegionId
            FROM RegionTag
            WHERE tagId = #{tagId}
            """)
    @NotNull List<String> selectRegionIdsByTagId(@Param("tagId") @NotNull String tagId);

    @Override
    @Select("""
            SELECT tagId
            FROM RegionTag
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            """)
    @NotNull List<String> selectTagIdsByRegionId(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId);

    @Override
    @Delete("""
            DELETE FROM RegionTag
            WHERE tagId = #{tagId}
            AND worldGuardRegionId = #{worldGuardRegionId}
            """)
    int deleteByTagAndRegion(@Param("tagId") @NotNull String tagId,
                             @Param("worldGuardRegionId") @NotNull String worldGuardRegionId);

    @Override
    @Select("""
            SELECT DISTINCT tagId
            FROM RegionTag
            """)
    @NotNull List<String> selectDistinctTagIds();

    @Override
    @Delete("""
            <script>
            DELETE FROM RegionTag
            WHERE tagId NOT IN
            <foreach item="tagId" collection="tagIds" open="(" separator="," close=")">
                #{tagId}
            </foreach>
            </script>
            """)
    int deleteByTagIdNotIn(@Param("tagIds") @NotNull Collection<String> tagIds);

    @Override
    @Delete("""
            DELETE FROM RegionTag
            """)
    int deleteAll();

}
