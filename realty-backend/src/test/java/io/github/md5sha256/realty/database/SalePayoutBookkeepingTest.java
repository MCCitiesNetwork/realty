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
import io.github.md5sha256.realty.api.RealtyBackend.ExpiredBidPayment;
import io.github.md5sha256.realty.api.RealtyBackend.ExpiredOfferPayment;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.PendingFinalize;
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
import java.util.OptionalInt;
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

    /** Marks the region's bid payment, and no other, as begun before sale fees. */
    private static void markOldRules(String regionId) {
        Assertions.assertEquals(1, update("""
                UPDATE FreeholdContractBidPayment SET oldRules = TRUE
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId));
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
        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, payBidConfirmed(regionId, WORLD_ID, BUYER, 1050));
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
        Assertions.assertTrue(due.stream().allMatch(DuePayout::auction), "a bid sale is a sale at auction");
    }

    @Test
    void claimPayout_onlyOnce_thenPaidLineIsNoLongerDue() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 1050);
        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);
        DuePayout line = logic.duePayouts().getFirst();

        Assertions.assertEquals(OptionalInt.of(0), logic.claimPayout(line.id()));
        Assertions.assertEquals(OptionalInt.empty(), logic.claimPayout(line.id()));
        Assertions.assertEquals(List.of(line.id()), logic.stuckPayouts().stream().map(DuePayout::id).toList());
        logic.markPayoutPaid(line.id());

        Assertions.assertTrue(logic.stuckPayouts().isEmpty());
        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()));
    }

    @Test
    void failedPayout_waitsTheRetryInterval_untilAttemptsRunOut() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 1050);
        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);
        DuePayout line = logic.duePayouts().getFirst();
        Assertions.assertTrue(logic.claimPayout(line.id()).isPresent());

        logic.markPayoutFailed(line.id(), "account archived");
        Assertions.assertEquals(OptionalInt.empty(), logic.claimPayout(line.id()),
                "a failed line cannot be claimed before its retry interval");

        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()),
                "a failed line waits an hour");
        passRetryInterval();
        DuePayout retried = logic.duePayouts().stream().filter(p -> p.id() == line.id()).findFirst().orElseThrow();
        Assertions.assertEquals(1, retried.attempts());
        Assertions.assertEquals(OptionalInt.of(1), logic.claimPayout(line.id()), "the attempts read under the claim");
        logic.markPayoutFailed(line.id(), "account archived");
        passRetryInterval();

        update("UPDATE SaleFee SET attempts = 24 WHERE saleFeeId = ?", String.valueOf(line.id()));
        Assertions.assertTrue(logic.duePayouts().stream().noneMatch(p -> p.id() == line.id()),
                "a line out of attempts is left for an admin");
        Assertions.assertEquals(OptionalInt.empty(), logic.claimPayout(line.id()),
                "nor can it be claimed from a stale list");
    }

    @Test
    void pendingRefund_failed_retriedThenDeletedWhenPaid() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // As a refund whose first attempt failed. The insert alone does not mark the session
            // dirty, so the failure is recorded in the same session before the commit.
            int id = wrapper.pendingRefundMapper().insert(BUYER, 300, "gone_plot", WORLD_ID, "bid-expired");
            wrapper.pendingRefundMapper().markFailed(id, "no holding account");
            wrapper.session().commit();
        }

        Assertions.assertTrue(refundsFor("gone_plot").isEmpty(), "the failed attempt counts, so it waits");
        passRetryInterval();
        DueRefund refund = refundsFor("gone_plot").getFirst();
        Assertions.assertEquals(BUYER, refund.buyerId());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        Assertions.assertEquals("bid-expired", refund.reason());
        Assertions.assertEquals(1, refund.attempts());

        Assertions.assertEquals(OptionalInt.of(1), logic.claimRefund(refund.id()));
        Assertions.assertEquals(OptionalInt.empty(), logic.claimRefund(refund.id()));
        Assertions.assertEquals(1, logic.stuckRefunds().size());
        logic.markRefundFailed(refund.id(), "still failing");
        Assertions.assertTrue(logic.stuckRefunds().isEmpty());
        Assertions.assertEquals(OptionalInt.empty(), logic.claimRefund(refund.id()),
                "a failed refund cannot be claimed before its retry interval");
        passRetryInterval();
        DueRefund again = refundsFor("gone_plot").getFirst();
        Assertions.assertEquals(2, again.attempts());
        Assertions.assertEquals(OptionalInt.of(2), logic.claimRefund(again.id()));
        logic.markRefundPaid(again.id());

        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void cancelAuction_duringPayment_recordsPendingRefund() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = inPayment(regionId);
        Assertions.assertInstanceOf(PayBidResult.Success.class, payBidConfirmed(regionId, WORLD_ID, BUYER, 300));

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
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        markOldRules(regionId);

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
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        CompletableFuture<Integer> deletion;
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(regionId, WORLD_ID);
            Assertions.assertEquals(1, wrapper.freeholdContractBidPaymentMapper()
                    .atomicUpdatePayment(regionId, WORLD_ID, BUYER, 300, 500));
            deletion = CompletableFuture.supplyAsync(() -> logic.deleteRegion(regionId, WORLD_ID));
            Assertions.assertThrows(TimeoutException.class, () -> deletion.get(500, TimeUnit.MILLISECONDS));
            // Forced: a session that only locked is not dirty, and a plain commit would not end it.
            session.commit(true);
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
            session.commit(true);
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

    /** An offer by {@link #BUYER} at 1000 under the stamp duty, accepted, so owing 1050. */
    private static void acceptedOffer(String regionId) {
        levies = stampDuty();
        openToOffers(regionId, WORLD_ID);
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, ActorContext.player(BUYER, false), 1000, false));
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class,
                logic.acceptOffer(regionId, WORLD_ID, ActorContext.player(TITLE_HOLDER, false), BUYER));
    }

    private static void expireOfferPayment(String regionId) {
        update("""
                UPDATE FreeholdContractOfferPayment SET paymentDeadline = NOW() - INTERVAL 1 DAY
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);
    }

    @Test
    void bidExpiry_recordsTheRefundInTheSameTransaction_andReturnsItsId() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        expirePayment(regionId);

        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();

        Assertions.assertEquals(1, expired.size());
        Integer refundId = expired.getFirst().refundId();
        Assertions.assertNotNull(refundId);
        DueRefund refund = refundsFor(regionId).getFirst();
        Assertions.assertEquals(refundId, refund.id());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        Assertions.assertEquals("bid-expired", refund.reason());
        Assertions.assertEquals(OptionalInt.of(0), logic.claimRefund(refundId), "tried at once");
    }

    @Test
    void bidExpiry_nothingPaid_recordsNoRefund() {
        String regionId = freehold();
        inPayment(regionId);
        expirePayment(regionId);

        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();

        Assertions.assertEquals(1, expired.size());
        Assertions.assertNull(expired.getFirst().refundId());
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void offerExpiry_recordsTheRefundInTheSameTransaction() {
        String regionId = freehold();
        acceptedOffer(regionId);
        Assertions.assertInstanceOf(PayOfferResult.Success.class, payOfferConfirmed(regionId, WORLD_ID, BUYER, 400));
        expireOfferPayment(regionId);

        List<ExpiredOfferPayment> expired = logic.clearExpiredOfferPayments();

        Assertions.assertEquals(1, expired.size());
        Assertions.assertEquals(400, expired.getFirst().refundAmount(), 0.001);
        Assertions.assertNotNull(expired.getFirst().refundId());
        Assertions.assertEquals("offer-expired", refundsFor(regionId).getFirst().reason());
    }

    @Test
    void expiry_ofAPaymentPaidInFull_isLeftForItsFinalize() {
        String bidRegion = freehold();
        inPayment(bidRegion);
        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, payBidConfirmed(bidRegion, WORLD_ID, BUYER, 1050));
        expirePayment(bidRegion);
        String offerRegion = freehold();
        acceptedOffer(offerRegion);
        Assertions.assertInstanceOf(PayOfferResult.FullyPaid.class,
                payOfferConfirmed(offerRegion, WORLD_ID, BUYER, 1050));
        expireOfferPayment(offerRegion);

        Assertions.assertTrue(logic.clearExpiredBidPayments().isEmpty());
        Assertions.assertTrue(logic.clearExpiredOfferPayments().isEmpty());

        Assertions.assertNotNull(bidPayment(bidRegion));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
        List<PendingFinalize> pending = logic.listFullyPaidUnfinalized();
        Assertions.assertEquals(2, pending.size());
        PendingFinalize bid = pending.stream().filter(p -> p.kind() == PendingFinalize.Kind.BID).findFirst()
                .orElseThrow();
        Assertions.assertEquals(bidRegion, bid.regionId());
        Assertions.assertEquals(WORLD_ID, bid.worldId());
        Assertions.assertEquals(BUYER, bid.buyerId());
        Assertions.assertEquals(TITLE_HOLDER, bid.titleHolderId());
        Assertions.assertEquals(1000, bid.price(), 0.001);
        Assertions.assertEquals(1050, bid.fees().buyerTotal(), 0.001);
        PendingFinalize offer = pending.stream().filter(p -> p.kind() == PendingFinalize.Kind.OFFER).findFirst()
                .orElseThrow();
        Assertions.assertEquals(offerRegion, offer.regionId());

        Assertions.assertTrue(logic.finalizeBidPurchase(bidRegion, WORLD_ID, BUYER));
        Assertions.assertFalse(logic.finalizeBidPurchase(bidRegion, WORLD_ID, BUYER), "a second finalize is a no-op");
        Assertions.assertTrue(logic.finalizeOfferPurchase(offerRegion, WORLD_ID, BUYER));
        Assertions.assertTrue(logic.listFullyPaidUnfinalized().isEmpty());
        List<DuePayout> offerLines = logic.duePayouts().stream()
                .filter(p -> p.regionName().equals(offerRegion)).toList();
        Assertions.assertFalse(offerLines.isEmpty());
        Assertions.assertTrue(offerLines.stream().noneMatch(DuePayout::auction), "an offer sale is not an auction");
    }

    @Test
    void listFullyPaidUnfinalized_skipsPartialPayments_listsOldRulesOnes() {
        String partial = freehold();
        inPayment(partial);
        payBidConfirmed(partial, WORLD_ID, BUYER, 300);
        String oldRules = freehold();
        inPayment(oldRules);
        markOldRules(oldRules);
        payBidConfirmed(oldRules, WORLD_ID, BUYER, 1050);

        List<PendingFinalize> pending = logic.listFullyPaidUnfinalized();

        Assertions.assertEquals(1, pending.size(), pending.toString());
        Assertions.assertEquals(oldRules, pending.getFirst().regionId());
        Assertions.assertTrue(pending.getFirst().oldRules());
    }

    @Test
    void oldRulesExpiry_withoutHolding_isStillProcessed() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity first = inPayment(regionId);
        markOldRules(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        expirePayment(regionId);
        holdingAvailable = false;

        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();

        Assertions.assertEquals(1, expired.size());
        Assertions.assertTrue(expired.getFirst().oldRules());
        Assertions.assertNull(expired.getFirst().refundId(), "old-rules money went to the seller");
        Assertions.assertNotEquals(first.bidId(), bidPayment(regionId).bidId());
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    private static void movePendingBack(int minutes) {
        update("UPDATE FreeholdContractBidPayment SET pendingSince = pendingSince - INTERVAL " + minutes
                + " MINUTE WHERE ? IS NOT NULL", "x");
    }

    @Test
    void crashBeforeConfirm_notFinalized_notRefunded_reportedAsStuckAfterTenMinutes() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = inPayment(regionId);
        // Counted, then the server stops before the transfer's outcome is confirmed.
        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, logic.payBid(regionId, WORLD_ID, BUYER, 1050));

        Assertions.assertTrue(logic.listFullyPaidUnfinalized().isEmpty(), "unconfirmed money finishes nothing");
        Assertions.assertFalse(logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER));
        expirePayment(regionId);
        Assertions.assertTrue(logic.clearExpiredBidPayments().isEmpty(), "a pending row never expires");
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"), "nor is it refunded");
        Assertions.assertInstanceOf(PayBidResult.PaymentInProgress.class, logic.payBid(regionId, WORLD_ID, BUYER, 1));
        Assertions.assertTrue(logic.stuckInstalments(10).isEmpty(), "not yet stuck");

        movePendingBack(11);

        List<RealtyBackend.StuckInstalment> stuck = logic.stuckInstalments(10);
        Assertions.assertEquals(1, stuck.size());
        Assertions.assertEquals(RealtyBackend.PendingFinalize.Kind.BID, stuck.getFirst().kind());
        Assertions.assertEquals(payment.bidId(), stuck.getFirst().saleId());
        Assertions.assertEquals(regionId, stuck.getFirst().regionId());
        Assertions.assertEquals(BUYER, stuck.getFirst().buyerId());
        Assertions.assertEquals(1050, stuck.getFirst().amount(), 0.001);
        Assertions.assertNotNull(bidPayment(regionId), "it is left for an admin");
    }

    @Test
    void confirm_movesThePendingInstalmentIntoThePayment() {
        String regionId = freehold();
        inPayment(regionId);
        Assertions.assertInstanceOf(PayBidResult.Success.class, logic.payBid(regionId, WORLD_ID, BUYER, 300));
        FreeholdContractBidPaymentEntity pending = bidPayment(regionId);
        Assertions.assertEquals(0, pending.currentPayment(), 0.001);
        Assertions.assertEquals(300, pending.pendingPayment(), 0.001);
        Assertions.assertNotNull(pending.pendingSince());

        Assertions.assertTrue(logic.confirmPayBid(regionId, WORLD_ID, BUYER, 300, false));

        FreeholdContractBidPaymentEntity confirmed = bidPayment(regionId);
        Assertions.assertEquals(300, confirmed.currentPayment(), 0.001);
        Assertions.assertEquals(0, confirmed.pendingPayment(), 0.001);
        Assertions.assertNull(confirmed.pendingSince());
        Assertions.assertFalse(logic.confirmPayBid(regionId, WORLD_ID, BUYER, 300, false), "only once");
    }

    @Test
    void confirmOffer_movesThePendingInstalmentIntoThePayment() {
        String regionId = freehold();
        acceptedOffer(regionId);
        Assertions.assertInstanceOf(PayOfferResult.Success.class, logic.payOffer(regionId, WORLD_ID, BUYER, 400));

        Assertions.assertTrue(logic.confirmPayOffer(regionId, WORLD_ID, BUYER, 400, false));

        try (SqlSessionWrapper wrapper = database.openSession()) {
            var payment = wrapper.freeholdContractOfferPaymentMapper().selectByRegion(regionId, WORLD_ID);
            Assertions.assertEquals(400, payment.currentPayment(), 0.001);
            Assertions.assertEquals(0, payment.pendingPayment(), 0.001);
        }
    }

    @Test
    void rollback_dropsThePendingInstalment() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        logic.payBid(regionId, WORLD_ID, BUYER, 200);

        logic.rollbackPayBid(regionId, WORLD_ID, BUYER, 200);

        FreeholdContractBidPaymentEntity payment = bidPayment(regionId);
        Assertions.assertEquals(300, payment.currentPayment(), 0.001, "confirmed money stays");
        Assertions.assertEquals(0, payment.pendingPayment(), 0.001);
        Assertions.assertInstanceOf(PayBidResult.Success.class, logic.payBid(regionId, WORLD_ID, BUYER, 200),
                "the buyer may pay again");
    }

    @Test
    void expiry_waitsForAPendingInstalment_thenRefundsOnlyConfirmedMoney() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        logic.payBid(regionId, WORLD_ID, BUYER, 200);
        expirePayment(regionId);

        Assertions.assertTrue(logic.clearExpiredBidPayments().isEmpty());

        logic.rollbackPayBid(regionId, WORLD_ID, BUYER, 200);
        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();
        Assertions.assertEquals(1, expired.size());
        Assertions.assertEquals(300, expired.getFirst().refundAmount(), 0.001);
        Assertions.assertEquals(300, refundsFor(regionId).getFirst().amount(), 0.001);
    }

    @Test
    void confirm_afterTheAuctionWasCancelled_recordsTheInstalmentAsARefund() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        logic.payBid(regionId, WORLD_ID, BUYER, 200);
        logic.cancelAuction(regionId, WORLD_ID);
        Assertions.assertEquals(List.of(300.0), refundsFor(regionId).stream().map(DueRefund::amount).toList(),
                "the cancel refunds confirmed money only");

        Assertions.assertFalse(logic.confirmPayBid(regionId, WORLD_ID, BUYER, 200, false));

        List<DueRefund> refunds = refundsFor(regionId);
        Assertions.assertEquals(2, refunds.size());
        DueRefund late = refunds.getLast();
        Assertions.assertEquals(200, late.amount(), 0.001);
        Assertions.assertEquals("sale-ended", late.reason());
    }

    @Test
    void rollback_afterTheAuctionWasCancelled_changesNoRefund() {
        String regionId = freehold();
        inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        logic.payBid(regionId, WORLD_ID, BUYER, 200);
        logic.cancelAuction(regionId, WORLD_ID);

        logic.rollbackPayBid(regionId, WORLD_ID, BUYER, 200);

        Assertions.assertEquals(List.of(300.0), refundsFor(regionId).stream().map(DueRefund::amount).toList());
    }

    @Test
    void holdsSaleMoney_whileAnInstalmentIsHeld() {
        String regionId = freehold();
        inPayment(regionId);
        Assertions.assertFalse(logic.holdsSaleMoney(), "nothing paid yet");

        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);

        Assertions.assertTrue(logic.holdsSaleMoney());
    }

    @Test
    void confirm_ofAnOldRulesInstalmentAfterTheSaleEnded_refundsNothing() {
        String regionId = freehold();
        inPayment(regionId);
        markOldRules(regionId);
        PayBidResult.Success counted = Assertions.assertInstanceOf(PayBidResult.Success.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 200));
        Assertions.assertTrue(counted.oldRules());
        logic.cancelAuction(regionId, WORLD_ID);

        // The money went to the seller; the holding account never received it.
        Assertions.assertFalse(logic.confirmPayBid(regionId, WORLD_ID, BUYER, 200, counted.oldRules()));

        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void cancelAuction_withAPendingInstalment_logsSevere() {
        String regionId = freehold();
        inPayment(regionId);
        logic.payBid(regionId, WORLD_ID, BUYER, 200);
        List<java.util.logging.LogRecord> logged = new java.util.ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger.getLogger("test").addHandler(handler);
        try {
            logic.cancelAuction(regionId, WORLD_ID);
        } finally {
            java.util.logging.Logger.getLogger("test").removeHandler(handler);
        }

        List<String> severe = logged.stream().filter(r -> r.getLevel() == java.util.logging.Level.SEVERE)
                .map(java.util.logging.LogRecord::getMessage).toList();
        Assertions.assertEquals(1, severe.size(), String.valueOf(severe));
        Assertions.assertTrue(severe.getFirst().contains(regionId) && severe.getFirst().contains(BUYER.toString())
                && severe.getFirst().contains("200"), severe.getFirst());
    }

    // --- Deadlines paused while the holding account is unusable ---

    /** Rows of {@code table} for the region whose {@code column} lies within a minute of NOW() + {@code seconds}. */
    private static int deadlinesNear(String table, String column, String regionId, long seconds) {
        return count("SELECT COUNT(*) FROM " + table + " t"
                + " INNER JOIN RealtyRegion rr ON rr.realtyRegionId = t.realtyRegionId"
                + " WHERE rr.worldGuardRegionId = '" + regionId + "'"
                + " AND t." + column + " BETWEEN NOW() + INTERVAL " + (seconds - 60) + " SECOND"
                + " AND NOW() + INTERVAL " + (seconds + 60) + " SECOND");
    }

    /** The minute scheduler: deadlines pause first while holding is unusable, then expiry runs. */
    private static List<ExpiredBidPayment> schedulerRun() {
        if (!holdingAvailable) {
            logic.pausePaymentDeadlines();
        }
        return logic.clearExpiredBidPayments();
    }

    @Test
    void deadlines_pushedWhileHoldingUnusable() {
        String newRules = freehold();
        inPayment(newRules);
        expirePayment(newRules);
        String oldRules = freehold();
        inPayment(oldRules);
        markOldRules(oldRules);
        expirePayment(oldRules);
        String offer = freehold();
        acceptedOffer(offer);
        expireOfferPayment(offer);
        String oldRulesOffer = freehold();
        acceptedOffer(oldRulesOffer);
        Assertions.assertEquals(1, update("""
                UPDATE FreeholdContractOfferPayment SET oldRules = TRUE
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, oldRulesOffer));
        expireOfferPayment(oldRulesOffer);
        holdingAvailable = false;

        logic.pausePaymentDeadlines();

        Assertions.assertEquals(1, deadlinesNear("FreeholdContractBidPayment", "paymentDeadline", newRules, 3600),
                "the full auction payment duration from now");
        Assertions.assertEquals(1, deadlinesNear("FreeholdContractAuction", "paymentDeadline", newRules, 3600),
                "the auction's own deadline moves with it");
        Assertions.assertEquals(1, deadlinesNear("FreeholdContractOfferPayment", "paymentDeadline", offer, 86400),
                "the configured offer payment duration from now");
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM FreeholdContractBidPayment p"
                + " INNER JOIN RealtyRegion rr ON rr.realtyRegionId = p.realtyRegionId"
                + " WHERE rr.worldGuardRegionId = '" + oldRules + "' AND p.paymentDeadline < NOW()"),
                "an old-rules payment never touches holding, so its deadline stands");
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM FreeholdContractOfferPayment p"
                + " INNER JOIN RealtyRegion rr ON rr.realtyRegionId = p.realtyRegionId"
                + " WHERE rr.worldGuardRegionId = '" + oldRulesOffer + "' AND p.paymentDeadline < NOW()"),
                "nor does an old-rules offer payment's");
        List<ExpiredOfferPayment> expiredOffers = logic.clearExpiredOfferPayments();
        Assertions.assertEquals(1, expiredOffers.size());
        Assertions.assertEquals(oldRulesOffer, expiredOffers.getFirst().regionId());
        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();
        Assertions.assertEquals(1, expired.size());
        Assertions.assertEquals(oldRules, expired.getFirst().regionId());
    }

    @Test
    void deadlines_paused_neverShortened() {
        String regionId = freehold();
        inPayment(regionId);
        update("""
                UPDATE FreeholdContractBidPayment SET paymentDeadline = NOW() + INTERVAL 3 HOUR
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);

        logic.pausePaymentDeadlines();

        Assertions.assertEquals(1, deadlinesNear("FreeholdContractBidPayment", "paymentDeadline", regionId, 3 * 3600));
    }

    @Test
    void buyerStillHasTimeAfterOutage() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = inPayment(regionId);
        payBidConfirmed(regionId, WORLD_ID, BUYER, 300);
        holdingAvailable = false;
        Assertions.assertTrue(schedulerRun().isEmpty());
        // The outage outlasts the deadline.
        expirePayment(regionId);
        Assertions.assertTrue(schedulerRun().isEmpty());

        holdingAvailable = true;
        Assertions.assertTrue(schedulerRun().isEmpty());

        FreeholdContractBidPaymentEntity after = bidPayment(regionId);
        Assertions.assertNotNull(after);
        Assertions.assertEquals(payment.bidId(), after.bidId(), "still the buyer's payment");
        Assertions.assertEquals(300, after.currentPayment(), 0.001);
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM FreeholdContractBidPayment p"
                + " INNER JOIN RealtyRegion rr ON rr.realtyRegionId = p.realtyRegionId"
                + " WHERE rr.worldGuardRegionId = '" + regionId + "' AND p.paymentDeadline > NOW()"));
    }

    @Test
    void runnerUpHandover_fromOldRules_isPausedLikeAnyNewRulesPayment() {
        String regionId = freehold();
        inPayment(regionId);
        markOldRules(regionId);
        expirePayment(regionId);
        holdingAvailable = false;

        Assertions.assertEquals(1, schedulerRun().size(), "the old-rules payment ends");
        Assertions.assertEquals(RUNNER_UP, bidPayment(regionId).bidderId());
        expirePayment(regionId);
        Assertions.assertTrue(schedulerRun().isEmpty(), "the runner-up's new-rules payment is paused");

        Assertions.assertEquals(RUNNER_UP, bidPayment(regionId).bidderId());
        Assertions.assertEquals(1, deadlinesNear("FreeholdContractBidPayment", "paymentDeadline", regionId, 3600));
    }

    @Test
    void offerExpiry_waitsWhileHoldingUnusable() {
        String regionId = freehold();
        acceptedOffer(regionId);
        expireOfferPayment(regionId);
        holdingAvailable = false;

        Assertions.assertTrue(logic.clearExpiredOfferPayments().isEmpty());

        holdingAvailable = true;
        Assertions.assertEquals(1, logic.clearExpiredOfferPayments().size());
    }
}
