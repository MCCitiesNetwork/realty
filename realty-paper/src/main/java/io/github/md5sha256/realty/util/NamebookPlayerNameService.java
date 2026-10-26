package io.github.md5sha256.realty.util;

import com.minecraftcitiesnetwork.namebook.api.Namebook;
import com.minecraftcitiesnetwork.namebook.api.NamebookFinder;
import io.github.md5sha256.realty.api.PlayerNameService;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link PlayerNameService} over Namebook, used instead of {@link SquirrelIdPlayerNameService}
 * when Namebook is installed. Namebook answers from its own cache and database before it tries the
 * server's usercache or the network, and a bulk lookup makes at most one database query, so a batch
 * from {@code query-service} costs one round trip.
 *
 * <p>Namebook is an optional dependency whose API is {@code compileOnly}. This is the only class
 * that names its types, and {@code Realty} loads it only after checking that Namebook is enabled.
 * Namebook's futures complete on its own threads, which {@link PlayerNameService} allows.</p>
 */
public final class NamebookPlayerNameService implements PlayerNameService {

    private final Namebook namebook;

    public NamebookPlayerNameService(@NotNull Namebook namebook) {
        this.namebook = Objects.requireNonNull(namebook, "namebook");
    }

    /** Null when Namebook is enabled but has not registered its service. */
    public static @Nullable PlayerNameService find(@NotNull ServicesManager services) {
        return NamebookFinder.find(services).map(NamebookPlayerNameService::new).orElse(null);
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
        return this.namebook.nameOf(id);
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
        return this.namebook.uuidOf(name);
    }

    @Override
    public @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(@NotNull Collection<UUID> ids) {
        return this.namebook.namesOf(ids);
    }

    @Override
    public @NotNull CompletableFuture<Map<String, Optional<UUID>>> uuidsOf(@NotNull Collection<String> names) {
        return this.namebook.uuidsOf(names);
    }
}
