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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        OptionalInt claimed = this.backend.claimPayout(payout.id());
        if (claimed.isEmpty()) {
            return;
        }
        // Read under the claim: the list may be stale.
        int attempts = claimed.getAsInt();
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
                if (attempts + 1 == RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS) {
                    this.log.severe("Sale payout #" + payout.id() + " of " + describe(payout) + " failed for the "
                            + RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS + "th time (" + failure.errorMessage()
                            + ") and will not be tried again. Pay it by hand from the holding account.");
                }
            }
        }
    }

    /**
     * Pays the refund an expired payment recorded, at once, through the same claim as a retry. The
     * refund row was written with the expiry, so a failure here leaves it pending for
     * {@link #retryRefunds()}.
     *
     * @param refundId the refund the expiry recorded, or null when nothing is owed back: a payment
     *                 begun before sale fees, whose instalments went to the seller, or nothing paid
     * @param amount   what the buyer had paid
     */
    public @NotNull ExpiryRefund settleExpiry(@Nullable Integer refundId, @NotNull UUID buyerId, double amount,
                                              @NotNull String regionName, @Nullable UUID worldId,
                                              @NotNull String reason) {
        if (refundId == null) {
            return new ExpiryRefund(0, false);
        }
        Optional<Party> from = this.holding.party();
        if (from.isEmpty()) {
            this.log.warning("A refund of " + CurrencyFormatter.format(amount) + " to " + buyerId + " for "
                    + regionName + " waits for a holding account");
            return new ExpiryRefund(amount, false);
        }
        boolean paid = retryRefund(from.get(), new DueRefund(refundId, buyerId, amount, regionName, worldId, reason, 0));
        return new ExpiryRefund(amount, paid);
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

    /** @return whether this call paid the refund */
    private boolean retryRefund(@NotNull Party from, @NotNull DueRefund refund) {
        OptionalInt claimed = this.backend.claimRefund(refund.id());
        if (claimed.isEmpty()) {
            return false;
        }
        int attempts = claimed.getAsInt();
        PaymentResult result = transfer(from, Party.personal(refund.buyerId()), refund.amount(),
                refundMessage(refund.reason(), refund.regionName()));
        switch (result) {
            case PaymentResult.Success ignored -> {
                markPaid(() -> this.backend.markRefundPaid(refund.id()), "Refund #" + refund.id() + " of "
                        + describe(refund));
                this.events.fireSync(new RefundPaidEvent(refund.buyerId(), refund.amount(), refund.regionName(),
                        refund.worldId(), refund.reason()));
                return true;
            }
            case PaymentResult.Failure failure -> {
                this.backend.markRefundFailed(refund.id(), failure.errorMessage());
                if (attempts + 1 == RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS) {
                    this.log.severe("Refund #" + refund.id() + " of " + describe(refund) + " failed for the "
                            + RealtyBackendImpl.MAX_PAYOUT_ATTEMPTS + "th time (" + failure.errorMessage()
                            + ") and will not be tried again. Pay it by hand from the holding account.");
                }
                return false;
            }
        }
    }

    /** How long an instalment may stay pending before it is reported as stuck, by the database clock. */
    public static final int STUCK_INSTALMENT_MINUTES = 10;

    /** The stuck instalments already reported, so each is reported once. */
    private final Set<String> reportedStuck = ConcurrentHashMap.newKeySet();

    /**
     * Logs SEVERE, once each, the instalments pending for longer than
     * {@link #STUCK_INSTALMENT_MINUTES}: counted, but never confirmed or rolled back, as after a
     * crash while the money moved. They are never credited or refunded automatically.
     */
    public void reportStuckInstalments() {
        for (RealtyBackend.StuckInstalment stuck : this.backend.stuckInstalments(STUCK_INSTALMENT_MINUTES)) {
            String key = stuck.kind() + ":" + stuck.saleId() + ":" + stuck.pendingSince();
            if (!this.reportedStuck.add(key)) {
                continue;
            }
            String table = stuck.kind() == RealtyBackend.PendingFinalize.Kind.BID
                    ? "FreeholdContractBidPayment (bidId " : "FreeholdContractOfferPayment (offerId ";
            this.log.severe("An instalment of " + CurrencyFormatter.format(stuck.amount()) + " by "
                    + stuck.buyerId() + " towards " + stuck.regionId() + " has been pending since "
                    + stuck.pendingSince() + ": its transfer may or may not have gone through. It is not credited"
                    + " or refunded automatically, and the payment deadline is suspended until an admin clears it."
                    + " Check the economy's ledger, then in " + table + stuck.saleId() + ") either add"
                    + " pendingPayment to currentPayment (the money arrived) or set it to 0 (it did not), and"
                    + " clear pendingSince.");
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
