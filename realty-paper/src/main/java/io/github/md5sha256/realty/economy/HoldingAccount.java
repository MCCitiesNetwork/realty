package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.DefaultParties;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The party that holds a buyer's auction and offer instalments until the sale finishes, so that a
 * sale that falls through can be refunded. It belongs to neither the buyer nor the seller.
 *
 * <p>With Treasury it is Realty's own SYSTEM account, with overdraft off so that a bug cannot pay
 * out money the account does not hold. Vault can move money only between players, so on Vault it
 * is the player an admin names as {@code escrow} in {@code settings.yml}, and there is none until
 * one is named.</p>
 *
 * <p>This class names no Treasury type, so a Vault-only server never loads one.</p>
 */
public final class HoldingAccount {

    private final Supplier<Optional<Party>> party;

    private HoldingAccount(@NotNull Supplier<Optional<Party>> party) {
        this.party = party;
    }

    /**
     * A fixed holding party, such as Realty's Treasury SYSTEM account; see
     * {@link TreasuryHoldingAccount#create}.
     */
    public static @NotNull HoldingAccount of(@NotNull Party party) {
        Optional<Party> fixed = Optional.of(party);
        return new HoldingAccount(() -> fixed);
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
