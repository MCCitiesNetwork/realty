package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One (title holder, region, tag) row from the property-tax enumeration query.
 *
 * <p>Each freehold region the player holds contributes one row per assigned tag,
 * or a single row with {@code tagId == null} when the region has no tags
 * (via the {@code LEFT JOIN RegionTag}). Callers group by
 * {@link #worldGuardRegionId()} to reconstruct each region's tag set.
 *
 * @param titleHolderId      the owner being taxed
 * @param worldGuardRegionId the WorldGuard region id of one of their freeholds
 * @param tagId              a tag on that region, or {@code null} if untagged
 */
public record TitleHeldRegionTag(
        @NotNull UUID titleHolderId,
        @NotNull String worldGuardRegionId,
        @Nullable String tagId
) {}
