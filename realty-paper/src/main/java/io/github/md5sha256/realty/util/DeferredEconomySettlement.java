package io.github.md5sha256.realty.util;

import org.jetbrains.annotations.NotNull;

/**
 * Holds the second half of an economy transfer until the database mutation has
 * committed, so failure paths can refund the payer without minting money.
 */
public final class DeferredEconomySettlement {

    private final Runnable refundPayer;
    private final Runnable payRecipient;
    private boolean completed;

    public DeferredEconomySettlement(@NotNull Runnable refundPayer,
                                     @NotNull Runnable payRecipient) {
        this.refundPayer = refundPayer;
        this.payRecipient = payRecipient;
    }

    public void refundPayer() {
        if (this.completed) {
            return;
        }
        this.refundPayer.run();
        this.completed = true;
    }

    public void settleRecipient() {
        if (this.completed) {
            return;
        }
        this.payRecipient.run();
        this.completed = true;
    }
}
