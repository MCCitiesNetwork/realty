package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.rest.json.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Names for every player id in a response, fetched in one module call. Handlers
 * collect their ids first and build {@link PlayerRef}s afterwards so a ten-player
 * response costs one round trip, not ten.
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    public static @NotNull Map<UUID, String> resolve(@NotNull ModuleClient module, @NotNull Collection<@Nullable UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return module.names(distinct);
    }

    public static @Nullable PlayerRef ref(@Nullable UUID id, @NotNull Map<UUID, String> names) {
        return id == null ? null : new PlayerRef(id.toString(), names.get(id));
    }
}
