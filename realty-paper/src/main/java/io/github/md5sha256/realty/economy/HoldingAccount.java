package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.DefaultParties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
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
 * <p>The account may exist and still be unusable, as when an admin archives Treasury's. While it
 * is unusable no instalment can be paid into it, so Realty pauses payment deadlines; see
 * {@link #usable()}.</p>
 *
 * <p>This class names no Treasury type, so a Vault-only server never loads one.</p>
 */
public final class HoldingAccount {

    /** Whether the holding account can take and pay out money now. */
    public sealed interface Status permits Usable, Unusable {
    }

    /** The holding account can be used, and is {@code party}. */
    public record Usable(@NotNull Party party) implements Status {
    }

    /** The holding account cannot be used, for {@code reason}, which is shown to admins. */
    public record Unusable(@NotNull String reason) implements Status {
    }

    /** How long a check that asks the economy is reused before it is asked again. */
    static final Duration RECHECK_AFTER = Duration.ofSeconds(5);

    private final Supplier<Optional<Party>> party;
    private final Supplier<Status> check;
    private final @Nullable LongSupplier nanoClock;
    private volatile @Nullable CachedStatus cached;

    private record CachedStatus(@NotNull Status status, long checkedAt) {
    }

    /**
     * @param nanoClock when the check asks the economy, the clock its result is cached by; null
     *                  to check on each use, for a check that does no I/O
     */
    private HoldingAccount(@NotNull Supplier<Optional<Party>> party, @NotNull Supplier<Status> check,
                           @Nullable LongSupplier nanoClock) {
        this.party = party;
        this.check = check;
        this.nanoClock = nanoClock;
    }

    /**
     * A fixed holding party that is always usable. Treasury's account is set up with a check; see
     * {@link TreasuryHoldingAccount#create}.
     */
    public static @NotNull HoldingAccount of(@NotNull Party party) {
        Optional<Party> fixed = Optional.of(party);
        Status usable = new Usable(party);
        return new HoldingAccount(() -> fixed, () -> usable, null);
    }

    /**
     * A fixed holding party whose usability {@code check} decides, as it asks the economy: each
     * result is reused for {@link #RECHECK_AFTER} by {@code nanoClock}.
     */
    static @NotNull HoldingAccount checked(@NotNull Party party, @NotNull Supplier<Status> check,
                                           @NotNull LongSupplier nanoClock) {
        Optional<Party> fixed = Optional.of(party);
        return new HoldingAccount(() -> fixed, check, nanoClock);
    }

    /**
     * The {@code escrow} player of the default parties, read on each use so that a reload of
     * {@code settings.yml} takes effect. Usable while one is set.
     */
    public static @NotNull HoldingAccount vault(@NotNull Supplier<DefaultParties> defaults) {
        Supplier<Optional<Party>> escrow = () -> Optional.ofNullable(defaults.get().escrow()).map(Party::personal);
        return new HoldingAccount(escrow, () -> escrow.get()
                .<Status>map(Usable::new)
                .orElseGet(() -> new Unusable("no escrow player is set in settings.yml")), null);
    }

    /** No holding account, as when Treasury's could not be set up, for {@code reason}. */
    public static @NotNull HoldingAccount none(@NotNull String reason) {
        Status unusable = new Unusable(reason);
        return new HoldingAccount(Optional::empty, () -> unusable, null);
    }

    /**
     * The holding party, or empty when there is none, so no sale may move to payment. A party
     * that exists may still be unusable; see {@link #usable()}.
     */
    public @NotNull Optional<Party> party() {
        return this.party.get();
    }

    /**
     * Whether the holding account can be used now. With Treasury this asks Treasury whether the
     * account still exists, is not archived, is still a SYSTEM account and needs no authorization;
     * the answer is reused for a few seconds, so hot paths ask Treasury at most that often.
     */
    public @NotNull Status usable() {
        LongSupplier clock = this.nanoClock;
        if (clock == null) {
            return this.check.get();
        }
        long now = clock.getAsLong();
        CachedStatus last = this.cached;
        if (last != null && now - last.checkedAt() < RECHECK_AFTER.toNanos()) {
            return last.status();
        }
        Status status = this.check.get();
        this.cached = new CachedStatus(status, now);
        return status;
    }
}
