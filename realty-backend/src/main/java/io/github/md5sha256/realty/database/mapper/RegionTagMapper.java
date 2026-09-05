package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.TagCountEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface RegionTagMapper {

    boolean exists(@NotNull String tagId, @NotNull String worldGuardRegionId);

    int insert(@NotNull String tagId, @NotNull String worldGuardRegionId);

    /** Inserts the tag if the region doesn't already have it; a no-op (0 rows) when it does. */
    int insertIfAbsent(@NotNull String tagId, @NotNull String worldGuardRegionId);

    @NotNull List<String> selectRegionIdsByTagId(@NotNull String tagId);

    @NotNull List<String> selectTagIdsByRegionId(@NotNull String worldGuardRegionId);

    int deleteByTagAndRegion(@NotNull String tagId, @NotNull String worldGuardRegionId);

    int deleteByRegionId(@NotNull String worldGuardRegionId);

    @NotNull List<String> selectDistinctTagIds();

    /** Every tag in use with how many regions carry it, in tag-id order. */
    @NotNull List<TagCountEntity> selectTagCounts();

    int deleteByTagIdNotIn(@NotNull Collection<String> tagIds);

    int deleteAll();

    int countByTagId(@NotNull String tagId);

}
