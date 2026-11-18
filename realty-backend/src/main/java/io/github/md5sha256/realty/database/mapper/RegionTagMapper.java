package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.TagCountEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The tags on regions. A tag names its region by name and world, as {@code RealtyRegion} does.
 */
public interface RegionTagMapper {

    boolean exists(@NotNull String tagId, @NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int insert(@NotNull String tagId, @NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /** Inserts the tag if the region doesn't already have it; a no-op (0 rows) when it does. */
    int insertIfAbsent(@NotNull String tagId, @NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /** The names of the regions with the tag, each once, whatever world they are in. */
    @NotNull List<String> selectRegionIdsByTagId(@NotNull String tagId);

    @NotNull List<String> selectTagIdsByRegionId(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /** The tags on any region with this name, each once, whatever world it is in. */
    @NotNull List<String> selectTagIdsByRegionName(@NotNull String worldGuardRegionId);

    int deleteByTagAndRegion(@NotNull String tagId, @NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteByRegionId(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<String> selectDistinctTagIds();

    /** Every tag in use with how many regions carry it, in tag-id order. */
    @NotNull List<TagCountEntity> selectTagCounts();

    int deleteByTagIdNotIn(@NotNull Collection<String> tagIds);

    int deleteAll();

    int countByTagId(@NotNull String tagId);

    /** Moves a renamed region's tags to its new name. The new name must have no tags in that world. */
    int moveToRegion(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId,
                     @NotNull UUID worldId);
}
