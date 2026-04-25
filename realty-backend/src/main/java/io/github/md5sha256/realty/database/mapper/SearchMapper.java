package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Mapper for searching regions by contract type, tags, price range, and occupancy.
 *
 * @param tagIds         when non-null, only regions with at least one of these tags are included
 * @param excludedTagIds when non-null, regions with any of these tags are excluded
 * @param occupancy      filters results by whether the region has a titleholder/tenant
 * @see SearchResultEntity
 */
public interface SearchMapper {

    @NotNull List<SearchResultEntity> search(boolean includeFreehold,
                                             boolean includeLeasehold,
                                             @Nullable Collection<String> tagIds,
                                             @Nullable Collection<String> excludedTagIds,
                                             double minPrice,
                                             double maxPrice,
                                             @NotNull OccupancyFilter occupancy,
                                             int limit,
                                             int offset);

    int searchCount(boolean includeFreehold,
                    boolean includeLeasehold,
                    @Nullable Collection<String> tagIds,
                    @Nullable Collection<String> excludedTagIds,
                    double minPrice,
                    double maxPrice,
                    @NotNull OccupancyFilter occupancy);

}
