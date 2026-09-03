package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.NameLookup;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
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
     * Resolves a parameter that may be either a UUID or a player name.
     *
     * <p>A UUID-shaped value that fails to parse is a malformed UUID (400) rather than
     * a name to look up: a caller who sent 36 characters with hyphens in the standard
     * places meant a UUID, and looking it up as a name would answer the less useful
     * "no player called that".</p>
     */
    static @NotNull PlayerRef byUuidOrName(@NotNull ModuleClient moduleClient,
                                           @NotNull String param,
                                           @NotNull String parameterName) {
        if (isUuidShaped(param)) {
            UUID id;
            try {
                id = UUID.fromString(param);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("MALFORMED_UUID",
                        "Query parameter '" + parameterName + "' is not a valid UUID");
            }
            return Objects.requireNonNull(
                    PlayerNames.ref(id, PlayerNames.resolve(moduleClient, List.of(id))));
        }
        return byName(moduleClient, param, parameterName);
    }

    private static boolean isUuidShaped(@NotNull String value) {
        return value.length() == 36
                && value.charAt(8) == '-'
                && value.charAt(13) == '-'
                && value.charAt(18) == '-'
                && value.charAt(23) == '-';
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
