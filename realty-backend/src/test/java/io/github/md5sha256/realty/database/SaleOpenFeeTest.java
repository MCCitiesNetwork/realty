package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptAgentInviteResult;
import io.github.md5sha256.realty.api.RealtyBackend.BidResult;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.FeeListing;
import io.github.md5sha256.realty.api.RealtyBackend.InviteAgentResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayBidResult;
import io.github.md5sha256.realty.api.RealtyBackend.RemoveFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.ResolveFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetLevyResult;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * While a plot has bids or offers open, nothing may raise what its buyers pay: no new or higher
 * buyer-paid fee, and no levy change.
 */
class SaleOpenFeeTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID AGENT = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID RUNNER_UP = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);
    private static final ActorContext MANAGER = new ActorContext(MANAGER_ID, Set.of(GOV), Set.of(), false);

    private static final LevyDefinition NATIONAL_LEVY = levy("national-levy", true, 2);
    private static final LevyDefinition TOWN_LEVY = levy("town-levy", false, 5);
    private static final LevyDefinition REGION_LEVY = levy("region-levy", false, 1.5);

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static LevyDefinition levy(String name, boolean isDefault, double percent) {
        return new LevyDefinition(name, true, isDefault, FeeSide.BUYER, null, null,
                List.of(new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, percent)))));
    }

    private static LevyCatalog catalog(LevyDefinition... definitions) {
        List<LevyDefinition> all = Arrays.asList(definitions);
        return new LevyCatalog() {
            @Override
            public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
                return all.stream().filter(levy -> levy.name().equals(name.toLowerCase(Locale.ROOT))).findFirst();
            }

            @Override
            public @NotNull Optional<LevyDefinition> defaultLevy() {
                return all.stream().filter(LevyDefinition::isDefault).findFirst();
            }

            @Override
            public @NotNull List<LevyDefinition> all() {
                return all;
            }
        };
    }

    private static ActorContext player(UUID id) {
        return ActorContext.player(id, false);
    }

    private static String freehold() {
        String regionId = "open_region_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, GOV, TITLE_HOLDER));
        return regionId;
    }

    /** An auction taking bids, with one bid. */
    private static String freeholdWithBidding() {
        String regionId = freehold();
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(TITLE_HOLDER), 3600, 3600, 100.0, 10.0));
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(BUYER), 500.0, false));
        return regionId;
    }

    private static void placeOffer(String regionId) {
        openToOffers(regionId, WORLD_ID);
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, player(BUYER), 500.0, false));
    }

    private static SetFeeResult titleholderFee(String regionId, double percent, FeeSide side) {
        return logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), percent, side, false);
    }

    private static FeeListing titleholderListing(String regionId) {
        return logic.listFees(regionId, WORLD_ID).stream()
                .filter(listing -> Party.personal(TITLE_HOLDER).equals(listing.recipient()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no titleholder fee"));
    }

    private static String regionLevyChoice(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            int realtyRegionId = wrapper.realtyRegionMapper()
                    .selectByWorldGuardRegion(regionId, WORLD_ID).realtyRegionId();
            return wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId);
        }
    }

    /** Moves the auction's start and its bids two hours back, so bidding has ended, and settles it. */
    private static void endBidding(String regionId) {
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
        Assertions.assertEquals(1, logic.clearExpiredBiddingAuctions().size());
    }

    private static void expireBidPayment(String regionId) {
        update("""
                UPDATE FreeholdContractBidPayment SET paymentDeadline = NOW() - INTERVAL 1 DAY
                WHERE realtyRegionId = (SELECT realtyRegionId FROM RealtyRegion WHERE worldGuardRegionId = ?)
                """, regionId);
    }

    private static FreeholdContractBidPaymentEntity bidPayment(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractBidPaymentMapper().selectByRegion(regionId, WORLD_ID);
        }
    }

    /** The percentages of the buyer-paid lines locked for the bid, in ascending order. */
    private static List<Double> buyerPercents(int bidId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.saleFeeMapper().selectBySale("bid", bidId).stream()
                    .filter(line -> line.side() == FeeSide.BUYER)
                    .map(SaleFeeEntity::percent)
                    .sorted()
                    .toList();
        }
    }

    private static void update(String sql, String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session();
             PreparedStatement statement = session.getConnection().prepareStatement(sql)) {
            statement.setString(1, regionId);
            statement.executeUpdate();
            session.commit();
        } catch (SQLException ex) {
            throw new AssertionError(ex);
        }
    }

    // --- Player fees ---

    @Test
    void buyerFeeRaise_duringBidding_refused() {
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 2, FeeSide.BUYER));
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(TITLE_HOLDER), 3600, 3600, 100.0, 10.0));

        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 3, FeeSide.BUYER));
        Assertions.assertEquals("2%", titleholderListing(regionId).rate());

        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(BUYER), 500.0, false));
        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 3, FeeSide.BUYER));
        Assertions.assertEquals("2%", titleholderListing(regionId).rate());
    }

    @Test
    void newBuyerFee_withOpenOffer_refused() {
        String regionId = freehold();
        placeOffer(regionId);

        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 2, FeeSide.BUYER));
        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class,
                logic.setFee(regionId, WORLD_ID, MANAGER, 2, FeeSide.BUYER, true));
        Assertions.assertTrue(logic.listFees(regionId, WORLD_ID).isEmpty());
    }

    @Test
    void sellerToBuyerSwitch_refused() {
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 2, FeeSide.SELLER));
        freeholdAuction(regionId);

        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 1, FeeSide.BUYER));
        Assertions.assertEquals(FeeSide.SELLER, titleholderListing(regionId).side());
    }

    private static void freeholdAuction(String regionId) {
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(TITLE_HOLDER), 3600, 3600, 100.0, 10.0));
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(BUYER), 500.0, false));
    }

    @Test
    void buyerFeeLowerOrRemove_duringBidding_allowed() {
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 3, FeeSide.BUYER));
        freeholdAuction(regionId);

        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 2, FeeSide.BUYER));
        Assertions.assertEquals("2%", titleholderListing(regionId).rate());
        Assertions.assertInstanceOf(RemoveFeeResult.Success.class,
                logic.removeFee(regionId, WORLD_ID, player(TITLE_HOLDER), false));
        Assertions.assertTrue(logic.listFees(regionId, WORLD_ID).isEmpty());
    }

    @Test
    void sellerFeeChange_duringBidding_allowed() {
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 3, FeeSide.BUYER));
        freeholdAuction(regionId);

        // Moving a buyer-paid fee to the seller lowers what the buyer pays.
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 5, FeeSide.SELLER));
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 8, FeeSide.SELLER));
        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, MANAGER, 1, FeeSide.SELLER, true));
        Assertions.assertEquals("8%", titleholderListing(regionId).rate());
    }

    @Test
    void approveFee_duringBidding_allowed() {
        String regionId = freehold();
        Assertions.assertInstanceOf(InviteAgentResult.Success.class,
                logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, player(AGENT), false));
        Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class,
                logic.acceptAgentInvite(regionId, WORLD_ID, player(AGENT), false));
        freeholdAuction(regionId);

        SetFeeResult.Success agentFee = Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false));
        Assertions.assertTrue(agentFee.needsApproval());
        Assertions.assertInstanceOf(ResolveFeeResult.Success.class,
                logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT)));
    }

    @Test
    void paymentPhase_raisesRefused_allowedAfterFinalize() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY);
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 2, FeeSide.BUYER));
        freeholdAuction(regionId);
        endBidding(regionId);

        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 4, FeeSide.BUYER));
        Assertions.assertEquals(new SetLevyResult.SaleOpen(1),
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "town-levy"));
        FreeholdContractBidPaymentEntity payment = bidPayment(regionId);
        Assertions.assertEquals(List.of(2.0, 2.0), buyerPercents(payment.bidId()),
                "the locked titleholder fee and national levy keep their percent");
        Assertions.assertEquals(520.0, payment.totalDue(), 0.001);

        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, payBidConfirmed(regionId, WORLD_ID, BUYER, 520.0));
        Assertions.assertTrue(logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER));

        Assertions.assertInstanceOf(SetFeeResult.Success.class,
                logic.setFee(regionId, WORLD_ID, player(BUYER), 4, FeeSide.BUYER, false));
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "town-levy"));
    }

    @Test
    void runnerUpHandover_raisesRefused_runnerUpTotalUnchanged() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY);
        String regionId = freehold();
        Assertions.assertInstanceOf(SetFeeResult.Success.class, titleholderFee(regionId, 2, FeeSide.BUYER));
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(TITLE_HOLDER), 3600, 3600, 100.0, 10.0));
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(RUNNER_UP), 400.0, false));
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(BUYER), 500.0, false));
        endBidding(regionId);
        expireBidPayment(regionId);
        Assertions.assertEquals(1, logic.clearExpiredBidPayments().size());
        FreeholdContractBidPaymentEntity handedOver = bidPayment(regionId);
        Assertions.assertEquals(RUNNER_UP, handedOver.bidderId());
        Assertions.assertEquals(416.0, handedOver.totalDue(), 0.001);

        Assertions.assertInstanceOf(SetFeeResult.SaleOpen.class, titleholderFee(regionId, 5, FeeSide.BUYER));
        Assertions.assertEquals(new SetLevyResult.SaleOpen(1),
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "town-levy"));
        Assertions.assertEquals(new SetLevyResult.Success(null, 1), logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));

        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));
        Assertions.assertEquals("2%", titleholderListing(regionId).rate());
        FreeholdContractBidPaymentEntity after = bidPayment(regionId);
        Assertions.assertEquals(416.0, after.totalDue(), 0.001);
        Assertions.assertEquals(List.of(2.0, 2.0), buyerPercents(after.bidId()));
    }

    // --- Levies ---

    @Test
    void regionLevyChange_duringBidding_refused() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY, REGION_LEVY);
        String bidding = freeholdWithBidding();

        Assertions.assertEquals(new SetLevyResult.SaleOpen(1),
                logic.setRegionLevy(bidding, WORLD_ID, MANAGER, "region-levy"));
        Assertions.assertEquals("national-levy", logic.chosenLevy(bidding, WORLD_ID));

        String offered = freehold();
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setRegionLevy(offered, WORLD_ID, MANAGER, "region-levy"));
        placeOffer(offered);
        Assertions.assertEquals(new SetLevyResult.SaleOpen(1), logic.clearRegionLevy(offered, WORLD_ID, MANAGER));
        Assertions.assertEquals("region-levy", logic.chosenLevy(offered, WORLD_ID));
    }

    @Test
    void authorityLevy_pinsOpenPlots_appliesToOthers() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY, REGION_LEVY);
        String bidding = freeholdWithBidding();
        String offered = freehold();
        placeOffer(offered);
        String ownChoice = freehold();
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setRegionLevy(ownChoice, WORLD_ID, MANAGER, "region-levy"));
        placeOffer(ownChoice);
        String closed = freehold();

        Assertions.assertEquals(new SetLevyResult.Success(null, 2), logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));

        Assertions.assertEquals("national-levy", logic.chosenLevy(bidding, WORLD_ID));
        Assertions.assertEquals("national-levy", regionLevyChoice(bidding));
        Assertions.assertEquals("national-levy", logic.chosenLevy(offered, WORLD_ID));
        Assertions.assertEquals("region-levy", logic.chosenLevy(ownChoice, WORLD_ID));
        Assertions.assertEquals("town-levy", logic.chosenLevy(closed, WORLD_ID));
        Assertions.assertNull(regionLevyChoice(closed));

        // Pinned, the open plots keep their levy when the authority's choice is cleared too.
        Assertions.assertEquals(new SetLevyResult.Success("town-levy", 0), logic.clearAuthorityLevy(GOV, MANAGER));
        Assertions.assertEquals("national-levy", logic.chosenLevy(bidding, WORLD_ID));
        Assertions.assertEquals("national-levy", logic.chosenLevy(closed, WORLD_ID));
    }

    @Test
    void authorityLevyClear_pinsOpenPlots() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY);
        String closed = freehold();
        Assertions.assertInstanceOf(SetLevyResult.Success.class, logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));
        String bidding = freeholdWithBidding();

        Assertions.assertEquals(new SetLevyResult.Success("town-levy", 1), logic.clearAuthorityLevy(GOV, MANAGER));

        Assertions.assertEquals("town-levy", logic.chosenLevy(bidding, WORLD_ID));
        Assertions.assertEquals("national-levy", logic.chosenLevy(closed, WORLD_ID));
    }

    @Test
    void authorityLevy_sameLevyForOpenPlot_notPinned() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY);
        String bidding = freeholdWithBidding();

        Assertions.assertEquals(new SetLevyResult.Success(null, 0),
                logic.setAuthorityLevy(GOV, MANAGER, "national-levy"));
        Assertions.assertNull(regionLevyChoice(bidding));
    }

    @Test
    void authorityLevy_openPlotWithNoLevy_refused() {
        levies = catalog(TOWN_LEVY);
        freeholdWithBidding();
        String offered = freehold();
        placeOffer(offered);
        String closed = freehold();

        Assertions.assertEquals(new SetLevyResult.SaleOpen(2), logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));

        Assertions.assertNull(logic.chosenLevy(closed, WORLD_ID));
        Assertions.assertNull(logic.chosenLevy(offered, WORLD_ID));
        Assertions.assertNull(regionLevyChoice(offered));
    }

    @Test
    void authorityLevy_duringPaymentPhase_pins_appliesAfterFinalize() {
        levies = catalog(NATIONAL_LEVY, TOWN_LEVY);
        String regionId = freeholdWithBidding();
        endBidding(regionId);

        Assertions.assertEquals(new SetLevyResult.Success(null, 1), logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));
        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));

        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, payBidConfirmed(regionId, WORLD_ID, BUYER, 510.0));
        Assertions.assertTrue(logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER));

        Assertions.assertEquals(new SetLevyResult.Success("national-levy"),
                logic.clearRegionLevy(regionId, WORLD_ID, MANAGER));
        Assertions.assertEquals("town-levy", logic.chosenLevy(regionId, WORLD_ID));
    }
}
