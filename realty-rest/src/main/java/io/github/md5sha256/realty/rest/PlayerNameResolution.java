package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.NameLookup;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Turning a player name into a {@link PlayerRef} through the query-service module.
 *
 * <p>Shared so that every route accepting a name answers a failure the same way: an
 * unknown name is the caller's mistake (404), an unreachable module is not (502).
 * The parameter name is passed in because the routes disagree on what they call it --
 * {@code player} on the listing routes, {@code name} on the lookup route -- and the
 * message should name the parameter the caller actually sent.</p>
 */
final class PlayerNameResolution {

    private PlayerNameResolution() {
    }

    /**
     * Reads the {@code playerId} / {@code playerName} pair a route was given.
     *
     * <p>They are separate parameters because they do not cost the same thing.
     * {@code playerId} is answered from the database alone and cannot fail on a module
     * outage; {@code playerName} needs the query-service module, so it can answer 404
     * or 502 where {@code playerId} never will. A single polymorphic {@code player}
     * hid that difference behind the shape of the value, leaving a caller unable to
     * tell from its own request whether the module was on the critical path -- and
     * leaving the OpenAPI document unable to say so either.</p>
     *
     * <p>Giving both is rejected rather than resolved by a precedence rule. A request
     * carrying two answers to one question is a caller mistake, and silently picking
     * one would hide it.</p>
     *
     * @param required whether one of the two must be present. False where the pair is
     *                 an optional filter, in which case absent means "do not filter"
     * @return the resolved player, or {@code null} only when {@code required} is false
     *         and neither parameter was given
     */
    static @Nullable PlayerRef fromRequest(@NotNull Context ctx,
                                           @NotNull ModuleClient moduleClient,
                                           boolean required) {
        String id = QueryParams.optional(ctx, "playerId");
        String name = QueryParams.optional(ctx, "playerName");
        if (id != null && name != null) {
            throw ApiException.badRequest("AMBIGUOUS_PARAMETER",
                    "Give either 'playerId' or 'playerName', not both");
        }
        if (id != null) {
            UUID parsed = parseId(id);
            return new PlayerRef(parsed.toString(), nameOf(moduleClient, parsed));
        }
        if (name != null) {
            return byName(moduleClient, name, "playerName");
        }
        if (required) {
            throw ApiException.badRequest("MISSING_PARAMETER",
                    "Query parameter 'playerId' or 'playerName' is required");
        }
        return null;
    }

    private static @NotNull UUID parseId(@NotNull String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("MALFORMED_UUID",
                    "Query parameter 'playerId' is not a valid UUID");
        }
    }

    /**
     * The display name for an id, or null when the module cannot supply one. Unlike a
     * name lookup this never fails the request: the name is enrichment, and every other
     * module-sourced field already degrades to null.
     */
    private static @Nullable String nameOf(@NotNull ModuleClient moduleClient, @NotNull UUID id) {
        return PlayerNames.resolve(moduleClient, List.of(id)).get(id);
    }

    static @NotNull PlayerRef byName(@NotNull ModuleClient moduleClient,
                                     @NotNull String name,
                                     @NotNull String parameterName) {
        return switch (moduleClient.uuidOf(name)) {
            case NameLookup.Resolved resolved -> new PlayerRef(resolved.id().toString(), resolved.name());
            case NameLookup.Unknown unknown -> throw ApiException.notFound("PLAYER_NOT_FOUND",
                    "No player named '" + name + "'");
            case NameLookup.Unavailable unavailable -> throw ApiException.badGateway(
                    "NAME_LOOKUP_UNAVAILABLE",
                    "Resolving query parameter '" + parameterName
                            + "' requires the query-service module, which is not reachable");
        };
    }

}
