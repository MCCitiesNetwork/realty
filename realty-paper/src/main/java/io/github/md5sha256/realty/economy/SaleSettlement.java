package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.DuePayout;
import io.github.md5sha256.realty.api.RealtyBackend.DueRefund;
import io.github.md5sha256.realty.api.event.FeePaidEvent;
import io.github.md5sha256.realty.api.event.RefundPaidEvent;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves the money held for sales out of the holding account: the payouts of a completed sale to
 * its seller and fee recipients, and refunds to buyers whose sale fell through.
 *
 * <p>Every payout and refund is claimed in the database before its transfer, so two runs never pay
 * the same line. A transfer that fails is recorded and retried by the scheduler; one that succeeds
 * but whose record then fails stays claimed and is never paid again automatically, because whether
 * the money moved is then known only from the economy. {@link #reportStuck()} lists those for an
 * admin.</p>
 *
 * <p>Treasury does I/O, so every method here belongs off the main thread.</p>
 */
public final class SaleSettlement {

    private static final String NO_HOLDING = "no holding account";

    private final EconomyProvider economy;
    private final RealtyBackend backend;
    private final HoldingAccount holding;
    private final RealtyEventDispatch events;
    private final Logger log;

    public SaleSettlement(@NotNull EconomyProvider economy,
                          @NotNull RealtyBackend backend,
                          @NotNull HoldingAccount holding,
                          @NotNull RealtyEventDispatch events,
                          @NotNull Logger log) {
        this.economy = economy;
        this.backend = backend;
        this.holding = holding;
        this.events = events;
        this.log = log;
    }

    /**
     * What an expired payment refunded.
     *
     * @param amount what is owed back to the buyer; zero under the old rules
     * @param paid   whether it was paid now, rather than recorded for a retry
     */
    public record ExpiryRefund(double amount, boolean paid) {}

    /**
     * Pays every payout line due, one at a time. Without a holding account nothing is claimed, and
     * the lines stay due.
     */
    public void payOutDue() {
        Optional<Party> from = this.holding.party();
        if (from.isEmpty()) {
            return;
        }
        List<DuePayout> due;
        try {
            due = this.backend.duePayouts();
        } catch (RuntimeException ex) {
            this.log.log(Level.WARNING, "Could not read the sale payouts due; they will be tried again", ex);
            return;
        }
        for (DuePayout payout : due) {
            try {
                payOut(from.get(), payout);
            } catch (RuntimeException ex) {
                this.log.log(Level.SEVERE, "Sale payout #" + payout.id() + " of " + describe(payout)
                        + " was interrupted; check whether it was paid", ex);
            }
        }
    }

    private void payOut(@NotNull Party from, @NotNull DuePayout payout) {
        if (!this.backend.claimPayout(payout.id())) {
            return;
        }
        PaymentResult result = payout.amount() > 0
                ? transfer(from, payout.recipient(), payout.amount(),
                        "Sale payout (" + payout.source() + "): " + payout.regionName())
                : new PaymentResult.Success();
        switch (result) {
            case PaymentResult.Success ignored -> {
                markPaid(() -> this.backend.markPayoutPaid(payout.id()),
                        "Sale payout #" + payout.id() + " of " + describe(payout));
                if ("fee".equals(payout.lineKind())) {
                    this.events.fireSync(new FeePaidEvent(payout.regionName(), payout.worldId(), payout.source(),
                            payout.recipient(), payout.amount(), payout.buyerId()));
                }
            }
            case PaymentResult.Failure failure -> {
                this.backend.markPayoutFailed(payout.id(), failure.errorMessage());
                if (payout.attempts() + 1 == RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS) {
                    this.log.severe("Sale payout #" + payout.id() + " of " + describe(payout) + " failed for the "
                            + RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS + "th time (" + failure.errorMessage()
                            + ") and will not be tried again. Pay it by hand from the holding account.");
                }
            }
        }
    }

    /**
     * Refunds an expired payment's held instalments. A payment begun before sale fees refunds
     * nothing: its instalments went to the seller.
     */
    public @NotNull ExpiryRefund refundExpired(@NotNull UUID buyerId, double paid, boolean oldRules,
                                               @NotNull String regionName, @Nullable UUID worldId,
                                               @NotNull String reason) {
        if (oldRules || paid <= 0) {
            return new ExpiryRefund(0, false);
        }
        return new ExpiryRefund(paid, refund(buyerId, paid, regionName, worldId, reason));
    }

    /**
     * Pays {@code amount} from the holding account back to the buyer. A refund that cannot be paid
     * now is recorded as pending, to be retried.
     *
     * @return whether the refund was paid now
     */
    public boolean refund(@NotNull UUID buyerId, double amount, @NotNull String regionName,
                          @Nullable UUID worldId, @NotNull String reason) {
        Optional<Party> from = this.holding.party();
        PaymentResult result = from.isEmpty()
                ? new PaymentResult.Failure(NO_HOLDING)
                : transfer(from.get(), Party.personal(buyerId), amount, refundMessage(reason, regionName));
        if (result instanceof PaymentResult.Failure failure) {
            this.backend.recordPendingRefund(buyerId, amount, regionName, worldId, reason, failure.errorMessage());
            this.log.warning("A refund of " + CurrencyFormatter.format(amount) + " to " + buyerId + " for "
                    + regionName + " failed (" + failure.errorMessage() + "); it will be tried again");
            return false;
        }
        this.events.fireSync(new RefundPaidEvent(buyerId, amount, regionName, worldId, reason));
        return true;
    }

    /** Pays every pending refund due, one at a time. */
    public void retryRefunds() {
        Optional<Party> from = this.holding.party();
        if (from.isEmpty()) {
            return;
        }
        List<DueRefund> due;
        try {
            due = this.backend.dueRefunds();
        } catch (RuntimeException ex) {
            this.log.log(Level.WARNING, "Could not read the refunds due; they will be tried again", ex);
            return;
        }
        for (DueRefund refund : due) {
            try {
                retryRefund(from.get(), refund);
            } catch (RuntimeException ex) {
                this.log.log(Level.SEVERE, "Refund #" + refund.id() + " of " + describe(refund)
                        + " was interrupted; check whether it was paid", ex);
            }
        }
    }

    private void retryRefund(@NotNull Party from, @NotNull DueRefund refund) {
        if (!this.backend.claimRefund(refund.id())) {
            return;
        }
        PaymentResult result = transfer(from, Party.personal(refund.buyerId()), refund.amount(),
                refundMessage(refund.reason(), refund.regionName()));
        switch (result) {
            case PaymentResult.Success ignored -> {
                markPaid(() -> this.backend.markRefundPaid(refund.id()), "Refund #" + refund.id() + " of "
                        + describe(refund));
                this.events.fireSync(new RefundPaidEvent(refund.buyerId(), refund.amount(), refund.regionName(),
                        refund.worldId(), refund.reason()));
            }
            case PaymentResult.Failure failure -> {
                this.backend.markRefundFailed(refund.id(), failure.errorMessage());
                if (refund.attempts() + 1 == RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS) {
                    this.log.severe("Refund #" + refund.id() + " of " + describe(refund) + " failed for the "
                            + RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS + "th time (" + failure.errorMessage()
                            + ") and will not be tried again. Pay it by hand from the holding account.");
                }
            }
        }
    }

    /**
     * Logs, once, the payouts and refunds left claimed by a run that never finished, as after a
     * crash. Whether their money moved is unknown, so an admin must check each against the
     * economy's ledger.
     */
    public void reportStuck() {
        List<DuePayout> payouts = this.backend.stuckPayouts();
        List<DueRefund> refunds = this.backend.stuckRefunds();
        if (payouts.isEmpty() && refunds.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder("These sale payments were being paid when Realty last stopped,"
                + " and whether the money moved is unknown. Check each in the economy's ledger; they are not"
                + " retried automatically:");
        for (DuePayout payout : payouts) {
            message.append("\n  payout #").append(payout.id()).append(": ").append(describe(payout));
        }
        for (DueRefund refund : refunds) {
            message.append("\n  refund #").append(refund.id()).append(": ").append(describe(refund));
        }
        this.log.severe(message.toString());
    }

    private @NotNull PaymentResult transfer(@NotNull Party from, @NotNull Party to, double amount,
                                            @NotNull String ledgerMessage) {
        try {
            // No player caused a payout or a refund, so it has no initiator.
            return this.economy.transfer(from, to, amount, ledgerMessage, null);
        } catch (RuntimeException ex) {
            return new PaymentResult.Failure(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    /** Records a payment made; a failure to record it leaves the row claimed, so it is not paid twice. */
    private void markPaid(@NotNull Runnable mark, @NotNull String what) {
        try {
            mark.run();
        } catch (RuntimeException ex) {
            this.log.log(Level.SEVERE, what + " was paid, but recording it failed. It stays claimed and will"
                    + " not be paid again; mark it paid by hand.", ex);
        }
    }

    private static @NotNull String refundMessage(@NotNull String reason, @NotNull String regionName) {
        return "Sale refund (" + reason + "): " + regionName;
    }

    private static @NotNull String describe(@NotNull DuePayout payout) {
        return CurrencyFormatter.format(payout.amount()) + " (" + payout.lineKind() + ", " + payout.source()
                + ") to " + payout.recipient() + " for " + payout.regionName() + ", bought by " + payout.buyerId();
    }

    private static @NotNull String describe(@NotNull DueRefund refund) {
        return CurrencyFormatter.format(refund.amount()) + " to " + refund.buyerId() + " for "
                + refund.regionName() + " (" + refund.reason() + ")";
    }
}
