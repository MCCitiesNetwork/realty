package io.github.md5sha256.realty.util;

import org.enginehub.squirrelid.Profile;
import org.enginehub.squirrelid.cache.ProfileCache;
import org.enginehub.squirrelid.cache.SQLiteCache;
import org.enginehub.squirrelid.resolver.CacheForwardingService;
import org.enginehub.squirrelid.resolver.CombinedProfileService;
import org.enginehub.squirrelid.resolver.HttpRepositoryService;
import org.enginehub.squirrelid.resolver.PaperPlayerService;
import org.enginehub.squirrelid.resolver.ProfileService;
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

    public SquirrelIdUsernameResolver(@NotNull File cacheFile,
                                      @NotNull Executor asyncExecutor) throws IOException {
        this.cache = new SQLiteCache(cacheFile);
        this.service = new CacheForwardingService(
                new CombinedProfileService(
                        PaperPlayerService.getInstance(),
                        HttpRepositoryService.forMinecraft()),
                this.cache);
        this.asyncExecutor = asyncExecutor;
    }

    @NotNull
    public Optional<String> getUsernameIfCached(@NotNull UUID uuid) {
        Profile profile = this.cache.getIfPresent(uuid);
        return Optional.ofNullable(profile).map(Profile::getName);
    }

    @NotNull
    public CompletableFuture<String> getUsername(@NotNull UUID uuid) {
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

}
