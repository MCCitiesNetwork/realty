package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Mapper for searching regions by contract type, world, tags, price range, and occupancy.
 *
 * @param includeUnpricedFreehold when true, freehold contracts with no asking price (a
 *                                sold or never-listed region) are included as well; they have
 *                                no price to compare, so the price bounds do not apply to them
 *                                and their {@code price} is null. Has no effect on the
 *                                leasehold side, which always carries a price.
 * @param worldId        when non-null, only regions in that world are included
 * @param tagIds         when non-null, only regions with at least one of these tags are included
 * @param excludedTagIds when non-null, regions with any of these tags are excluded
 * @param matchAllTags   when true a region must carry every one of {@code tagIds} to match;
 *                       when false, at least one. No effect without {@code tagIds}.
 * @param occupancy      filters results by whether the region has a titleholder/tenant
 * @param sort           the result order; mapped to a fixed ORDER BY clause, never interpolated
 * @see SearchResultEntity
 */
public interface SearchMapper {

    @NotNull List<SearchResultEntity> search(boolean includeFreehold,
                                             boolean includeLeasehold,
                                             boolean includeUnpricedFreehold,
                                             @Nullable UUID worldId,
                                             @Nullable Collection<String> tagIds,
                                             @Nullable Collection<String> excludedTagIds,
                                             boolean matchAllTags,
                                             double minPrice,
                                             double maxPrice,
                                             @NotNull OccupancyFilter occupancy,
                                             @NotNull SearchSort sort,
                                             int limit,
                                             int offset);

    int searchCount(boolean includeFreehold,
                    boolean includeLeasehold,
                    boolean includeUnpricedFreehold,
                    @Nullable UUID worldId,
                    @Nullable Collection<String> tagIds,
                    @Nullable Collection<String> excludedTagIds,
                    boolean matchAllTags,
                    double minPrice,
                    double maxPrice,
                    @NotNull OccupancyFilter occupancy);

}
