package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.RealtyBackend;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the holding account on each scheduler run. While it cannot be used nobody can pay an
 * instalment, so the deadline of every payment that pays into it is paused, and no buyer is
 * expired by an outage they could not pay through. Admins are told once when it becomes unusable
 * and once when it recovers.
 */
public final class HoldingWatch {

    private final HoldingAccount holding;
    private final RealtyBackend backend;
    private final Logger logger;
    /**
     * Whether the last run found the account usable; assumed so before the first. Volatile: the
     * async timer may run each check on a different thread.
     */
    private volatile boolean wasUsable = true;

    public HoldingWatch(@NotNull HoldingAccount holding, @NotNull RealtyBackend backend, @NotNull Logger logger) {
        this.holding = holding;
        this.backend = backend;
        this.logger = logger;
    }

    /**
     * Checks the holding account and, while it is unusable, pauses payment deadlines. Run before
     * expired payments are cleared, so none expires for want of a usable account. Called by one
     * scheduler run at a time, though not always on the same thread.
     */
    public @NotNull HoldingAccount.Status check() {
        HoldingAccount.Status status = this.holding.usable();
        switch (status) {
            case HoldingAccount.Unusable unusable -> {
                if (this.wasUsable) {
                    this.logger.severe("Realty's holding account cannot be used: " + unusable.reason()
                            + ". Until it is fixed, payment deadlines are paused, buyers cannot pay, auctions"
                            + " do not move to payment and offers cannot be accepted.");
                }
                pauseDeadlines();
            }
            case HoldingAccount.Usable ignored -> {
                if (!this.wasUsable) {
                    this.logger.info("Realty's holding account can be used again; payment deadlines run again"
                            + " from where they were paused.");
                }
            }
        }
        this.wasUsable = status instanceof HoldingAccount.Usable;
        return status;
    }

    private void pauseDeadlines() {
        try {
            this.backend.pausePaymentDeadlines();
        } catch (RuntimeException ex) {
            // Expiry skips new-rules payments while the account is unusable, so none is lost.
            this.logger.log(Level.WARNING, "Could not pause payment deadlines; tried again on the next run", ex);
        }
    }
}
