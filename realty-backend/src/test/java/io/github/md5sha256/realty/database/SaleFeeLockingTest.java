package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptAgentInviteResult;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.ActiveAuction;
import io.github.md5sha256.realty.api.RealtyBackend.BidResult;
import io.github.md5sha256.realty.api.RealtyBackend.BuyResult;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.ExpiredBidPayment;
import io.github.md5sha256.realty.api.RealtyBackend.ExpiredBiddingAuction;
import io.github.md5sha256.realty.api.RealtyBackend.InviteAgentResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferListing;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayBidResult;
import io.github.md5sha256.realty.api.RealtyBackend.ResolveFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetFeeResult;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import io.github.md5sha256.realty.database.entity.PendingRefundEntity;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class SaleFeeLockingTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID RUNNER_UP = UUID.randomUUID();
    private static final UUID AGENT_A = UUID.randomUUID();
    private static final UUID AGENT_B = UUID.randomUUID();

    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);

    /** 5% paid by the buyer to the authority, 15% for a buyer holding 10 plots or more. */
    private static final LevyDefinition STAMP_DUTY = stampDuty(5, 15);

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static LevyDefinition stampDuty(double base, double from10Plots) {
        return new LevyDefinition("stamp-duty", true, true, FeeSide.BUYER, null, null,
                List.of(new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, base), new LevyTier(10, from10Plots)))));
    }

    private static LevyCatalog only(LevyDefinition levy) {
        return new LevyCatalog() {
            @Override
            public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
                return levy.name().equalsIgnoreCase(name) ? Optional.of(levy) : Optional.empty();
            }

            @Override
            public @NotNull Optional<LevyDefinition> defaultLevy() {
                return Optional.of(levy);
            }

            @Override
            public @NotNull List<LevyDefinition> all() {
                return List.of(levy);
            }
        };
    }

    private static ActorContext player(UUID id) {
        return ActorContext.player(id, false);
    }

    private static String freehold() {
        String regionId = "lock_region_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, GOV, TITLE_HOLDER));
        return regionId;
    }

    private static void makeAgent(String regionId, UUID agent) {
        Assertions.assertInstanceOf(InviteAgentResult.Success.class,
                logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, player(agent), false));
        Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class,
                logic.acceptAgentInvite(regionId, WORLD_ID, player(agent), false));
    }

    /** Agent A's 3% seller-paid fee, approved by the titleholder. */
    private static void agentFee(String regionId, UUID agent) {
        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(agent), 3, FeeSide.SELLER, false));
        Assertions.assertInstanceOf(ResolveFeeResult.Success.class,
                logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(agent)));
    }

    private static void auction(String regionId, UUID auctioneer) {
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(auctioneer), 3600, 3600, 100.0, 10.0));
    }

    private static void bid(String regionId, UUID bidder, double amount) {
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(bidder), amount, false));
    }

    /** Moves the auction's start and its bids two hours back, so bidding has ended, and settles it. */
    private static List<ExpiredBiddingAuction> endBidding(String regionId) {
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
        return logic.clearExpiredBiddingAuctions();
    }

    private static void expireBidPayment(String regionId) {
        update("""
                UPDATE FreeholdContractBidPayment SET paymentDeadline = NOW() - INTERVAL 1 DAY
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);
    }

    private static int update(String sql, String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session();
             PreparedStatement statement = session.getConnection().prepareStatement(sql)) {
            statement.setString(1, regionId);
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

    private static FreeholdContractOfferPaymentEntity offerPayment(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferPaymentMapper().selectByRegion(regionId, WORLD_ID);
        }
    }

    private static List<SaleFeeEntity> lines(String saleKind, int saleRef) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.saleFeeMapper().selectBySale(saleKind, saleRef);
        }
    }

    private static Party recipient(SaleFeeEntity line) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.partyMapper().selectById(line.recipientPartyId());
        }
    }

    private static SaleFeeEntity line(List<SaleFeeEntity> lines, String lineKind, String source) {
        return lines.stream()
                .filter(l -> l.lineKind().equals(lineKind) && l.source().equals(source))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + lineKind + " line " + source + " in " + lines));
    }

    /** Ends an auction won by {@link #BUYER} at 1000 under the stamp duty. */
    private static FreeholdContractBidPaymentEntity lockedAuction(String regionId) {
        levies = only(STAMP_DUTY);
        auction(regionId, TITLE_HOLDER);
        bid(regionId, BUYER, 1000);
        Assertions.assertEquals(1, endBidding(regionId).size());
        FreeholdContractBidPaymentEntity payment = bidPayment(regionId);
        Assertions.assertNotNull(payment);
        return payment;
    }

    @Test
    void buy_returnsQuote_writesNoRows() {
        levies = only(STAMP_DUTY);
        String regionId = freehold();

        BuyResult result = logic.executeBuy(regionId, WORLD_ID, player(BUYER), false);

        BuyResult.Success success = Assertions.assertInstanceOf(BuyResult.Success.class, result);
        Assertions.assertEquals(1050, success.fees().buyerTotal(), 0.001);
        Assertions.assertEquals(1000, success.fees().sellerReceives(), 0.001);
        Assertions.assertEquals(1, success.fees().lines().size());
        Assertions.assertEquals(GOV, success.fees().lines().getFirst().recipient());
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM SaleFee"));
    }

    @Test
    void recordBuyFees_writesPaidLines_andClearsSellerFees() {
        levies = only(STAMP_DUTY);
        String regionId = freehold();
        makeAgent(regionId, AGENT_A);
        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(AGENT_A), 2, FeeSide.BUYER, false));
        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 1, FeeSide.SELLER, false));
        BuyResult.Success success = Assertions.assertInstanceOf(BuyResult.Success.class,
                logic.executeBuy(regionId, WORLD_ID, player(BUYER), false));
        // A buy runs without an agent, so the agent's fee does not apply; the titleholder's does.
        Assertions.assertEquals(1050, success.fees().buyerTotal(), 0.001);
        Assertions.assertEquals(990, success.fees().sellerReceives(), 0.001);

        logic.recordBuyFees(regionId, WORLD_ID, BUYER, Party.personal(TITLE_HOLDER), success.fees());

        Assertions.assertEquals(3, count("SELECT COUNT(*) FROM SaleFee WHERE saleKind = 'buy' AND saleRef IS NULL"
                + " AND status = 'paid' AND completedAt IS NOT NULL"));
        Assertions.assertEquals(1, count("SELECT COUNT(*) FROM SaleFee WHERE lineKind = 'proceeds' AND amount = 990"));
        Assertions.assertTrue(logic.listFees(regionId, WORLD_ID).stream().allMatch(l -> l.role() == null),
                "the agent's and the old titleholder's fees are gone: " + logic.listFees(regionId, WORLD_ID));
    }

    @Test
    void auctionEnd_locksLinesAndTotalDue() {
        String regionId = freehold();

        FreeholdContractBidPaymentEntity payment = lockedAuction(regionId);

        Assertions.assertEquals(1050, payment.totalDue(), 0.001);
        Assertions.assertFalse(payment.oldRules());
        List<SaleFeeEntity> lines = lines("bid", payment.bidId());
        Assertions.assertEquals(2, lines.size());
        SaleFeeEntity levy = line(lines, "fee", "stamp-duty");
        Assertions.assertEquals(50, levy.amount(), 0.001);
        Assertions.assertEquals(FeeSide.BUYER, levy.side());
        Assertions.assertEquals(GOV, recipient(levy));
        Assertions.assertEquals("locked", levy.status());
        Assertions.assertNull(levy.completedAt());
        SaleFeeEntity proceeds = line(lines, "proceeds", "proceeds");
        Assertions.assertEquals(1000, proceeds.amount(), 0.001);
        Assertions.assertEquals(Party.personal(TITLE_HOLDER), recipient(proceeds));
        Assertions.assertEquals(BUYER, proceeds.buyerId());

        ActiveAuction active = logic.listActiveAuctions().stream()
                .filter(a -> a.regionId().equals(regionId)).findFirst().orElseThrow();
        Assertions.assertNotNull(active.fees());
        Assertions.assertEquals(1050, active.fees().buyerTotal(), 0.001);
        Assertions.assertEquals(1000, active.fees().sellerReceives(), 0.001);
        Assertions.assertEquals(1, active.fees().lines().size());
    }

    @Test
    void auctionEnd_withoutHolding_leavesAuction() {
        String regionId = freehold();
        auction(regionId, TITLE_HOLDER);
        bid(regionId, BUYER, 1000);
        holdingAvailable = false;

        Assertions.assertTrue(endBidding(regionId).isEmpty());
        Assertions.assertTrue(logic.clearExpiredBiddingAuctions().isEmpty());

        Assertions.assertNull(bidPayment(regionId));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM SaleFee"));
        holdingAvailable = true;
        Assertions.assertEquals(1, logic.clearExpiredBiddingAuctions().size());
        Assertions.assertNotNull(bidPayment(regionId));
    }

    @Test
    void auctionEnd_agentFeeOnlyForAuctioneer() {
        String regionId = freehold();
        makeAgent(regionId, AGENT_A);
        makeAgent(regionId, AGENT_B);
        agentFee(regionId, AGENT_A);
        auction(regionId, AGENT_B);
        bid(regionId, BUYER, 1000);

        Assertions.assertEquals(1, endBidding(regionId).size());

        FreeholdContractBidPaymentEntity payment = bidPayment(regionId);
        List<SaleFeeEntity> lines = lines("bid", payment.bidId());
        Assertions.assertTrue(lines.stream().noneMatch(l -> l.source().equals("agent fee")), lines.toString());
        Assertions.assertEquals(1000, line(lines, "proceeds", "proceeds").amount(), 0.001);
    }

    @Test
    void auctionEnd_agentFeeForAuctioneer() {
        String regionId = freehold();
        makeAgent(regionId, AGENT_A);
        agentFee(regionId, AGENT_A);
        auction(regionId, AGENT_A);
        bid(regionId, BUYER, 1000);

        Assertions.assertEquals(1, endBidding(regionId).size());

        List<SaleFeeEntity> lines = lines("bid", bidPayment(regionId).bidId());
        SaleFeeEntity agent = line(lines, "fee", "agent fee");
        Assertions.assertEquals(30, agent.amount(), 0.001);
        Assertions.assertEquals(Party.personal(AGENT_A), recipient(agent));
        Assertions.assertEquals(970, line(lines, "proceeds", "proceeds").amount(), 0.001);
    }

    @Test
    void feeChangedAfterLock_doesNotChangeSale() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = lockedAuction(regionId);

        levies = only(stampDuty(10, 20));
        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 2, FeeSide.BUYER, false));

        FreeholdContractBidPaymentEntity after = bidPayment(regionId);
        Assertions.assertEquals(1050, after.totalDue(), 0.001);
        Assertions.assertEquals(2, lines("bid", payment.bidId()).size());
        PayBidResult.FullyPaid paid = Assertions.assertInstanceOf(PayBidResult.FullyPaid.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 1050));
        Assertions.assertEquals(1050, paid.fees().buyerTotal(), 0.001);
        Assertions.assertEquals(50, paid.fees().lines().getFirst().amount(), 0.001);
    }

    @Test
    void runnerUp_relocksForNewBidder() {
        for (int i = 0; i < 10; i++) {
            Assertions.assertTrue(logic.createFreehold("runner_plot_" + REGION_COUNTER.incrementAndGet(),
                    WORLD_ID, null, GOV, RUNNER_UP));
        }
        String regionId = freehold();
        levies = only(STAMP_DUTY);
        auction(regionId, TITLE_HOLDER);
        bid(regionId, RUNNER_UP, 900);
        bid(regionId, BUYER, 1000);
        Assertions.assertEquals(1, endBidding(regionId).size());
        FreeholdContractBidPaymentEntity first = bidPayment(regionId);
        Assertions.assertEquals(1050, first.totalDue(), 0.001);
        expireBidPayment(regionId);

        List<ExpiredBidPayment> expired = logic.clearExpiredBidPayments();

        Assertions.assertEquals(1, expired.size());
        Assertions.assertEquals(BUYER, expired.getFirst().bidderId());
        Assertions.assertFalse(expired.getFirst().oldRules());
        Assertions.assertTrue(lines("bid", first.bidId()).isEmpty(), "the first bidder's lines are gone");
        FreeholdContractBidPaymentEntity next = bidPayment(regionId);
        Assertions.assertEquals(RUNNER_UP, next.bidderId());
        Assertions.assertEquals(1035, next.totalDue(), 0.001);
        List<SaleFeeEntity> lines = lines("bid", next.bidId());
        Assertions.assertEquals(2, lines.size());
        SaleFeeEntity levy = line(lines, "fee", "stamp-duty");
        Assertions.assertEquals(15, levy.percent(), 0.001);
        Assertions.assertEquals(135, levy.amount(), 0.001);
        Assertions.assertEquals(RUNNER_UP, levy.buyerId());
        Assertions.assertEquals(900, line(lines, "proceeds", "proceeds").amount(), 0.001);
    }

    @Test
    void acceptOffer_withoutHolding_refused() {
        String regionId = freehold();
        openToOffers(regionId, WORLD_ID);
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, player(BUYER), 1000, false));
        holdingAvailable = false;

        AcceptOfferResult result = logic.acceptOffer(regionId, WORLD_ID, player(TITLE_HOLDER), BUYER);

        Assertions.assertInstanceOf(AcceptOfferResult.NoHoldingAccount.class, result);
        Assertions.assertNull(offerPayment(regionId));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM SaleFee"));
    }

    @Test
    void acceptOffer_recordsAcceptedBy_andAgentFee() {
        levies = only(STAMP_DUTY);
        String regionId = freehold();
        makeAgent(regionId, AGENT_A);
        agentFee(regionId, AGENT_A);
        openToOffers(regionId, WORLD_ID);
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, player(BUYER), 1000, false));

        AcceptOfferResult result = logic.acceptOffer(regionId, WORLD_ID, player(AGENT_A), BUYER);

        Assertions.assertInstanceOf(AcceptOfferResult.Success.class, result);
        FreeholdContractOfferPaymentEntity payment = offerPayment(regionId);
        Assertions.assertEquals(AGENT_A, payment.acceptedBy());
        Assertions.assertEquals(1050, payment.totalDue(), 0.001);
        Assertions.assertFalse(payment.oldRules());
        List<SaleFeeEntity> lines = lines("offer", payment.offerId());
        Assertions.assertEquals(3, lines.size());
        Assertions.assertEquals(50, line(lines, "fee", "stamp-duty").amount(), 0.001);
        SaleFeeEntity agent = line(lines, "fee", "agent fee");
        Assertions.assertEquals(30, agent.amount(), 0.001);
        Assertions.assertEquals(FeeSide.SELLER, agent.side());
        Assertions.assertEquals(Party.personal(AGENT_A), recipient(agent));
        Assertions.assertEquals(970, line(lines, "proceeds", "proceeds").amount(), 0.001);

        OfferListing listing = logic.listOfferListings().stream()
                .filter(l -> l.regionId().equals(regionId)).findFirst().orElseThrow();
        Assertions.assertNotNull(listing.fees());
        Assertions.assertEquals(1050, listing.fees().buyerTotal(), 0.001);
        Assertions.assertEquals(970, listing.fees().sellerReceives(), 0.001);
        Assertions.assertEquals(List.of("stamp-duty", "agent fee"),
                listing.fees().lines().stream().map(FeeLine::source).toList());
    }

    @Test
    void payBid_againstTotalDue() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = lockedAuction(regionId);

        PayBidResult.Success partial = Assertions.assertInstanceOf(PayBidResult.Success.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 50));
        Assertions.assertEquals(1000, partial.remaining(), 0.001);
        Assertions.assertFalse(partial.oldRules());
        Assertions.assertEquals(1000, Assertions.assertInstanceOf(PayBidResult.ExceedsAmountOwed.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 1001)).amountOwed(), 0.001);
        logic.rollbackPayBid(regionId, WORLD_ID, BUYER, 50);

        PayBidResult.ExceedsAmountOwed exceeds = Assertions.assertInstanceOf(PayBidResult.ExceedsAmountOwed.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 1051));
        Assertions.assertEquals(1050, exceeds.amountOwed(), 0.001);
        PayBidResult.FullyPaid paid = Assertions.assertInstanceOf(PayBidResult.FullyPaid.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 1050));
        Assertions.assertFalse(paid.oldRules());
        Assertions.assertEquals(payment.bidId(), paid.saleRef());
        Assertions.assertEquals(1050, paid.fees().buyerTotal(), 0.001);

        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);

        Assertions.assertEquals(BUYER, logic.getFreeholdContract(regionId, WORLD_ID).titleHolderId());
        Assertions.assertTrue(lines("bid", payment.bidId()).stream().allMatch(l -> l.completedAt() != null));
    }

    @Test
    void oldRulesPayment_noFees_priceIsTotal() {
        levies = only(STAMP_DUTY);
        String regionId = freehold();
        auction(regionId, TITLE_HOLDER);
        bid(regionId, BUYER, 1000);
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractBidPaymentMapper().insertPayment(regionId, WORLD_ID, BUYER, 1000,
                    LocalDateTime.now().plusDays(1), 1000);
            session.commit();
        }
        update("""
                UPDATE FreeholdContractBidPayment SET oldRules = TRUE
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);

        PayBidResult.FullyPaid paid = Assertions.assertInstanceOf(PayBidResult.FullyPaid.class,
                logic.payBid(regionId, WORLD_ID, BUYER, 1000));

        Assertions.assertTrue(paid.oldRules());
        Assertions.assertEquals(FeeQuote.none(1000), paid.fees());
        ActiveAuction active = logic.listActiveAuctions().stream()
                .filter(a -> a.regionId().equals(regionId)).findFirst().orElseThrow();
        Assertions.assertNull(active.fees());
    }

    @Test
    void deleteRegion_withHeldInstalments_recordsPendingRefund() {
        String regionId = freehold();
        FreeholdContractBidPaymentEntity payment = lockedAuction(regionId);
        Assertions.assertInstanceOf(PayBidResult.Success.class, logic.payBid(regionId, WORLD_ID, BUYER, 300));

        Assertions.assertEquals(1, logic.deleteRegion(regionId, WORLD_ID));

        List<PendingRefundEntity> refunds;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            refunds = wrapper.pendingRefundMapper().selectDue(LocalDateTime.now().plusDays(1), 100);
        }
        Assertions.assertEquals(1, refunds.size());
        PendingRefundEntity refund = refunds.getFirst();
        Assertions.assertEquals(BUYER, refund.buyerId());
        Assertions.assertEquals(300, refund.amount(), 0.001);
        Assertions.assertEquals(regionId, refund.regionName());
        Assertions.assertEquals(WORLD_ID, refund.worldId());
        Assertions.assertEquals("region-deleted", refund.reason());
        Assertions.assertTrue(lines("bid", payment.bidId()).isEmpty(), "the fallen-through sale's lines are gone");
    }

    @Test
    void deleteRegion_withoutInstalments_recordsNothing() {
        String regionId = freehold();
        lockedAuction(regionId);

        Assertions.assertEquals(1, logic.deleteRegion(regionId, WORLD_ID));

        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM PendingRefund"));
    }

    @Test
    void previewFees_savesNothing() {
        levies = only(STAMP_DUTY);
        String regionId = freehold();

        FeeQuote quote = logic.previewFees(regionId, WORLD_ID, BUYER, 2000);

        Assertions.assertEquals(2100, quote.buyerTotal(), 0.001);
        Assertions.assertEquals(2000, quote.sellerReceives(), 0.001);
        Assertions.assertEquals(FeeQuote.none(2000), logic.previewFees("no_such_region", WORLD_ID, BUYER, 2000));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM SaleFee"));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM FreeholdContractBidPayment"));
        Assertions.assertEquals(0, count("SELECT COUNT(*) FROM FreeholdContractOfferPayment"));
    }
}
