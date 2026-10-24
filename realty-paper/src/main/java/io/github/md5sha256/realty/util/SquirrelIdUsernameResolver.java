package io.github.md5sha256.realty.util;

import com.destroystokyo.paper.profile.PlayerProfile;
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
    private final ProfileService mojang;
    private final Executor asyncExecutor;
    private final Server server;

    public SquirrelIdUsernameResolver(@NotNull File cacheFile,
                                      @NotNull Executor asyncExecutor,
                                      @NotNull Server server) throws IOException {
        this(new SQLiteCache(cacheFile), HttpRepositoryService.forMinecraft(), asyncExecutor, server);
    }

    SquirrelIdUsernameResolver(@NotNull ProfileCache cache,
                               @NotNull ProfileService mojang,
                               @NotNull Executor asyncExecutor,
                               @NotNull Server server) {
        this.cache = cache;
        this.mojang = mojang;
        this.service = new CacheForwardingService(
                new CombinedProfileService(PaperPlayerService.getInstance(), mojang),
                cache);
        this.asyncExecutor = asyncExecutor;
        this.server = server;
    }

    @NotNull
    public Optional<String> getUsernameIfCached(@NotNull UUID uuid) {
        Profile profile = this.cache.getIfPresent(uuid);
        return Optional.ofNullable(profile).map(Profile::getName);
    }

    /**
     * UUID → name, tried in this order: the server's usercache, then {@code profiles.sqlite}, then
     * Mojang. Completes with the UUID's own string when none of them knows the player.
     *
     * <p>The usercache holds the correct name for any player who has joined, including
     * Bedrock/Floodgate players whose '.'-prefixed names Mojang can't resolve. It is read through
     * {@link PlayerProfile#completeFromCache()}, never {@code OfflinePlayer#getName()}: for a player
     * missing from the usercache that loads their playerdata file from disk on every call, along with
     * any plugin that hooks the load (MyWorlds upgrades the whole file). Every name found locally is
     * kept in {@code profiles.sqlite}, so a player who drops out of the usercache is still known
     * without asking Mojang. Paper guards the usercache with a lock, so all of this runs on
     * {@code asyncExecutor}.</p>
     */
    @NotNull
    public CompletableFuture<String> getUsername(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Profile kept = this.cache.getIfPresent(uuid);
                PlayerProfile local = this.server.createProfile(uuid);
                String localName = local.completeFromCache() ? local.getName() : null;
                if (localName != null && !localName.isEmpty()) {
                    if (kept == null || !localName.equals(kept.getName())) {
                        this.cache.put(new Profile(uuid, localName));
                    }
                    return localName;
                }
                if (kept != null && kept.getName() != null) {
                    return kept.getName();
                }
                Profile profile = this.mojang.findByUuid(uuid);
                if (profile != null && profile.getName() != null) {
                    this.cache.put(profile);
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
