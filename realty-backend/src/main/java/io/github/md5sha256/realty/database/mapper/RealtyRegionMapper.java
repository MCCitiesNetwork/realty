package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RegionStateRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code RealtyRegion} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see RealtyRegionEntity
 */
public interface RealtyRegionMapper {

    int registerWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable RealtyRegionEntity selectById(int realtyRegionId);

    @Nullable RealtyRegionEntity selectByWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteByWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteByRealtyRegionId(int realtyRegionId);

    @NotNull List<RealtyRegionEntity> selectAll();

    /**
     * One page of every registered region, in a stable total order so that paging
     * through the table never repeats or skips a row between requests.
     */
    @NotNull List<RealtyRegionEntity> selectPage(int limit, int offset);

    @NotNull List<RealtyRegionEntity> selectRegionsByTitleHolder(@NotNull UUID playerId, int limit, int offset);

    @NotNull List<RealtyRegionEntity> selectRegionsByAuthority(@NotNull UUID playerId, int limit, int offset);

    @NotNull List<RealtyRegionEntity> selectRegionsByTenant(@NotNull UUID playerId, int limit, int offset);

    int countRegionsByTitleHolder(@NotNull UUID playerId);

    int countRegionsByAuthority(@NotNull UUID playerId);

    int countRegionsByTenant(@NotNull UUID playerId);

    @NotNull List<String> selectRegionNamesByTitleHolder(@NotNull UUID playerId);

    @NotNull List<String> selectRegionNamesByTenant(@NotNull UUID playerId);

    @NotNull List<String> selectRegionNamesByLandlord(@NotNull UUID playerId);

    int countRegionsByLandlord(@NotNull UUID playerId);

    int countAll();

    /**
     * The same page as {@link #selectPage(int, int)}, narrowed to one world.
     */
    @NotNull List<RealtyRegionEntity> selectPageByWorld(@NotNull UUID worldId, int limit, int offset);

    int countByWorld(@NotNull UUID worldId);

    /**
     * The same page as {@link #selectPage(int, int)}, each row carrying the state its
     * contract implies, derived in SQL rather than by a lookup per row.
     */
    @NotNull List<RegionStateRow> selectPageWithState(int limit, int offset);

    /**
     * The same page as {@link #selectPageWithState(int, int)}, narrowed to one world.
     */
    @NotNull List<RegionStateRow> selectPageWithStateByWorld(@NotNull UUID worldId, int limit, int offset);

    /**
     * The subset of {@code candidates} that is registered in {@code worldId}, each reported once.
     *
     * <p>WorldGuard knows regions Realty does not, so a lookup answered from WorldGuard has to be
     * intersected with this table before it is reported. Done in SQL rather than by a lookup per
     * candidate, and an empty candidate list matches nothing rather than everything.</p>
     */
    @NotNull List<String> selectRegisteredIds(@NotNull UUID worldId, @NotNull List<String> candidates);

}
