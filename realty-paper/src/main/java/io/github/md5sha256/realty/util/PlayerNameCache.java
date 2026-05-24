package io.github.md5sha256.realty.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PlayerNameCache {

    private final Server server;

    private final Cache<UUID, String> nameCache;

    public PlayerNameCache(@NotNull Server server) {
        this.server = server;
        this.nameCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    private String lookupUsername(@NotNull UUID uuid) {
        try {
            PlayerProfile profile = this.server.createProfile(uuid);
            if (profile.complete(false, true)) {
                return profile.getName();
            }
            return uuid.toString();
        } catch (Exception ex) {
            return uuid.toString();
        }
    }

    @NotNull
    public Optional<String> getUsernameIfCached(@NotNull UUID uuid) {
        return Optional.ofNullable(this.nameCache.getIfPresent(uuid));
    }

    @NotNull
    public CompletableFuture<String> getUsername(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return this.nameCache.get(uuid, () -> lookupUsername(uuid));
                    } catch (ExecutionException ex) {
                        return uuid.toString();
                    }
                }
        );
    }

}
