package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.DuePayout;
import io.github.md5sha256.realty.api.RealtyBackend.DueRefund;
import io.github.md5sha256.realty.api.event.AuctionPaymentExpiredEvent;
import io.github.md5sha256.realty.api.event.FeePaidEvent;
import io.github.md5sha256.realty.api.event.OfferPaymentExpiredEvent;
import io.github.md5sha256.realty.api.event.RefundPaidEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.settings.DefaultParties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleSettlementTest {

    private static final UUID ESCROW_ID = UUID.randomUUID();
    private static final Party HOLDING = Party.personal(ESCROW_ID);
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final Party BUYER = Party.personal(BUYER_ID);
    private static final Party SELLER = Party.personal(UUID.randomUUID());
    private static final Party AGENT = Party.personal(UUID.randomUUID());
    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);
    private static final UUID WORLD_ID = UUID.randomUUID();

    private static final DuePayout PROCEEDS =
            new DuePayout(1, "proceeds", "proceeds", SELLER, 970, "plot", WORLD_ID, BUYER_ID, 0);
    private static final DuePayout AGENT_FEE =
            new DuePayout(2, "fee", "agent", AGENT, 30, "plot", WORLD_ID, BUYER_ID, 0);

    @Mock
    private RealtyBackend backend;
    @Mock
    private RealtyEventDispatch events;

    private RecordingEconomy economy;
    private List<LogRecord> logged;
    private SaleSettlement settlement;

    /** Records each transfer, and fails those to the parties it is told to. */
    static final class RecordingEconomy implements EconomyProvider {

        record Transfer(Party from, Party to, double amount, String ledgerMessage) {}

        final List<Transfer> transfers = new ArrayList<>();
        final Set<Party> failingRecipients = new HashSet<>();

        @Override
        public double getBalance(@NotNull Party party) {
            return 1_000_000;
        }

        @Override
        public @NotNull PaymentResult transfer(@NotNull Party from, @NotNull Party to, double amount,
                                               @NotNull String ledgerMessage, @Nullable UUID initiator) {
            if (failingRecipients.contains(to)) {
                return new PaymentResult.Failure("Account #42 is archived");
            }
            transfers.add(new Transfer(from, to, amount, ledgerMessage));
            return new PaymentResult.Success();
        }

        @Override
        public @NotNull String formatAmount(double amount) {
            return String.valueOf(amount);
        }

        @Override
        public boolean hasLedgerSupport() {
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        economy = new RecordingEconomy();
        logged = new ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        HoldingAccount holding = HoldingAccount.vault(
                () -> new DefaultParties(null, null, null, false, ESCROW_ID, List.of()));
        settlement = new SaleSettlement(economy, backend, holding, events, log);
    }

    private List<LogRecord> severe() {
        return logged.stream().filter(r -> r.getLevel() == Level.SEVERE).toList();
    }

    @Test
    void payout_paysEachLineFromHolding_firesFeePaidForFees() {
        when(backend.duePayouts()).thenReturn(List.of(PROCEEDS, AGENT_FEE));
        when(backend.claimPayout(anyInt())).thenReturn(OptionalInt.of(0));

        settlement.payOutDue();

        Assertions.assertEquals(List.of(
                new RecordingEconomy.Transfer(HOLDING, SELLER, 970, "Sale payout (proceeds): plot"),
                new RecordingEconomy.Transfer(HOLDING, AGENT, 30, "Sale payout (agent): plot")), economy.transfers);
        verify(backend).markPayoutPaid(1);
        verify(backend).markPayoutPaid(2);
        ArgumentCaptor<FeePaidEvent> fired = ArgumentCaptor.forClass(FeePaidEvent.class);
        verify(events).fireSync(fired.capture());
        Assertions.assertEquals(AGENT, fired.getValue().getRecipient());
        Assertions.assertEquals(30, fired.getValue().getAmount(), 0.001);
        Assertions.assertEquals("agent", fired.getValue().getSource());
        Assertions.assertEquals(BUYER_ID, fired.getValue().getBuyerId());
        Assertions.assertEquals("plot", fired.getValue().getRegionId());
    }

    @Test
    void payout_oneRecipientFails_lineFailed_othersPaid_retriedLater() {
        economy.failingRecipients.add(AGENT);
        when(backend.duePayouts()).thenReturn(List.of(PROCEEDS, AGENT_FEE));
        when(backend.claimPayout(anyInt())).thenReturn(OptionalInt.of(0));

        settlement.payOutDue();

        verify(backend).markPayoutPaid(1);
        verify(backend).markPayoutFailed(2, "Account #42 is archived");
        verify(backend, never()).markPayoutPaid(2);
        verify(events, never()).fireSync(any(FeePaidEvent.class));

        economy.failingRecipients.clear();
        when(backend.duePayouts()).thenReturn(List.of(
                new DuePayout(2, "fee", "agent", AGENT, 30, "plot", WORLD_ID, BUYER_ID, 1)));
        settlement.payOutDue();

        verify(backend).markPayoutPaid(2);
        Assertions.assertEquals(new RecordingEconomy.Transfer(HOLDING, AGENT, 30, "Sale payout (agent): plot"),
                economy.transfers.getLast());
    }

    @Test
    void payout_lockedLinesAfterRestart_paidOnce() {
        when(backend.duePayouts()).thenReturn(List.of(PROCEEDS));
        when(backend.claimPayout(1)).thenReturn(OptionalInt.of(0), OptionalInt.empty());

        settlement.payOutDue();
        settlement.payOutDue();

        Assertions.assertEquals(1, economy.transfers.size());
        verify(backend).markPayoutPaid(1);
    }

    @Test
    void payout_lastAttemptFails_logsSevere() {
        economy.failingRecipients.add(GOV);
        when(backend.duePayouts()).thenReturn(List.of(
                new DuePayout(3, "fee", "stamp-duty", GOV, 50, "plot", WORLD_ID, BUYER_ID, 23)));
        when(backend.claimPayout(3)).thenReturn(OptionalInt.of(23));

        settlement.payOutDue();

        verify(backend).markPayoutFailed(3, "Account #42 is archived");
        Assertions.assertEquals(1, severe().size());
        Assertions.assertTrue(severe().getFirst().getMessage().contains("plot"));
    }

    @Test
    void payout_earlierFailure_logsNoSevere() {
        economy.failingRecipients.add(GOV);
        when(backend.duePayouts()).thenReturn(List.of(
                new DuePayout(3, "fee", "stamp-duty", GOV, 50, "plot", WORLD_ID, BUYER_ID, 5)));
        when(backend.claimPayout(3)).thenReturn(OptionalInt.of(5));

        settlement.payOutDue();

        Assertions.assertTrue(severe().isEmpty());
    }

    @Test
    void payout_staleList_usesTheAttemptsReadUnderTheClaim() {
        economy.failingRecipients.add(GOV);
        when(backend.duePayouts()).thenReturn(List.of(
                new DuePayout(3, "fee", "stamp-duty", GOV, 50, "plot", WORLD_ID, BUYER_ID, 5)));
        when(backend.claimPayout(3)).thenReturn(OptionalInt.of(23));

        settlement.payOutDue();

        Assertions.assertEquals(1, severe().size(), "the 24th attempt, by the claim's count");
    }

    @Test
    void payout_zeroLine_isMarkedPaidWithoutATransfer() {
        when(backend.duePayouts()).thenReturn(List.of(
                new DuePayout(4, "proceeds", "proceeds", SELLER, 0, "plot", WORLD_ID, BUYER_ID, 0)));
        when(backend.claimPayout(4)).thenReturn(OptionalInt.of(0));

        settlement.payOutDue();

        Assertions.assertTrue(economy.transfers.isEmpty());
        verify(backend).markPayoutPaid(4);
    }

    @Test
    void payout_withoutHolding_claimsNothing() {
        settlement = new SaleSettlement(economy, backend, HoldingAccount.vault(DefaultParties::unresolved), events,
                Logger.getAnonymousLogger());

        settlement.payOutDue();

        verify(backend, never()).claimPayout(anyInt());
        Assertions.assertTrue(economy.transfers.isEmpty());
    }

    @Test
    void expiry_refundsFromHolding_eventSaysPaid() {
        when(backend.claimRefund(7)).thenReturn(OptionalInt.of(0));

        SaleSettlement.ExpiryRefund refund = settlement.settleExpiry(7, BUYER_ID, 300, "plot", WORLD_ID,
                "bid-expired");

        Assertions.assertTrue(refund.paid());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        Assertions.assertEquals(List.of(new RecordingEconomy.Transfer(HOLDING, BUYER, 300,
                "Sale refund (bid-expired): plot")), economy.transfers);
        verify(backend).markRefundPaid(7);
        ArgumentCaptor<RefundPaidEvent> fired = ArgumentCaptor.forClass(RefundPaidEvent.class);
        verify(events).fireSync(fired.capture());
        Assertions.assertEquals(BUYER_ID, fired.getValue().getBuyerId());
        Assertions.assertEquals("bid-expired", fired.getValue().getReason());
        Assertions.assertTrue(new AuctionPaymentExpiredEvent("plot", WORLD_ID, null, BUYER_ID,
                refund.amount(), refund.paid()).isRefundPaid());
    }

    @Test
    void expiry_refundFails_recordsPendingRefund_eventSaysNotPaid() {
        economy.failingRecipients.add(BUYER);
        when(backend.claimRefund(7)).thenReturn(OptionalInt.of(0));

        SaleSettlement.ExpiryRefund refund = settlement.settleExpiry(7, BUYER_ID, 300, "plot", WORLD_ID,
                "offer-expired");

        Assertions.assertFalse(refund.paid());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        // The row the expiry recorded stays pending, counting this attempt.
        verify(backend).markRefundFailed(7, "Account #42 is archived");
        verify(backend, never()).markRefundPaid(anyInt());
        verify(events, never()).fireSync(any(RefundPaidEvent.class));
        Assertions.assertFalse(new OfferPaymentExpiredEvent("plot", WORLD_ID, null, BUYER_ID,
                refund.amount(), refund.paid()).isRefundPaid());
    }

    @Test
    void expiry_withoutARefundRow_refundsNothing() {
        SaleSettlement.ExpiryRefund refund = settlement.settleExpiry(null, BUYER_ID, 300, "plot", WORLD_ID,
                "bid-expired");

        Assertions.assertFalse(refund.paid());
        Assertions.assertEquals(0, refund.amount(), 0.001);
        Assertions.assertTrue(economy.transfers.isEmpty());
        verify(backend, never()).claimRefund(anyInt());
    }

    @Test
    void expiry_withoutHolding_leavesTheRefundPending() {
        settlement = new SaleSettlement(economy, backend, HoldingAccount.vault(DefaultParties::unresolved), events,
                Logger.getAnonymousLogger());

        SaleSettlement.ExpiryRefund refund = settlement.settleExpiry(7, BUYER_ID, 300, "plot", WORLD_ID,
                "bid-expired");

        Assertions.assertFalse(refund.paid());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        verify(backend, never()).claimRefund(anyInt());
        Assertions.assertTrue(economy.transfers.isEmpty());
    }

    @Test
    void retryRefunds_paysAndDeletes_orMarksFailed() {
        DueRefund owed = new DueRefund(7, BUYER_ID, 300, "plot", WORLD_ID, "region-deleted", 0);
        Party other = Party.personal(UUID.randomUUID());
        DueRefund failing = new DueRefund(8, Party.playerUuidOf(other).orElseThrow(), 100, "plot", WORLD_ID,
                "region-deleted", 23);
        economy.failingRecipients.add(other);
        when(backend.dueRefunds()).thenReturn(List.of(owed, failing));
        when(backend.claimRefund(7)).thenReturn(OptionalInt.of(0));
        when(backend.claimRefund(8)).thenReturn(OptionalInt.of(23));

        settlement.retryRefunds();

        Assertions.assertEquals(List.of(new RecordingEconomy.Transfer(HOLDING, BUYER, 300,
                "Sale refund (region-deleted): plot")), economy.transfers);
        verify(backend).markRefundPaid(7);
        verify(backend).markRefundFailed(8, "Account #42 is archived");
        verify(events).fireSync(any(RefundPaidEvent.class));
        Assertions.assertEquals(1, severe().size(), "the last attempt failing is logged");
    }

    @Test
    void retryRefunds_claimedElsewhere_isSkipped() {
        when(backend.dueRefunds()).thenReturn(List.of(new DueRefund(7, BUYER_ID, 300, "plot", WORLD_ID,
                "region-deleted", 0)));
        when(backend.claimRefund(7)).thenReturn(OptionalInt.empty());

        settlement.retryRefunds();

        Assertions.assertTrue(economy.transfers.isEmpty());
        verify(backend, never()).markRefundPaid(anyInt());
    }

    @Test
    void reportStuck_logsSevereOnceWithTheRows() {
        when(backend.stuckPayouts()).thenReturn(List.of(AGENT_FEE));
        when(backend.stuckRefunds()).thenReturn(List.of(new DueRefund(7, BUYER_ID, 300, "plot", WORLD_ID,
                "bid-expired", 0)));

        settlement.reportStuck();

        Assertions.assertEquals(1, severe().size());
        String message = severe().getFirst().getMessage();
        Assertions.assertTrue(message.contains("#2") && message.contains("#7"), message);
    }

    @Test
    void reportStuck_nothingStuck_logsNothing() {
        when(backend.stuckPayouts()).thenReturn(List.of());
        when(backend.stuckRefunds()).thenReturn(List.of());

        settlement.reportStuck();

        Assertions.assertTrue(logged.isEmpty());
    }
}
