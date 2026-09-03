package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.NamesRequest;
import io.github.md5sha256.realty.adapter.query.json.PlayerName;
import io.github.md5sha256.realty.adapter.query.json.UuidsRequest;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The player routes. Batch forms exist because a ten-region response resolved one name at a
 * time would turn an N+1 removed from SQL into an N+1 over the network. Bodies rather than query
 * strings, because a Floodgate name such as {@code .Cool Guy 123} is not reliably URL-safe.
 */
final class PlayerNamesHandler {

    private final PlayerNameService names;
    private final Duration timeout;

    PlayerNamesHandler(@NotNull PlayerNameService names, @NotNull Duration timeout) {
        this.names = Objects.requireNonNull(names, "names");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void single(@NotNull Context ctx) {
        UUID id = parseUuid(ctx.pathParam("uuid"));
        Optional<String> name = join(this.names.nameOf(id));
        ctx.json(new PlayerName(id.toString(), name.orElse(null)));
    }

    void names(@NotNull Context ctx) {
        NamesRequest request = Bodies.read(ctx, NamesRequest.class);
        if (request.ids() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"ids\":[...]}");
        }
        Bodies.requireWithinBatchLimit(request.ids().size());
        List<UUID> ids = new ArrayList<>(request.ids().size());
        for (String raw : request.ids()) {
            ids.add(parseUuid(raw));
        }
        Map<UUID, Optional<String>> resolved = join(this.names.namesOf(ids));
        List<PlayerName> players = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            players.add(new PlayerName(id.toString(), resolved.get(id).orElse(null)));
        }
        ctx.json(Map.of("players", players));
    }

    void uuids(@NotNull Context ctx) {
        UuidsRequest request = Bodies.read(ctx, UuidsRequest.class);
        if (request.names() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"names\":[...]}");
        }
        Bodies.requireWithinBatchLimit(request.names().size());
        for (String name : request.names()) {
            if (name == null) {
                throw ApiException.badRequest("INVALID_BODY", "Names must not contain null");
            }
        }
        Map<String, Optional<UUID>> resolved = join(this.names.uuidsOf(request.names()));
        List<PlayerName> players = new ArrayList<>(request.names().size());
        for (String name : request.names()) {
            players.add(new PlayerName(resolved.get(name).map(UUID::toString).orElse(null), name));
        }
        ctx.json(Map.of("players", players));
    }

    /**
     * Name resolution hops to the main thread and may fall through to Mojang, so it is bounded by
     * the same request budget the geometry route uses rather than pinning a worker indefinitely.
     */
    private <T> T join(@NotNull CompletableFuture<T> future) {
        return Futures.joinWithin(future, this.timeout, ApiException.UPSTREAM_TIMEOUT);
    }

    private static @NotNull UUID parseUuid(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("INVALID_UUID", "Not a UUID: null");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_UUID", "Not a UUID: " + raw);
        }
    }
}
