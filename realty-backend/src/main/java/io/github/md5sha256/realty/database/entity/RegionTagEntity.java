package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

public record RegionTagEntity(
        @NotNull String tagId,
        @NotNull String worldGuardRegionId
) {
}
