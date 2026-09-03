package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.NamesRequest;
import io.github.md5sha256.realty.adapter.query.json.PlayerName;
import io.github.md5sha256.realty.adapter.query.json.UuidsRequest;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The player routes. Batch forms exist because a ten-region response resolved one name at a
 * time would turn an N+1 removed from SQL into an N+1 over the network. Bodies rather than query
 * strings, because a Floodgate name such as {@code .Cool Guy 123} is not reliably URL-safe.
 */
final class PlayerNamesHandler {

    private final PlayerNameService names;

    PlayerNamesHandler(@NotNull PlayerNameService names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    void single(@NotNull Context ctx) {
        UUID id = parseUuid(ctx.pathParam("uuid"));
        Optional<String> name = this.names.nameOf(id).join();
        ctx.json(new PlayerName(id.toString(), name.orElse(null)));
    }

    void names(@NotNull Context ctx) {
        NamesRequest request = body(ctx, NamesRequest.class);
        if (request.ids() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"ids\":[...]}");
        }
        List<UUID> ids = new ArrayList<>(request.ids().size());
        for (String raw : request.ids()) {
            ids.add(parseUuid(raw));
        }
        Map<UUID, Optional<String>> resolved = this.names.namesOf(ids).join();
        List<PlayerName> players = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            players.add(new PlayerName(id.toString(), resolved.get(id).orElse(null)));
        }
        ctx.json(Map.of("players", players));
    }

    void uuids(@NotNull Context ctx) {
        UuidsRequest request = body(ctx, UuidsRequest.class);
        if (request.names() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"names\":[...]}");
        }
        Map<String, Optional<UUID>> resolved = this.names.uuidsOf(request.names()).join();
        List<PlayerName> players = new ArrayList<>(request.names().size());
        for (String name : request.names()) {
            players.add(new PlayerName(resolved.get(name).map(UUID::toString).orElse(null), name));
        }
        ctx.json(Map.of("players", players));
    }

    private static <T> @NotNull T body(@NotNull Context ctx, @NotNull Class<T> type) {
        try {
            return ctx.bodyAsClass(type);
        } catch (Exception ex) {
            throw ApiException.badRequest("INVALID_BODY", "Body is not valid JSON for this route");
        }
    }

    private static @NotNull UUID parseUuid(@NotNull String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_UUID", "Not a UUID: " + raw);
        }
    }
}
