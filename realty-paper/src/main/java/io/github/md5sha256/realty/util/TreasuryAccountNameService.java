package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.AccountNameService;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * {@link AccountNameService} over Treasury.
 *
 * <p>Looking up an account is a blocking database read, so it runs on the database executor and
 * never on the caller's thread. Without Treasury every name is empty. Unlike {@link PartyNames},
 * this keeps no cache and adds no kind suffix: its callers ask for the bare name.</p>
 */
public final class TreasuryAccountNameService implements AccountNameService {

    private final @Nullable TreasuryApi treasury;
    private final Executor dbExecutor;

    public TreasuryAccountNameService(@Nullable TreasuryApi treasury, @NotNull Executor dbExecutor) {
        this.treasury = treasury;
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> nameOf(int accountId) {
        if (this.treasury == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            return CompletableFuture.supplyAsync(() -> lookUp(this.treasury, accountId), this.dbExecutor)
                    .exceptionally(ex -> Optional.empty());
        } catch (RuntimeException ex) {
            // The executor refused the task, for instance while shutting down.
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static @NotNull Optional<String> lookUp(@NotNull TreasuryApi treasury, int accountId) {
        Account account = treasury.getAccountById(accountId);
        return account == null ? Optional.empty() : Optional.ofNullable(account.getDisplayName());
    }
}
