package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;

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
            public @NotNull Status status() {
                return Status.DISABLED;
            }
        };
    }
}
