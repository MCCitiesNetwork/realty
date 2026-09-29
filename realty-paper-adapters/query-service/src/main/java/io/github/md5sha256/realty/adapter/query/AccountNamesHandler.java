package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.AccountIdsRequest;
import io.github.md5sha256.realty.adapter.query.json.AccountName;
import io.github.md5sha256.realty.api.AccountNameService;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The account route. Names are returned as JSON strings and escaped by the JSON library; they may
 * have been chosen by a player, so they are never interpreted here.
 */
final class AccountNamesHandler {

    private final AccountNameService names;
    private final Duration timeout;

    AccountNamesHandler(@NotNull AccountNameService names, @NotNull Duration timeout) {
        this.names = Objects.requireNonNull(names, "names");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void names(@NotNull Context ctx) {
        AccountIdsRequest request = Bodies.read(ctx, AccountIdsRequest.class);
        if (request.ids() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"ids\":[...]}");
        }
        Bodies.requireWithinBatchLimit(request.ids().size());
        List<Integer> ids = new ArrayList<>(request.ids().size());
        for (Object raw : request.ids()) {
            if (!(raw instanceof Integer id)) {
                throw ApiException.badRequest("INVALID_ACCOUNT_ID", "Not an account id: " + raw);
            }
            ids.add(id);
        }
        // Bounded by the request budget: the lookup is a database read on another thread.
        Map<Integer, Optional<String>> resolved = Futures.joinWithin(
                this.names.namesOf(ids), this.timeout, ApiException.UPSTREAM_TIMEOUT);
        List<AccountName> accounts = new ArrayList<>(ids.size());
        for (int id : ids) {
            accounts.add(new AccountName(id, resolved.get(id).orElse(null)));
        }
        ctx.json(Map.of("accounts", accounts));
    }
}
