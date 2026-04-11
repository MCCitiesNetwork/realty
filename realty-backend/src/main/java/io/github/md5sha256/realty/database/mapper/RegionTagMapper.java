package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface RegionTagMapper {

    int insert(@NotNull String tagId, @NotNull String worldGuardRegionId);

    @NotNull List<String> selectRegionIdsByTagId(@NotNull String tagId);

    @NotNull List<String> selectTagIdsByRegionId(@NotNull String worldGuardRegionId);

    int deleteByTagAndRegion(@NotNull String tagId, @NotNull String worldGuardRegionId);

    int deleteByTagIdNotIn(@NotNull Collection<String> tagIds);

    int deleteAll();

}
