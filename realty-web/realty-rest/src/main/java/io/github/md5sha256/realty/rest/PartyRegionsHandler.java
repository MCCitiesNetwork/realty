package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PartyNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /v1/parties/{kind}/{id}/regions} -- the regions of any party, addressed by
 * kind and id.
 *
 * <p>Both path parameters are text from the network. An error message never repeats them,
 * so a caller cannot get its own text echoed back or written to a log.</p>
 */
final class PartyRegionsHandler {

    private final RealtyBackend backend;
    private final PartyRegionsListing listing;
    private final ModuleClient moduleClient;

    PartyRegionsHandler(@NotNull RealtyBackend backend,
                        @NotNull PartyRegionsListing listing,
                        @NotNull ModuleClient moduleClient) {
        this.backend = backend;
        this.listing = listing;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        Addressed addressed = party(ctx.pathParam("kind"), ctx.pathParam("id"));
        Party party = addressed.party();
        // An account Realty does not store is not named. The module can name any Treasury
        // account, and would name it under whatever kind the caller wrote.
        PartyRef ref = addressed.stored()
                ? PartyNames.resolve(this.moduleClient, List.of(party)).ref(party)
                : new PartyRef(PartyNames.kind(party), PartyNames.id(party), null);
        this.listing.respond(ctx, party, ref);
    }

    /** The party the path names, and whether Realty stores it. */
    private record Addressed(@NotNull Party party, boolean stored) {}

    private @NotNull Addressed party(@NotNull String kind, @NotNull String id) {
        return switch (kind) {
            case "personal" -> new Addressed(Party.personal(playerId(id)), true);
            case "business" -> account(accountId(id), AccountKind.BUSINESS);
            case "government" -> account(accountId(id), AccountKind.GOVERNMENT);
            case "system" -> account(accountId(id), AccountKind.SYSTEM);
            case "group" -> {
                // Group names are stored in lower case; the backend matches without regard to case.
                Party.Group group = this.backend.findGroupParty(id);
                if (group == null) {
                    throw ApiException.notFound("PARTY_NOT_FOUND", "No group is mapped under that name");
                }
                yield new Addressed(group, true);
            }
            default -> throw ApiException.badRequest("INVALID_PARTY_KIND",
                    "Path parameter 'kind' must be one of [personal, business, government, system, group]");
        };
    }

    /**
     * An account that no contract names is listed as the kind asked for, with nothing in it:
     * this API cannot ask Treasury whether it exists. One stored under another kind is not a
     * party of the kind asked for.
     */
    private @NotNull Addressed account(int accountId, @NotNull AccountKind kind) {
        Party.Account stored = this.backend.findAccountParty(accountId);
        if (stored == null) {
            return new Addressed(Party.account(accountId, kind), false);
        }
        if (stored.kind() != kind) {
            throw ApiException.notFound("PARTY_NOT_FOUND", "No party of that kind has that id");
        }
        return new Addressed(stored, true);
    }

    /** UUID.fromString alone also accepts short forms such as {@code 1-1-1-1-1}; only the full form is an id. */
    private static @NotNull UUID playerId(@NotNull String id) {
        if (id.length() == 36) {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException ex) {
                // Falls through to the error below.
            }
        }
        throw ApiException.badRequest("MALFORMED_UUID", "Path parameter 'id' is not a valid UUID");
    }

    /** An account id is a positive integer written in digits only: no sign, no spaces. */
    private static int accountId(@NotNull String id) {
        boolean digits = !id.isEmpty() && id.chars().allMatch(c -> c >= '0' && c <= '9');
        if (digits) {
            try {
                int accountId = Integer.parseInt(id);
                if (accountId > 0) {
                    return accountId;
                }
            } catch (NumberFormatException ex) {
                // Too large for an account id: falls through to the error below.
            }
        }
        throw ApiException.badRequest("INVALID_ACCOUNT_ID", "Path parameter 'id' must be a positive integer");
    }

}
