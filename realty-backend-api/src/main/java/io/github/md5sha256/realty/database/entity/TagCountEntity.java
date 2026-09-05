package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

/**
 * One tag in use and how many regions carry it -- a row of {@code GROUP BY tagId}.
 *
 * <p>Read in one statement for every tag at once. Listing the tag ids and then counting
 * each was a query per tag, and the tag list is asked for by every visitor to the front
 * page.</p>
 *
 * @param tagId       The tag's raw id, as {@code RegionTag} stores it
 * @param regionCount How many regions carry the tag; never zero, since a tag nobody
 *                    carries has no row to be grouped
 */
public record TagCountEntity(@NotNull String tagId, int regionCount) {
}
