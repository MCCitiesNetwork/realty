package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.BuyResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.RegionInfo;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferEntity;
import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A purchase is reserved in the database before the buyer is charged, and undone if the
 * charge fails. These tests hold the undoing to its word: a purchase that was not paid
 * for leaves the region exactly as it found it.
 *
 * <p>It did not. The reservation recorded the sale and withdrew the region's offers and
 * auctioneers, and the rollback put back only the title and the price. One plot on a
 * live server showed sixteen sales and had changed hands once.</p>
 */
class FailedPurchaseTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID OFFERER = UUID.randomUUID();
    private static final UUID AUCTIONEER = UUID.randomUUID();
    private static final double ASKING_PRICE = 1000.0;

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    /** A region for sale at the asking price, held by the title holder. */
    private static String regionForSale() {
        String regionId = "failed_purchase_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, ASKING_PRICE, AUTHORITY, TITLE_HOLDER));
        return regionId;
    }

    /** The first step of a purchase. One that is paid for has no second step. */
    private static BuyResult.Success reserve(String regionId, UUID buyer) {
        return Assertions.assertInstanceOf(BuyResult.Success.class,
                logic.executeBuy(regionId, WORLD_ID, buyer));
    }

    /** What a buyer who cannot pay does to a region: a reservation, then its undoing. */
    private static void tryToBuyAndFailToPay(String regionId, UUID buyer) {
        logic.rollbackBuy(regionId, WORLD_ID, buyer, reserve(regionId, buyer));
    }

    private static List<FreeholdHistoryEntity> salesRecorded(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdHistoryMapper().searchHistory(
                    regionId, WORLD_ID, HistoryEventType.BUY.name(), null, null, 100, 0);
        }
    }

    private static UUID titleHolderOf(String regionId) {
        return logic.getRegionInfo(regionId, WORLD_ID).freehold().titleHolderId();
    }

    private static List<FreeholdContractOfferEntity> offersOn(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            List<FreeholdContractOfferEntity> offers =
                    wrapper.freeholdContractOfferMapper().selectByRegion(regionId, WORLD_ID);
            return offers == null ? List.of() : offers;
        }
    }

    private static boolean isSanctioned(String regionId, UUID auctioneer) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .existsByRegionAndAuctioneer(regionId, WORLD_ID, auctioneer);
        }
    }

    private static void sanction(String regionId, UUID auctioneer) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractSanctionedAuctioneerMapper().insert(regionId, WORLD_ID, auctioneer);
            session.commit();
        }
    }

    private static void placeOffer(String regionId, UUID offerer, double price) {
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, offerer, price));
    }

    // --- A purchase that was not paid for ---

    @Test
    void aPurchaseThatWasNotPaidForIsNotInTheHistory() {
        String regionId = regionForSale();

        tryToBuyAndFailToPay(regionId, BUYER);

        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    @Test
    void tryingAgainAndAgainStillLeavesNothingInTheHistory() {
        // One player, seven attempts in an afternoon, none of them paid for.
        String regionId = regionForSale();

        for (int attempt = 0; attempt < 7; attempt++) {
            tryToBuyAndFailToPay(regionId, BUYER);
        }

        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    @Test
    void aPurchaseThatWasNotPaidForLeavesTheTitleAndThePriceAsTheyWere() {
        String regionId = regionForSale();

        tryToBuyAndFailToPay(regionId, BUYER);

        RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
        Assertions.assertEquals(TITLE_HOLDER, info.freehold().titleHolderId());
        Assertions.assertEquals(ASKING_PRICE, info.freehold().price());
    }

    @Test
    void aPurchaseThatWasNotPaidForLeavesEveryOfferAsItWas() {
        // Anybody without the money could otherwise clear a plot of every offer on it.
        String regionId = regionForSale();
        UUID secondOfferer = UUID.randomUUID();
        placeOffer(regionId, OFFERER, 500.0);
        placeOffer(regionId, secondOfferer, 750.0);
        List<FreeholdContractOfferEntity> before = offersOn(regionId);

        tryToBuyAndFailToPay(regionId, BUYER);

        List<FreeholdContractOfferEntity> after = offersOn(regionId);
        Assertions.assertEquals(2, after.size());
        for (FreeholdContractOfferEntity was : before) {
            FreeholdContractOfferEntity is = after.stream()
                    .filter(offer -> offer.offererId().equals(was.offererId()))
                    .findFirst().orElseThrow();
            Assertions.assertEquals(was.offerPrice(), is.offerPrice());
            Assertions.assertEquals(was.offerTime(), is.offerTime(), "made when it was first made");
        }
    }

    @Test
    void anOfferPutBackCanStillBeAccepted() {
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);

        tryToBuyAndFailToPay(regionId, BUYER);

        Assertions.assertInstanceOf(AcceptOfferResult.Success.class,
                logic.acceptOffer(regionId, WORLD_ID, AUTHORITY, OFFERER));
    }

    @Test
    void aPurchaseThatWasNotPaidForLeavesTheAuctioneersSanctioned() {
        String regionId = regionForSale();
        UUID secondAuctioneer = UUID.randomUUID();
        sanction(regionId, AUCTIONEER);
        sanction(regionId, secondAuctioneer);

        tryToBuyAndFailToPay(regionId, BUYER);

        Assertions.assertTrue(isSanctioned(regionId, AUCTIONEER));
        Assertions.assertTrue(isSanctioned(regionId, secondAuctioneer));
    }

    @Test
    void aPurchaseThatWasNotPaidForDoesNotMoveTheLastSalePrice() {
        String regionId = regionForSale();

        tryToBuyAndFailToPay(regionId, BUYER);

        Assertions.assertNull(logic.getRegionInfo(regionId, WORLD_ID).lastSoldPrice());
    }

    @Test
    void afterAFailedAttemptTheRegionCanStillBeBought() {
        String regionId = regionForSale();
        UUID someoneWithMoney = UUID.randomUUID();

        tryToBuyAndFailToPay(regionId, BUYER);
        reserve(regionId, someoneWithMoney);

        List<FreeholdHistoryEntity> sales = salesRecorded(regionId);
        Assertions.assertEquals(1, sales.size());
        Assertions.assertEquals(someoneWithMoney, sales.getFirst().buyerId());
        Assertions.assertEquals(someoneWithMoney, titleHolderOf(regionId));
    }

    // --- What somebody else did between the reservation and its undoing ---

    @Test
    void anOffererWhoOfferedAgainInTheMeantimeDoesNotStopTheRegionGoingBack() {
        // Their offer was withdrawn by the reservation, so nothing stopped them making
        // another. Putting the old one back beside it broke the rule of one offer each,
        // the rollback failed whole, and the buyer kept a region they had not paid for.
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);
        BuyResult.Success reserved = reserve(regionId, BUYER);
        placeOffer(regionId, OFFERER, 650.0);

        Assertions.assertDoesNotThrow(() -> logic.rollbackBuy(regionId, WORLD_ID, BUYER, reserved));

        Assertions.assertEquals(TITLE_HOLDER, titleHolderOf(regionId));
        Assertions.assertEquals(ASKING_PRICE, logic.getRegionInfo(regionId, WORLD_ID).freehold().price());
        Assertions.assertEquals(List.of(), salesRecorded(regionId));
        List<FreeholdContractOfferEntity> offers = offersOn(regionId);
        Assertions.assertEquals(1, offers.size());
        Assertions.assertEquals(650.0, offers.getFirst().offerPrice(), "their later offer is the one that stands");
    }

    @Test
    void anAuctioneerSanctionedAgainInTheMeantimeDoesNotStopTheRegionGoingBack() {
        String regionId = regionForSale();
        sanction(regionId, AUCTIONEER);
        BuyResult.Success reserved = reserve(regionId, BUYER);
        sanction(regionId, AUCTIONEER);

        Assertions.assertDoesNotThrow(() -> logic.rollbackBuy(regionId, WORLD_ID, BUYER, reserved));

        Assertions.assertEquals(TITLE_HOLDER, titleHolderOf(regionId));
        Assertions.assertTrue(isSanctioned(regionId, AUCTIONEER));
        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    @Test
    void aRegionHeldByNobodyGoesBackToNobody() {
        // Held by its authority, with no title holder of its own.
        String regionId = "failed_purchase_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, ASKING_PRICE, AUTHORITY, null));

        tryToBuyAndFailToPay(regionId, BUYER);

        Assertions.assertNull(titleHolderOf(regionId));
        Assertions.assertEquals(ASKING_PRICE, logic.getRegionInfo(regionId, WORLD_ID).freehold().price());
        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    // --- The record that is removed is the one that was written ---

    @Test
    void undoingAFailedAttemptLeavesAnEarlierPurchaseByTheSameBuyerInTheHistory() {
        // The buyer bought the region once and paid. It came back on the market, they
        // tried again and could not pay. Only the second record goes.
        String regionId = regionForSale();
        reserve(regionId, BUYER);
        logic.setTitleHolder(regionId, WORLD_ID, TITLE_HOLDER);
        logic.setPrice(regionId, WORLD_ID, 2000.0);

        tryToBuyAndFailToPay(regionId, BUYER);

        List<FreeholdHistoryEntity> sales = salesRecorded(regionId);
        Assertions.assertEquals(1, sales.size());
        Assertions.assertEquals(ASKING_PRICE, sales.getFirst().price(), "the sale that was paid for");
    }

    @Test
    void undoingTheSameReservationTwiceTakesNothingMoreAway() {
        // The second has nothing of its own to undo. Removing "the latest" record would
        // have taken the record of a sale that was paid for.
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);
        BuyResult.Success failed = reserve(regionId, BUYER);
        logic.rollbackBuy(regionId, WORLD_ID, BUYER, failed);
        UUID someoneWithMoney = UUID.randomUUID();
        reserve(regionId, someoneWithMoney);

        logic.rollbackBuy(regionId, WORLD_ID, BUYER, failed);

        Assertions.assertEquals(1, salesRecorded(regionId).size());
        Assertions.assertEquals(someoneWithMoney, titleHolderOf(regionId));
        Assertions.assertEquals(List.of(), offersOn(regionId), "the sale that was paid for withdrew them");
    }

    @Test
    void undoingAReservationDoesNotUndoWhatSomebodyElseDidSince() {
        // An administrator gave the region to somebody else between the reservation and
        // the failed payment. Putting the old holder back would undo that, not this.
        String regionId = regionForSale();
        UUID givenTo = UUID.randomUUID();
        BuyResult.Success reserved = reserve(regionId, BUYER);
        logic.setTitleHolder(regionId, WORLD_ID, givenTo);

        logic.rollbackBuy(regionId, WORLD_ID, BUYER, reserved);

        Assertions.assertEquals(givenTo, titleHolderOf(regionId));
        Assertions.assertEquals(List.of(), salesRecorded(regionId), "and the buyer still did not buy it");
    }

    @Test
    void theRecordIsRemovedEvenIfTheRegionHasGone() {
        // History is kept by the region's name, so it outlives the region. A record left
        // here would be shown for the next region to be given that name.
        String regionId = regionForSale();
        BuyResult.Success reserved = reserve(regionId, BUYER);
        logic.deleteRegion(regionId, WORLD_ID);

        Assertions.assertDoesNotThrow(() -> logic.rollbackBuy(regionId, WORLD_ID, BUYER, reserved));

        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    @Test
    void aRecordWrittenInATransactionThatFailsIsNotKept() {
        // The record is written by a statement that answers with a row. Unless it is
        // declared to write, the session does not count it, and it is committed when the
        // session closes whatever happened after it.
        String regionId = regionForSale();

        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.freeholdHistoryMapper().insertReturningId(
                    regionId, WORLD_ID, HistoryEventType.BUY.name(), BUYER, AUTHORITY, ASKING_PRICE);
            // No commit: whatever was to follow failed.
        }

        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }

    // --- A purchase that was paid for ---

    @Test
    void theSaleIsRecordedWithTheTitleAndNotAfterIt() {
        // Written together, so that nothing has to happen between the payment and the
        // buyer being given the region, and nothing can fail there.
        String regionId = regionForSale();

        reserve(regionId, BUYER);

        Assertions.assertEquals(BUYER, titleHolderOf(regionId));
        List<FreeholdHistoryEntity> sales = salesRecorded(regionId);
        Assertions.assertEquals(1, sales.size());
        Assertions.assertEquals(BUYER, sales.getFirst().buyerId());
        Assertions.assertEquals(AUTHORITY, sales.getFirst().authorityId());
        Assertions.assertEquals(ASKING_PRICE, sales.getFirst().price());
        Assertions.assertEquals(ASKING_PRICE, logic.getRegionInfo(regionId, WORLD_ID).lastSoldPrice());
    }

    @Test
    void aPurchaseWithdrawsTheOffersAndAuctioneersItOvertakes() {
        // With the title, in the same transaction. Left for afterwards, the old owner's
        // auctioneer could accept an offer on a plot that had just been sold.
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);
        sanction(regionId, AUCTIONEER);

        reserve(regionId, BUYER);

        Assertions.assertEquals(List.of(), offersOn(regionId));
        Assertions.assertFalse(isSanctioned(regionId, AUCTIONEER));
    }

    @Test
    void theReservationSaysWhatItTookSoThatItCanBePutBack() {
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);
        sanction(regionId, AUCTIONEER);

        BuyResult.Success reserved = reserve(regionId, BUYER);

        Assertions.assertEquals(1, reserved.undo().offers().size());
        Assertions.assertEquals(OFFERER, reserved.undo().offers().getFirst().offererId());
        Assertions.assertEquals(500.0, reserved.undo().offers().getFirst().offerPrice());
        Assertions.assertEquals(List.of(AUCTIONEER), reserved.undo().auctioneers());
        Assertions.assertEquals(salesRecorded(regionId).getFirst().historyId(), reserved.undo().historyId());
    }

    // --- A region somebody is already paying for ---

    @Test
    void aRegionWithAnAcceptedOfferBeingPaidForIsNotForSale() {
        // Accepting an offer does not clear the asking price. Selling the region at that
        // price took it from under an offerer who had already paid part of theirs.
        String regionId = regionForSale();
        placeOffer(regionId, OFFERER, 500.0);
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class,
                logic.acceptOffer(regionId, WORLD_ID, AUTHORITY, OFFERER));

        Assertions.assertInstanceOf(BuyResult.NotForFreehold.class,
                logic.executeBuy(regionId, WORLD_ID, BUYER));

        Assertions.assertEquals(TITLE_HOLDER, titleHolderOf(regionId));
        Assertions.assertEquals(List.of(), salesRecorded(regionId));
        Assertions.assertEquals(1, offersOn(regionId).size());
    }

    @Test
    void aRegionWithAWinningBidBeingPaidForIsNotForSale() {
        String regionId = regionForSale();
        logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
        logic.performBid(regionId, WORLD_ID, OFFERER, 200.0);
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractBidPaymentMapper().insertPayment(
                    regionId, WORLD_ID, OFFERER, 0, LocalDateTime.now().plusDays(1));
            session.commit();
        }

        Assertions.assertInstanceOf(BuyResult.NotForFreehold.class,
                logic.executeBuy(regionId, WORLD_ID, BUYER));

        Assertions.assertEquals(TITLE_HOLDER, titleHolderOf(regionId));
        Assertions.assertEquals(List.of(), salesRecorded(regionId));
    }
}
