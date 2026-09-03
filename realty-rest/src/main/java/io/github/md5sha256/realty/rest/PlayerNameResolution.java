package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.NameLookup;
import org.jetbrains.annotations.NotNull;

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
