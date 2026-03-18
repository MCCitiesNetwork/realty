package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.RealtyLogicImpl.AcceptOfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.BidResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.ListResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.OfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.PayOfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.RegionInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class RealtyLogicImplTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "region_" + REGION_COUNTER.incrementAndGet();
    }

    private static void createSaleRegion(String regionId, UUID worldId, UUID authority, UUID titleHolder) {
        boolean created = logic.createSale(regionId, worldId, 1000.0, authority, titleHolder);
        Assertions.assertTrue(created, "Expected sale region to be created");
    }

    private static void createAuctionOnRegion(String regionId, UUID worldId) {
        logic.createAuction(regionId, worldId, 3600, 3600, 100.0, 10.0);
    }

    private static void placeAndAcceptOffer(String regionId, UUID worldId, UUID offererId, double price) {
        OfferResult offerResult = logic.placeOffer(regionId, worldId, offererId, price);
        Assertions.assertInstanceOf(OfferResult.Success.class, offerResult);
        AcceptOfferResult acceptResult = logic.acceptOffer(regionId, worldId, offererId);
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class, acceptResult);
    }

    // --- CreateSale ---

    @Nested
    @DisplayName("createSale")
    class CreateSale {

        @Test
        @DisplayName("succeeds for a new region")
        void succeeds() {
            String regionId = uniqueRegionId();
            boolean result = logic.createSale(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);
            Assertions.assertTrue(result);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.sale());
        }

        @Test
        @DisplayName("returns false for duplicate region")
        void duplicateRegion() {
            String regionId = uniqueRegionId();
            logic.createSale(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);

            boolean second = logic.createSale(regionId, WORLD_ID, 800.0, AUTHORITY, PLAYER_B);
            Assertions.assertFalse(second);
        }
    }

    // --- CreateRental ---

    @Nested
    @DisplayName("createRental")
    class CreateRental {

        @Test
        @DisplayName("succeeds for a new region")
        void succeeds() {
            String regionId = uniqueRegionId();
            boolean result = logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);
            Assertions.assertTrue(result);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.lease());
        }

        @Test
        @DisplayName("returns false for duplicate region")
        void duplicateRegion() {
            String regionId = uniqueRegionId();
            logic.createSale(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);

            boolean second = logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_B);
            Assertions.assertFalse(second);
        }
    }

    // --- DeleteRegion ---

    @Nested
    @DisplayName("deleteRegion")
    class DeleteRegion {

        @Test
        @DisplayName("returns 1 when region exists")
        void existingRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            int deleted = logic.deleteRegion(regionId, WORLD_ID);
            Assertions.assertEquals(1, deleted);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNull(info.sale());
        }

        @Test
        @DisplayName("returns 0 when region does not exist")
        void nonExistentRegion() {
            int deleted = logic.deleteRegion("nonexistent", WORLD_ID);
            Assertions.assertEquals(0, deleted);
        }

        @Test
        @DisplayName("cascades to auctions")
        void cascadesAuctions() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            logic.deleteRegion(regionId, WORLD_ID);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNull(info.auction());
        }
    }

    // --- GetRegionInfo ---

    @Nested
    @DisplayName("getRegionInfo")
    class GetRegionInfo {

        @Test
        @DisplayName("returns all null for unregistered region")
        void noContracts() {
            RegionInfo info = logic.getRegionInfo("nonexistent", WORLD_ID);
            Assertions.assertNull(info.sale());
            Assertions.assertNull(info.lease());
            Assertions.assertNull(info.auction());
        }

        @Test
        @DisplayName("returns sale when sale exists")
        void withSale() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.sale());
            Assertions.assertNull(info.lease());
        }

        @Test
        @DisplayName("returns lease when rental exists")
        void withLease() {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.lease());
            Assertions.assertNull(info.sale());
        }
    }

    // --- CheckRegionAuthority ---

    @Nested
    @DisplayName("checkRegionAuthority")
    class CheckRegionAuthority {

        @Test
        @DisplayName("returns true when player is authority of sale")
        void isAuthority() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            Assertions.assertTrue(logic.checkRegionAuthority(regionId, WORLD_ID, AUTHORITY));
        }

        @Test
        @DisplayName("returns true when player is tenant of lease")
        void isTenant() {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);

            Assertions.assertTrue(logic.checkRegionAuthority(regionId, WORLD_ID, PLAYER_A));
        }

        @Test
        @DisplayName("returns false for unrelated player")
        void unrelatedPlayer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            Assertions.assertFalse(logic.checkRegionAuthority(regionId, WORLD_ID, PLAYER_B));
        }

        @Test
        @DisplayName("returns false for non-existent region")
        void noRegion() {
            Assertions.assertFalse(logic.checkRegionAuthority("nonexistent", WORLD_ID, PLAYER_A));
        }
    }

    // --- ListRegions ---

    @Nested
    @DisplayName("listRegions")
    class ListRegions {

        @Test
        @DisplayName("returns empty result when no regions exist")
        void empty() {
            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(0, result.totalCount());
            Assertions.assertTrue(result.owned().isEmpty());
            Assertions.assertTrue(result.landlord().isEmpty());
            Assertions.assertTrue(result.rented().isEmpty());
        }

        @Test
        @DisplayName("counts owned region for title holder")
        void ownedRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(1, result.ownedCount());
            Assertions.assertEquals(1, result.owned().size());
        }

        @Test
        @DisplayName("counts landlord region for authority")
        void landlordRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, PLAYER_A, PLAYER_B);

            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(1, result.landlordCount());
        }

        @Test
        @DisplayName("counts rented region for tenant")
        void rentedRegion() {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);

            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(1, result.rentedCount());
        }

        @Test
        @DisplayName("pagination limits results")
        void pagination() {
            for (int i = 0; i < 3; i++) {
                createSaleRegion(uniqueRegionId(), WORLD_ID, AUTHORITY, PLAYER_A);
            }

            ListResult page1 = logic.listRegions(PLAYER_A, 2, 0);
            Assertions.assertEquals(3, page1.ownedCount());
            Assertions.assertEquals(2, page1.owned().size());

            ListResult page2 = logic.listRegions(PLAYER_A, 2, 2);
            Assertions.assertEquals(1, page2.owned().size());
        }
    }

    // --- Auction ---

    @Nested
    @DisplayName("Auction (create and cancel)")
    class Auction {

        @Test
        @DisplayName("createAuction succeeds when region exists")
        void createSucceeds() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.auction());
        }

        @Test
        @DisplayName("cancelAuction returns 1 when active auction exists")
        void cancelExisting() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            int cancelled = logic.cancelAuction(regionId, WORLD_ID);
            Assertions.assertEquals(1, cancelled);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNull(info.auction());
        }

        @Test
        @DisplayName("cancelAuction returns 0 when no auction exists")
        void cancelNonExistent() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            int cancelled = logic.cancelAuction(regionId, WORLD_ID);
            Assertions.assertEquals(0, cancelled);
        }
    }

    // --- Bid ---

    @Nested
    @DisplayName("performBid")
    class Bid {

        @Test
        @DisplayName("returns NoAuction when no auction exists")
        void noAuction() {
            BidResult result = logic.performBid("nonexistent", WORLD_ID, PLAYER_A, 200.0);
            Assertions.assertInstanceOf(BidResult.NoAuction.class, result);
        }

        @Test
        @DisplayName("returns BidTooLowMinimum when bid is below minimum")
        void belowMinBid() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID); // minBid = 100.0

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 50.0);
            Assertions.assertInstanceOf(BidResult.BidTooLowMinimum.class, result);
            Assertions.assertEquals(100.0, ((BidResult.BidTooLowMinimum) result).minBid());
        }

        @Test
        @DisplayName("returns Success for valid first bid")
        void successFirstBid() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 150.0);
            Assertions.assertInstanceOf(BidResult.Success.class, result);
        }

        @Test
        @DisplayName("returns BidTooLowCurrent when bid is below current highest")
        void belowCurrentHighest() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            logic.performBid(regionId, WORLD_ID, PLAYER_A, 200.0);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 150.0);
            Assertions.assertInstanceOf(BidResult.BidTooLowCurrent.class, result);
            Assertions.assertEquals(200.0, ((BidResult.BidTooLowCurrent) result).currentHighest());
        }

        @Test
        @DisplayName("returns Success when bid exceeds current highest")
        void aboveCurrentHighest() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            logic.performBid(regionId, WORLD_ID, PLAYER_A, 200.0);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 300.0);
            Assertions.assertInstanceOf(BidResult.Success.class, result);
        }
    }

    // --- Offer ---

    @Nested
    @DisplayName("Offer (place and withdraw)")
    class Offer {

        @Test
        @DisplayName("placeOffer returns NoSaleContract when no sale exists")
        void noSaleContract() {
            OfferResult result = logic.placeOffer("nonexistent", WORLD_ID, PLAYER_A, 500.0);
            Assertions.assertInstanceOf(OfferResult.NoSaleContract.class, result);
        }

        @Test
        @DisplayName("placeOffer succeeds for non-authority player")
        void success() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(OfferResult.Success.class, result);
        }

        @Test
        @DisplayName("placeOffer returns IsAuthority when offerer is authority")
        void isAuthority() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, AUTHORITY, 500.0);
            Assertions.assertInstanceOf(OfferResult.IsAuthority.class, result);
        }

        @Test
        @DisplayName("placeOffer returns AlreadyHasOffer when duplicate offer")
        void alreadyHasOffer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 600.0);
            Assertions.assertInstanceOf(OfferResult.AlreadyHasOffer.class, result);
        }

        @Test
        @DisplayName("withdrawOffer returns 1 when offer exists")
        void withdrawExisting() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            int withdrawn = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertEquals(1, withdrawn);
        }

        @Test
        @DisplayName("placeOffer returns AuctionExists when auction exists on region")
        void auctionExists() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(OfferResult.AuctionExists.class, result);
        }

        @Test
        @DisplayName("withdrawOffer returns 0 when no offer exists")
        void withdrawNonExistent() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            int withdrawn = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertEquals(0, withdrawn);
        }
    }

    // --- Pay Offer ---

    @Nested
    @DisplayName("payOffer")
    class PayOffer {

        @Test
        @DisplayName("returns NoPaymentRecord when no accepted offer exists")
        void noPaymentRecord() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 100.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("returns NoPaymentRecord when offerer does not match")
        void wrongOfferer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            UUID otherPlayer = UUID.randomUUID();
            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, otherPlayer, 100.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("returns ExceedsAmountOwed when payment exceeds remaining")
        void exceedsAmountOwed() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 501.0);
            Assertions.assertInstanceOf(PayOfferResult.ExceedsAmountOwed.class, result);
            Assertions.assertEquals(500.0, ((PayOfferResult.ExceedsAmountOwed) result).amountOwed());
        }

        @Test
        @DisplayName("returns Success for partial payment")
        void partialPayment() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 200.0);
            Assertions.assertInstanceOf(PayOfferResult.Success.class, result);
            PayOfferResult.Success success = (PayOfferResult.Success) result;
            Assertions.assertEquals(200.0, success.newTotal());
            Assertions.assertEquals(300.0, success.remaining());
        }

        @Test
        @DisplayName("returns ExceedsAmountOwed after partial payment reduces remaining")
        void exceedsAfterPartialPayment() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 300.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 201.0);
            Assertions.assertInstanceOf(PayOfferResult.ExceedsAmountOwed.class, result);
            Assertions.assertEquals(200.0, ((PayOfferResult.ExceedsAmountOwed) result).amountOwed());
        }

        @Test
        @DisplayName("returns FullyPaid when exact amount is paid")
        void fullyPaidExact() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(PayOfferResult.FullyPaid.class, result);
        }

        @Test
        @DisplayName("returns FullyPaid after multiple partial payments")
        void fullyPaidAfterPartials() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult first = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 200.0);
            Assertions.assertInstanceOf(PayOfferResult.Success.class, first);

            PayOfferResult second = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 300.0);
            Assertions.assertInstanceOf(PayOfferResult.FullyPaid.class, second);
        }

        @Test
        @DisplayName("full payment transfers title holder to offerer")
        void transfersTitleHolder() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.sale());
            Assertions.assertEquals(PLAYER_B, info.sale().titleHolderId());
        }

        @Test
        @DisplayName("full payment clears payment record")
        void clearsPaymentRecord() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 1.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("full payment removes all offers on the region")
        void clearsOffers() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            UUID playerC = UUID.randomUUID();
            logic.placeOffer(regionId, WORLD_ID, playerC, 600.0);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            int withdrawn = logic.withdrawOffer(regionId, WORLD_ID, playerC);
            Assertions.assertEquals(0, withdrawn);
        }
    }
}
