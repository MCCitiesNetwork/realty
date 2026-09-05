package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The seam to the in-server {@code query-service} module.
 *
 * <p>Every method degrades rather than throws: an unreachable module yields an empty
 * result, and callers render that as null. The one place a caller must know the
 * difference between "unknown" and "could not ask" is {@link #uuidOf}, which is why
 * it returns a {@link NameLookup} rather than an {@code Optional}.</p>
 */
public interface ModuleClient {

    enum Status { OK, UNREACHABLE, DISABLED }

    @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId, @NotNull String regionId);

    /** Resolved names only; an id absent from the map could not be named. One HTTP call. */
    @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids);

    @NotNull NameLookup uuidOf(@NotNull String name);

    /**
     * Geometry for several regions in one call, keyed by region id. An id naming no region is
     * omitted. Enrichment, so an unreachable module yields an empty map rather than failing.
     */
    @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(@NotNull UUID worldId,
                                                                  @NotNull Collection<String> regionIds);

    /**
     * Which WorldGuard regions cover a block. {@code y} null asks the column question.
     * {@code NotFound} means the world is unknown or not region-managed.
     */
    @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x, @Nullable Integer y, int z);

    /** A region's WorldGuard owner and member domains. */
    @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId, @NotNull String regionId);

    /**
     * The game server's configured resource pack, so a browser renderer can texture blocks
     * with the same pack the game client uses. The whole answer comes from the module, so an
     * unreachable one is {@code Unavailable} rather than a silently empty pack.
     */
    @NotNull ModuleResult<ResourcePack> resourcePack();

    /** A live probe of the module's {@code /health}; never cached. */
    @NotNull Status status();

    /** The client used when {@code REALTY_REST_MODULE_URL} is unset. */
    static @NotNull ModuleClient disabled() {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                           @NotNull String regionId) {
                return Optional.empty();
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                return Map.of();
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unavailable();
            }

            @Override
            public @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                    @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
                return Map.of();
            }

            @Override
            public @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x,
                                                              @Nullable Integer y, int z) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId,
                                                                @NotNull String regionId) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<ResourcePack> resourcePack() {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull Status status() {
                return Status.DISABLED;
            }
        };
    }
}
