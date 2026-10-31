package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.BidResult;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.DuePayout;
import io.github.md5sha256.realty.api.RealtyBackend.DueRefund;
import io.github.md5sha256.realty.api.RealtyBackend.PayBidResult;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bookkeeping the Paper plugin's settlement runs on: the payout lines and pending refunds it
 * claims and marks, and the held instalments a cancelled or handed-over sale must not lose.
 */
class SalePayoutBookkeepingTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID RUNNER_UP = UUID.randomUUID();
    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);

    /** 5% paid by the buyer to the authority. */
    private static final LevyDefinition STAMP_DUTY = new LevyDefinition("stamp-duty", true, true, FeeSide.BUYER,
            null, null, List.of(new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, 5)))));

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static LevyCatalog stampDuty() {
        return new LevyCatalog() {
            @Override
            public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
                return STAMP_DUTY.name().equalsIgnoreCase(name) ? Optional.of(STAMP_DUTY) : Optional.empty();
            }

            @Override
            public @NotNull Optional<LevyDefinition> defaultLevy() {
                return Optional.of(STAMP_DUTY);
            }

            @Override
            public @NotNull List<LevyDefinition> all() {
                return List.of(STAMP_DUTY);
            }
        };
    }

    private static String freehold() {
        String regionId = "payout_region_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, GOV, TITLE_HOLDER));
        return regionId;
    }

    private static void auctionWithBids(String regionId, double... bids) {
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, ActorContext.player(TITLE_HOLDER, false),
                        3600, 3600, 100.0, 10.0));
        UUID[] bidders = {RUNNER_UP, BUYER};
        for (int i = 0; i < bids.length; i++) {
            Assertions.assertInstanceOf(BidResult.Success.class,
                    logic.performBid(regionId, WORLD_ID, ActorContext.player(bidders[i], false), bids[i], false));
        }
    }

    private static void moveBiddingBack(String regionId) {
        update("""
                UPDATE FreeholdContractBid SET bidTime = bidTime - INTERVAL 2 HOUR
                WHERE freeholdContractAuctionId IN (
                    SELECT a.freeholdContractAuctionId FROM FreeholdContractAuction a
                    INNER JOIN RealtyRegion rr ON rr.realtyRegionId = a.realtyRegionId
                    WHERE rr.worldGuardRegionId = ?)
                """, regionId);
        update("""
                UPDATE FreeholdContractAuction SET startDate = startDate - INTERVAL 2 HOUR
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);
    }

    /** An auction won by {@link #BUYER} at 1000, with {@link #RUNNER_UP} at 900 behind, now in payment. */
    private static FreeholdContractBidPaymentEntity inPayment(String regionId) {
        levies = stampDuty();
        auctionWithBids(regionId, 900, 1000);
        moveBiddingBack(regionId);
        Assertions.assertEquals(1, logic.clearExpiredBiddingAuctions().size());
        FreeholdContractBidPaymentEntity payment = bidPayment(regionId);
        Assertions.assertNotNull(payment);
        return payment;
    }

    private static void expirePayment(String regionId) {
        update("""
                UPDATE FreeholdContractBidPayment SET paymentDeadline = NOW() - INTERVAL 1 DAY
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);
    }

    /** Moves every failed attempt back past the retry interval. */
    private static void passRetryInterval() {
        update("UPDATE SaleFee SET lastAttemptAt = lastAttemptAt - INTERVAL 61 MINUTE WHERE ? IS NOT NULL", "x");
        update("UPDATE PendingRefund SET lastAttemptAt = lastAttemptAt - INTERVAL 61 MINUTE WHERE ? IS NOT NULL",
                "x");
    }

    private static int update(String sql, String parameter) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session();
             PreparedStatement statement = session.getConnection().prepareStatement(sql)) {
            statement.setString(1, parameter);
            int updated = statement.executeUpdate();
            session.commit();
            return updated;
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    private static int count(String sql) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session();
             PreparedStatement statement = session.getConnection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    private static FreeholdContractBidPaymentEntity bidPayment(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractBidPaymentMapper().selectByRegion(regionId, WORLD_ID);
        }
    }

    private static List<DueRefund> refundsFor(String regionId) {
        return logic.dueRefunds().stream().filter(r -> r.regionName().equals(regionId)).toList();
    }

    @Test
    void duePayouts_listsCompletedSaleLines() {
        String regionId = freehold();
        inPayment(regionId);
        Assertions.assertTrue(logic.duePayouts().isEmpty(), "an uncompleted sale owes nothing yet");
        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, logic.payBid(regionId, WORLD_ID, BUYER, 1050));
        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);

        List<DuePayout> due = logic.duePayouts();

        Assertions.assertEquals(2, due.size());
        DuePayout fee = due.stream().filter(p -> p.lineKind().equals("fee")).findFirst().orElseThrow();
        Assertions.assertEquals("stamp-duty", fee.source());
        Assertions.assertEquals(GOV, fee.recipient());
        Assertions.assertEquals(50, fee.amount(), 0.001);
        Assertions.assertEquals(regionId, fee.regionName());
        Assertions.assertEquals(WORLD_ID, fee.worldId());
        Assertions.assertEquals(BUYER, fee.buyerId());
        Assertions.assertEquals(0, fee.attempts());
        DuePayout proceeds = due.stream().filter(p -> p.lineKind().equals("proceeds")).findFirst().orElseThrow();
        Assertions.assertEquals(Party.personal(TITLE_HOLDER), proceeds.recipient());
        Assertions.assertEquals(1000, proceeds.amount(), 0.001);
    }

    @Test
    void claimPayout_onlyOnce_thenPaidLineIsNoLongerDue() {
        String regionId = freehold();
        inPayment(regionId);
        logic.payBid(regionId, WORLD_ID, BUYER, 1050);
        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);
        DuePayout line = logic.duePayouts().getFirst();

        Assertions.assertTrue(logic.claimPayout(line.id()));
        Assertions.assertFalse(logic.claimPayout(line.id()));
        Assertions.assertEquals(List.of(line.id()), logic.stuckPayouts().stream().map(DuePayout::id).toList());
        logic.markPayoutPaid(line.id());

        Assertions.assertTrue(logic.stuckPayouts().isEmpty());
        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()));
    }

    @Test
    void failedPayout_waitsTheRetryInterval_untilAttemptsRunOut() {
        String regionId = freehold();
        inPayment(regionId);
        logic.payBid(regionId, WORLD_ID, BUYER, 1050);
        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);
        DuePayout line = logic.duePayouts().getFirst();
        Assertions.assertTrue(logic.claimPayout(line.id()));

        logic.markPayoutFailed(line.id(), "account archived");

        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()),
                "a failed line waits an hour");
        passRetryInterval();
        DuePayout retried = logic.duePayouts().stream().filter(p -> p.id() == line.id()).findFirst().orElseThrow();
        Assertions.assertEquals(1, retried.attempts());

        update("UPDATE SaleFee SET attempts = 24 WHERE saleFeeId = ?", String.valueOf(line.id()));
        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()),
                "a line out of attempts is left for an admin");
    }

    @Test
    void pendingRefund_recordedAfterAFailure_retriedThenDeletedWhenPaid() {
        logic.recordPendingRefund(BUYER, 300, "gone_plot", WORLD_ID, "bid-expired", "no holding account");

        Assertions.assertTrue(refundsFor("gone_plot").isEmpty(), "the failed attempt counts, so it waits");
        passRetryInterval();
        DueRefund refund = refundsFor("gone_plot").getFirst();
        Assertions.assertEquals(BUYER, refund.buyerId());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        Assertions.assertEquals("bid-expired", refund.reason());
        Assertions.assertEquals(1, refund.attempts());

        Assertions.assertTrue(logic.claimRefund(refund.id()));
        Assertions.assertFalse(logic.claimRefund(refund.id()));
        Assertions.assertEquals(1, logic.stuckRefunds().size());
        logic.markRefundFailed(refund.id(), "still failing");
        Assertions.assertTrue(logic.stuckRefunds().isEmpty());
        passRetryInterval();
        DueRefund again = refundsFor("gone_plot").getFirst();
        Assertions.assertEquals(2, again.attempts());
        Assertions.assertTrue(logic.claimRefund(again.id()));
        logic.markRefundPaid(again.id());

        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void cancelAuction_duringPayment_recordsPendingRefund() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = inPayment(regionId);
        Assertions.assertInstanceOf(PayBidResult.Success.class, logic.payBid(regionId, WORLD_ID, BUYER, 300));

        Assertions.assertEquals(1, logic.cancelAuction(regionId, WORLD_ID).deleted());

        List<DueRefund> refunds = refundsFor(regionId);
        Assertions.assertEquals(1, refunds.size());
        Assertions.assertEquals(BUYER, refunds.getFirst().buyerId());
        Assertions.assertEquals(300, refunds.getFirst().amount(), 0.001);
        Assertions.assertEquals("auction-cancelled", refunds.getFirst().reason());
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM SaleFee WHERE saleRef = " + payment.bidId()));
    }

    @Test
    void cancelAuction_oldRulesPayment_recordsNoRefund() {
        String regionId = freehold();
        inPayment(regionId);
        logic.payBid(regionId, WORLD_ID, BUYER, 300);
        update("UPDATE FreeholdContractBidPayment SET oldRules = TRUE WHERE ? IS NOT NULL", "x");

        Assertions.assertEquals(1, logic.cancelAuction(regionId, WORLD_ID).deleted());

        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void runnerUpHandover_withoutHolding_waitsForOne() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity first = inPayment(regionId);
        expirePayment(regionId);
        holdingAvailable = false;

        Assertions.assertTrue(logic.clearExpiredBidPayments().isEmpty());
        Assertions.assertTrue(logic.clearExpiredBidPayments().isEmpty());

        Assertions.assertEquals(first.bidId(), bidPayment(regionId).bidId(), "the expired payment waits");
        holdingAvailable = true;
        Assertions.assertEquals(1, logic.clearExpiredBidPayments().size());
        Assertions.assertEquals(RUNNER_UP, bidPayment(regionId).bidderId());
    }

    @Test
    void deleteRegion_waitsForAnInstalmentCommittingUnderTheFreeholdLock() throws Exception {
        String regionId = freehold();
        inPayment(regionId);
        logic.payBid(regionId, WORLD_ID, BUYER, 300);
        CompletableFuture<Integer> deletion;
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(regionId, WORLD_ID);
            Assertions.assertEquals(1, wrapper.freeholdContractBidPaymentMapper()
                    .atomicUpdatePayment(regionId, WORLD_ID, BUYER, 300, 500));
            deletion = CompletableFuture.supplyAsync(() -> logic.deleteRegion(regionId, WORLD_ID));
            Assertions.assertThrows(TimeoutException.class, () -> deletion.get(500, TimeUnit.MILLISECONDS));
            session.commit();
        }

        Assertions.assertEquals(1, deletion.get(10, TimeUnit.SECONDS));
        List<DueRefund> refunds = refundsFor(regionId);
        Assertions.assertEquals(1, refunds.size());
        Assertions.assertEquals(500, refunds.getFirst().amount(), 0.001, "the late instalment is refunded too");
    }

    @Test
    void payBid_takesTheFreeholdLockFirst() throws Exception {
        String regionId = freehold();
        inPayment(regionId);
        CompletableFuture<PayBidResult> paying;
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(regionId, WORLD_ID);
            paying = CompletableFuture.supplyAsync(() -> logic.payBid(regionId, WORLD_ID, BUYER, 100));
            Assertions.assertThrows(TimeoutException.class, () -> paying.get(500, TimeUnit.MILLISECONDS));
            session.commit();
        }

        Assertions.assertInstanceOf(PayBidResult.Success.class, paying.get(10, TimeUnit.SECONDS));
    }

    @Test
    void auctionEnd_oneItemFailing_doesNotStopTheOthers() {
        String failing = freehold();
        String settling = freehold();
        auctionWithBids(failing, 900, 1000);
        auctionWithBids(settling, 900, 1000);
        moveBiddingBack(failing);
        moveBiddingBack(settling);
        AtomicBoolean thrown = new AtomicBoolean();
        LevyCatalog stampDuty = stampDuty();
        levies = new LevyCatalog() {
            @Override
            public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
                return stampDuty.find(name);
            }

            @Override
            public @NotNull Optional<LevyDefinition> defaultLevy() {
                if (thrown.compareAndSet(false, true)) {
                    throw new PersistenceException("Duplicate entry for key 'uuid'");
                }
                return stampDuty.defaultLevy();
            }

            @Override
            public @NotNull List<LevyDefinition> all() {
                return stampDuty.all();
            }
        };

        Assertions.assertEquals(1, logic.clearExpiredBiddingAuctions().size());
        Assertions.assertEquals(1, logic.clearExpiredBiddingAuctions().size(), "the failed one is retried");
        Assertions.assertNotNull(bidPayment(failing));
        Assertions.assertNotNull(bidPayment(settling));
    }
}
