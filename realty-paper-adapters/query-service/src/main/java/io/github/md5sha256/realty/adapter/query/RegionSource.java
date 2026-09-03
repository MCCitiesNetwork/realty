package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Live reads against WorldGuard. Every method is asynchronous because the read has to happen on
 * the server main thread; the caller owns the timeout, because it owns the request.
 *
 * <p>Throughout, an empty {@link Optional} means "no such world, or no such region" -- the
 * distinction a handler turns into a 404. It is deliberately not the same as an empty
 * collection, which means the world was read and simply holds nothing matching.</p>
 */
public interface RegionSource {

    /** One region's geometry; empty when no such world or region exists. */
    @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                      @NotNull String regionId);

    /**
     * Geometry for several regions in one main-thread hop, keyed by region id. An id naming no
     * region is omitted rather than mapped to null, so the map's key set is exactly the subset
     * that exists. An unknown world yields an empty map.
     */
    @NotNull CompletableFuture<Map<String, RegionDimensions>> dimensionsOf(@NotNull UUID worldId,
                                                                           @NotNull Collection<String> regionIds);

    /**
     * The ids of every region covering a block, in the world's own region order.
     *
     * @param y the block's height for a true point test, or {@code null} for a column test that
     *          matches every region whose horizontal footprint covers {@code (x, z)} at any
     *          height. The two are different questions and neither is a default for the other:
     *          a map click has no {@code y} to send, while a player standing somewhere does.
     * @return empty when the world is unknown or not region-managed; an empty list when the world
     *         was read and nothing covers the block
     */
    @NotNull CompletableFuture<Optional<List<String>>> regionsAt(@NotNull UUID worldId,
                                                                 int x,
                                                                 @Nullable Integer y,
                                                                 int z);

    /** A region's WorldGuard owner and member domains; empty when no such world or region exists. */
    @NotNull CompletableFuture<Optional<RegionMembers>> members(@NotNull UUID worldId,
                                                                @NotNull String regionId);
}
