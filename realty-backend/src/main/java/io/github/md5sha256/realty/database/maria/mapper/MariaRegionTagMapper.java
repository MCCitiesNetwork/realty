package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MariaRegionTagMapper extends RegionTagMapper {

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM RegionTag
            WHERE tagId = #{tagId}
            AND worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    boolean exists(@Param("tagId") @NotNull String tagId,
                   @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                   @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO RegionTag (tagId, worldGuardRegionId, worldId)
            VALUES (#{tagId}, #{worldGuardRegionId}, #{worldId})
            """)
    int insert(@Param("tagId") @NotNull String tagId,
               @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT IGNORE INTO RegionTag (tagId, worldGuardRegionId, worldId)
            VALUES (#{tagId}, #{worldGuardRegionId}, #{worldId})
            """)
    int insertIfAbsent(@Param("tagId") @NotNull String tagId,
                       @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT DISTINCT worldGuardRegionId
            FROM RegionTag
            WHERE tagId = #{tagId}
            """)
    @NotNull List<String> selectRegionIdsByTagId(@Param("tagId") @NotNull String tagId);

    @Override
    @Select("""
            SELECT tagId
            FROM RegionTag
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    @NotNull List<String> selectTagIdsByRegionId(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                 @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT DISTINCT tagId
            FROM RegionTag
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            """)
    @NotNull List<String> selectTagIdsByRegionName(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId);

    @Override
    @Delete("""
            DELETE FROM RegionTag
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    int deleteByRegionId(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                         @Param("worldId") @NotNull UUID worldId);

    @Override
    @Delete("""
            DELETE FROM RegionTag
            WHERE tagId = #{tagId}
            AND worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    int deleteByTagAndRegion(@Param("tagId") @NotNull String tagId,
                             @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                             @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT DISTINCT tagId
            FROM RegionTag
            ORDER BY tagId ASC
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

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM RegionTag
            WHERE tagId = #{tagId}
            """)
    int countByTagId(@Param("tagId") @NotNull String tagId);

    @Override
    @Select("""
            SELECT tagId, COUNT(*) AS regionCount
            FROM RegionTag
            GROUP BY tagId
            ORDER BY tagId
            """)
    @ConstructorArgs({
            @Arg(column = "tagId", javaType = String.class),
            @Arg(column = "regionCount", javaType = int.class)
    })
    @NotNull List<TagCountEntity> selectTagCounts();

    @Override
    @Update("""
            UPDATE RegionTag
            SET worldGuardRegionId = #{newWorldGuardRegionId}
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    int moveToRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("newWorldGuardRegionId") @NotNull String newWorldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId);
}
