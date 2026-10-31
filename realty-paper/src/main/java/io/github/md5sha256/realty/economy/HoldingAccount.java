package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.DefaultParties;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The party that holds a buyer's auction and offer instalments until the sale finishes, so that a
 * sale that falls through can be refunded. It belongs to neither the buyer nor the seller.
 *
 * <p>With Treasury it is Realty's own SYSTEM account, with overdraft off so that a bug cannot pay
 * out money the account does not hold. Vault can move money only between players, so on Vault it
 * is the player an admin names as {@code escrow} in {@code settings.yml}, and there is none until
 * one is named.</p>
 */
public final class HoldingAccount {

    /** The owner of Realty's SYSTEM account. No player has this id. */
    static final UUID TREASURY_OWNER = UUID.nameUUIDFromBytes("realty:holding".getBytes(StandardCharsets.UTF_8));
    static final String TREASURY_NAME = "Realty Holding";

    private final Supplier<Optional<Party>> party;

    private HoldingAccount(@NotNull Supplier<Optional<Party>> party) {
        this.party = party;
    }

    /**
     * Finds Realty's SYSTEM account, or creates it. An account found with overdraft allowed has it
     * turned off. Does Treasury I/O.
     */
    public static @NotNull HoldingAccount treasury(@NotNull TreasuryApi api, @NotNull Logger log) {
        List<Account> existing = api.getAccountsByTypeAndOwner(AccountType.SYSTEM, TREASURY_OWNER).stream()
                .filter(account -> !account.isArchived())
                .toList();
        Account account;
        if (existing.isEmpty()) {
            account = api.createAccount(AccountType.SYSTEM, TREASURY_OWNER, TREASURY_NAME);
            log.info("Created the Treasury account '" + TREASURY_NAME + "' (#" + account.getAccountId()
                    + ") to hold instalments until a sale finishes");
        } else {
            account = existing.getFirst();
        }
        if (account.isAllowOverdraft()) {
            account.setAllowOverdraft(false);
            api.updateAccount(account);
            log.warning("The Treasury account '" + TREASURY_NAME + "' (#" + account.getAccountId()
                    + ") allowed overdraft; it has been turned off, so it can never pay out more than it holds");
        }
        Party holding = Party.account(account.getAccountId(), AccountKind.SYSTEM);
        return new HoldingAccount(() -> Optional.of(holding));
    }

    /**
     * The {@code escrow} player of the default parties, read on each use so that a reload of
     * {@code settings.yml} takes effect.
     */
    public static @NotNull HoldingAccount vault(@NotNull Supplier<DefaultParties> defaults) {
        return new HoldingAccount(() -> Optional.ofNullable(defaults.get().escrow()).map(Party::personal));
    }

    /** No holding account, as when Treasury's could not be set up. */
    public static @NotNull HoldingAccount none() {
        return new HoldingAccount(Optional::empty);
    }

    /** The holding party, or empty when there is none, so no sale may move to payment. */
    public @NotNull Optional<Party> party() {
        return this.party.get();
    }
}
