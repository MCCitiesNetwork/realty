package io.github.md5sha256.realty.util;

import org.enginehub.squirrelid.Profile;
import org.enginehub.squirrelid.cache.ProfileCache;
import org.enginehub.squirrelid.cache.SQLiteCache;
import org.enginehub.squirrelid.resolver.CacheForwardingService;
import org.enginehub.squirrelid.resolver.CombinedProfileService;
import org.enginehub.squirrelid.resolver.HttpRepositoryService;
import org.enginehub.squirrelid.resolver.PaperPlayerService;
import org.enginehub.squirrelid.resolver.ProfileService;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SquirrelIdUsernameResolver {

    private final ProfileCache cache;
    private final ProfileService service;
    private final Executor asyncExecutor;
    private final Server server;

    public SquirrelIdUsernameResolver(@NotNull File cacheFile,
                                      @NotNull Executor asyncExecutor,
                                      @NotNull Server server) throws IOException {
        this.cache = new SQLiteCache(cacheFile);
        this.service = new CacheForwardingService(
                new CombinedProfileService(
                        PaperPlayerService.getInstance(),
                        HttpRepositoryService.forMinecraft()),
                this.cache);
        this.asyncExecutor = asyncExecutor;
        this.server = server;
    }

    @NotNull
    public Optional<String> getUsernameIfCached(@NotNull UUID uuid) {
        Profile profile = this.cache.getIfPresent(uuid);
        return Optional.ofNullable(profile).map(Profile::getName);
    }

    @NotNull
    public CompletableFuture<String> getUsername(@NotNull UUID uuid) {
        // Prefer the server's local usercache: it holds the correct name for any player
        // who has joined, including Bedrock/Floodgate players whose '.'-prefixed names
        // Mojang can't resolve. The UUID overload of getOfflinePlayer never hits Mojang.
        // Only fall through to the (Mojang-backed) profile service for a UUID the server
        // has genuinely never seen.
        String local = this.server.getOfflinePlayer(uuid).getName();
        if (local != null && !local.isEmpty()) {
            return CompletableFuture.completedFuture(local);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Profile profile = this.service.findByUuid(uuid);
                if (profile != null && profile.getName() != null) {
                    return profile.getName();
                }
                return uuid.toString();
            } catch (Exception ex) {
                return uuid.toString();
            }
        }, this.asyncExecutor);
    }

    /**
     * Name → UUID. Mirrors {@link #getUsername}: the local usercache first, which is the only place
     * a Floodgate name such as {@code .Cool Guy 123} can be found, then the profile service.
     * Completes empty, never exceptionally, when nothing knows the name.
     */
    @NotNull
    public CompletableFuture<Optional<UUID>> getUuid(@NotNull String name) {
        OfflinePlayer cached = this.server.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached.getUniqueId()));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Profile profile = this.service.findByName(name);
                return Optional.ofNullable(profile).map(Profile::getUniqueId);
            } catch (Exception ex) {
                return Optional.<UUID>empty();
            }
        }, this.asyncExecutor);
    }

}
