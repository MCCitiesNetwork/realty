package io.github.md5sha256.realty.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class DeferredEconomySettlementTest {

    @Test
    @DisplayName("refundPayer runs only once")
    void refundRunsOnlyOnce() {
        AtomicInteger refunds = new AtomicInteger();
        AtomicInteger recipientPayments = new AtomicInteger();
        DeferredEconomySettlement settlement = new DeferredEconomySettlement(
                refunds::incrementAndGet,
                recipientPayments::incrementAndGet);

        settlement.refundPayer();
        settlement.refundPayer();

        Assertions.assertEquals(1, refunds.get());
        Assertions.assertEquals(0, recipientPayments.get());
    }

    @Test
    @DisplayName("settleRecipient runs only once")
    void settleRunsOnlyOnce() {
        AtomicInteger refunds = new AtomicInteger();
        AtomicInteger recipientPayments = new AtomicInteger();
        DeferredEconomySettlement settlement = new DeferredEconomySettlement(
                refunds::incrementAndGet,
                recipientPayments::incrementAndGet);

        settlement.settleRecipient();
        settlement.settleRecipient();

        Assertions.assertEquals(0, refunds.get());
        Assertions.assertEquals(1, recipientPayments.get());
    }

    @Test
    @DisplayName("first terminal action wins")
    void firstActionWins() {
        AtomicInteger refunds = new AtomicInteger();
        AtomicInteger recipientPayments = new AtomicInteger();
        DeferredEconomySettlement settlement = new DeferredEconomySettlement(
                refunds::incrementAndGet,
                recipientPayments::incrementAndGet);

        settlement.refundPayer();
        settlement.settleRecipient();

        Assertions.assertEquals(1, refunds.get());
        Assertions.assertEquals(0, recipientPayments.get());
    }
}
