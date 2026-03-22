package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.RealtyLogicImpl.CreateAuctionResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.AcceptOfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.BidResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.ExpiredBidPayment;
import io.github.md5sha256.realty.database.RealtyLogicImpl.ExpiredOfferPayment;
import io.github.md5sha256.realty.database.RealtyLogicImpl.ListResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.OfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.PayBidResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.PayOfferResult;
import io.github.md5sha256.realty.database.RealtyLogicImpl.RegionInfo;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class RealtyLogicImplTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID PLAYER_C = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "region_" + REGION_COUNTER.incrementAndGet();
    }

    private static void createFreeholdRegion(String regionId, UUID worldId, UUID authority, UUID titleHolder) {
        boolean created = logic.createFreehold(regionId, worldId, 1000.0, authority, titleHolder);
        Assertions.assertTrue(created, "Expected freehold region to be created");
    }

    private static void createAuctionOnRegion(String regionId, UUID worldId) {
        logic.createAuction(regionId, worldId, AUTHORITY, 3600, 3600, 100.0, 10.0);
    }

    private static void insertBidPaymentWithDeadline(String regionId, UUID worldId, UUID bidderId,
                                                      LocalDateTime deadline) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractBidPaymentMapper().insertPayment(regionId, worldId, bidderId, 0, deadline);
            session.commit();
        }
    }

    private static void applyPartialBidPayment(String regionId, UUID worldId, UUID bidderId, double amount) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractBidPaymentMapper().updatePayment(regionId, worldId, bidderId, amount);
            session.commit();
        }
    }

    private static void insertOfferPaymentWithDeadline(String regionId, UUID worldId, UUID offererId,
                                                        LocalDateTime deadline) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractOfferPaymentMapper().insertPayment(regionId, worldId, offererId, 0, deadline);
            session.commit();
        }
    }

    private static void applyPartialOfferPayment(String regionId, UUID worldId, UUID offererId, double amount) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.freeholdContractOfferPaymentMapper().updatePayment(regionId, worldId, offererId, amount);
            session.commit();
        }
    }

    private static void placeAndAcceptOffer(String regionId, UUID worldId, UUID offererId, double price) {
        OfferResult offerResult = logic.placeOffer(regionId, worldId, offererId, price);
        Assertions.assertInstanceOf(OfferResult.Success.class, offerResult);
        AcceptOfferResult acceptResult = logic.acceptOffer(regionId, worldId, offererId);
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class, acceptResult);
    }

    // --- CreateFreehold ---

    @Nested
    @DisplayName("createFreehold")
    class CreateFreehold {

        @Test
        @DisplayName("succeeds for a new region")
        void succeeds() {
            String regionId = uniqueRegionId();
            boolean result = logic.createFreehold(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);
            Assertions.assertTrue(result);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.freehold());
        }

        @Test
        @DisplayName("returns false for duplicate region")
        void duplicateRegion() {
            String regionId = uniqueRegionId();
            logic.createFreehold(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);

            boolean second = logic.createFreehold(regionId, WORLD_ID, 800.0, AUTHORITY, PLAYER_B);
            Assertions.assertFalse(second);
        }

        @Test
        @DisplayName("succeeds with null titleholder (for freehold)")
        void succeedsWithNullTitleHolder() {
            String regionId = uniqueRegionId();
            boolean result = logic.createFreehold(regionId, WORLD_ID, 500.0, AUTHORITY, null);
            Assertions.assertTrue(result);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.freehold());
            Assertions.assertNull(info.freehold().titleHolderId());
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
            logic.createFreehold(regionId, WORLD_ID, 500.0, AUTHORITY, PLAYER_A);

            boolean second = logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_B);
            Assertions.assertFalse(second);
        }

        @Test
        @DisplayName("succeeds with null tenant (for rent)")
        void succeedsWithNullTenant() {
            String regionId = uniqueRegionId();
            boolean result = logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);
            Assertions.assertTrue(result);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.lease());
            Assertions.assertNull(info.lease().tenantId());
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            int deleted = logic.deleteRegion(regionId, WORLD_ID);
            Assertions.assertEquals(1, deleted);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNull(info.freehold());
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
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
            Assertions.assertNull(info.freehold());
            Assertions.assertNull(info.lease());
            Assertions.assertNull(info.auction());
        }

        @Test
        @DisplayName("returns freehold when freehold exists")
        void withFreehold() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.freehold());
            Assertions.assertNull(info.lease());
        }

        @Test
        @DisplayName("returns lease when rental exists")
        void withLease() {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.lease());
            Assertions.assertNull(info.freehold());
        }
    }

    // --- CheckRegionAuthority ---

    @Nested
    @DisplayName("checkRegionAuthority")
    class CheckRegionAuthority {

        @Test
        @DisplayName("returns true when player is title holder of freehold")
        void isTitleHolder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            Assertions.assertTrue(logic.checkRegionAuthority(regionId, WORLD_ID, PLAYER_A));
        }

        @Test
        @DisplayName("returns false when player is authority but not title holder of freehold")
        void isAuthorityNotTitleHolder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            Assertions.assertFalse(logic.checkRegionAuthority(regionId, WORLD_ID, AUTHORITY));
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(1, result.ownedCount());
            Assertions.assertEquals(1, result.owned().size());
        }

        @Test
        @DisplayName("counts landlord region for authority")
        void landlordRegion() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, PLAYER_A, PLAYER_B);

            ListResult result = logic.listRegions(PLAYER_A, 10, 0);
            Assertions.assertEquals(1, result.landlordCount());
        }

        @Test
        @DisplayName("counts rented region for tenant")
        void rentedRegion() {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, PLAYER_A);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_B);

            ListResult result = logic.listRegions(PLAYER_B, 10, 0);
            Assertions.assertEquals(1, result.rentedCount());
        }

        @Test
        @DisplayName("pagination limits results")
        void pagination() {
            for (int i = 0; i < 3; i++) {
                createFreeholdRegion(uniqueRegionId(), WORLD_ID, AUTHORITY, PLAYER_A);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.auction());
        }

        @Test
        @DisplayName("cancelAuction returns 1 when active auction exists")
        void cancelExisting() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            RealtyLogicImpl.CancelAuctionResult result = logic.cancelAuction(regionId, WORLD_ID);
            Assertions.assertEquals(1, result.deleted());

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNull(info.auction());
        }

        @Test
        @DisplayName("cancelAuction returns 0 when no auction exists")
        void cancelNonExistent() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            RealtyLogicImpl.CancelAuctionResult result = logic.cancelAuction(regionId, WORLD_ID);
            Assertions.assertEquals(0, result.deleted());
        }

        @Test
        @DisplayName("titleHolder can create auction")
        void titleHolderCanCreate() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            CreateAuctionResult result = logic.createAuction(regionId, WORLD_ID, PLAYER_A, 3600, 3600, 100.0, 10.0);
            Assertions.assertInstanceOf(CreateAuctionResult.Success.class, result);
        }

        @Test
        @DisplayName("sanctioned auctioneer can create auction")
        void sanctionedAuctioneerCanCreate() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            UUID sanctioned = UUID.randomUUID();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.freeholdContractSanctionedAuctioneerMapper().insert(regionId, WORLD_ID, sanctioned);
                session.commit();
            }

            CreateAuctionResult result = logic.createAuction(regionId, WORLD_ID, sanctioned, 3600, 3600, 100.0, 10.0);
            Assertions.assertInstanceOf(CreateAuctionResult.Success.class, result);
        }

        @Test
        @DisplayName("non-sanctioned player cannot create auction")
        void nonSanctionedCannotCreate() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            UUID stranger = UUID.randomUUID();
            CreateAuctionResult result = logic.createAuction(regionId, WORLD_ID, stranger, 3600, 3600, 100.0, 10.0);
            Assertions.assertInstanceOf(CreateAuctionResult.NotSanctioned.class, result);
        }

        @Test
        @DisplayName("createAuction returns NoFreeholdContract when no freehold contract exists")
        void noFreeholdContract() {
            CreateAuctionResult result = logic.createAuction("nonexistent", WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            Assertions.assertInstanceOf(CreateAuctionResult.NoFreeholdContract.class, result);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID); // minBid = 100.0

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 50.0);
            Assertions.assertInstanceOf(BidResult.BidTooLowMinimum.class, result);
            Assertions.assertEquals(100.0, ((BidResult.BidTooLowMinimum) result).minBid());
        }

        @Test
        @DisplayName("returns Success for valid first bid")
        void successFirstBid() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 150.0);
            Assertions.assertInstanceOf(BidResult.Success.class, result);
        }

        @Test
        @DisplayName("returns BidTooLowCurrent when bid is below current highest")
        void belowCurrentHighest() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            logic.performBid(regionId, WORLD_ID, PLAYER_C, 200.0);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 150.0);
            Assertions.assertInstanceOf(BidResult.BidTooLowCurrent.class, result);
            Assertions.assertEquals(200.0, ((BidResult.BidTooLowCurrent) result).currentHighest());
        }

        @Test
        @DisplayName("returns Success when bid exceeds current highest")
        void aboveCurrentHighest() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            logic.performBid(regionId, WORLD_ID, PLAYER_C, 200.0);

            BidResult result = logic.performBid(regionId, WORLD_ID, PLAYER_B, 300.0);
            Assertions.assertInstanceOf(BidResult.Success.class, result);
        }
    }

    // --- Offer ---

    @Nested
    @DisplayName("Offer (place and withdraw)")
    class Offer {

        @Test
        @DisplayName("placeOffer returns NoFreeholdContract when no freehold contract exists")
        void noFreeholdContract() {
            OfferResult result = logic.placeOffer("nonexistent", WORLD_ID, PLAYER_A, 500.0);
            Assertions.assertInstanceOf(OfferResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("placeOffer succeeds for non-authority player")
        void success() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(OfferResult.Success.class, result);
        }

        @Test
        @DisplayName("placeOffer returns IsOwner when offerer is authority")
        void isAuthority() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, AUTHORITY, 500.0);
            Assertions.assertInstanceOf(OfferResult.IsOwner.class, result);
        }

        @Test
        @DisplayName("placeOffer returns AlreadyHasOffer when duplicate offer")
        void alreadyHasOffer() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 600.0);
            Assertions.assertInstanceOf(OfferResult.AlreadyHasOffer.class, result);
        }

        @Test
        @DisplayName("placeOffer returns AuctionExists when auction exists on region")
        void auctionExists() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);

            OfferResult result = logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(OfferResult.AuctionExists.class, result);
        }
    }

    // --- Withdraw Offer ---

    @Nested
    @DisplayName("withdrawOffer")
    class WithdrawOffer {

        @Test
        @DisplayName("returns Success when offer exists and is not accepted")
        void withdrawExisting() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.Success.class, result);
        }

        @Test
        @DisplayName("returns NoOffer when no offer exists")
        void withdrawNonExistent() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.NoOffer.class, result);
        }

        @Test
        @DisplayName("returns OfferAccepted when the offerer's offer has been accepted")
        void withdrawAccepted() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.OfferAccepted.class, result);
        }

        @Test
        @DisplayName("returns NoOffer when another offer on the region has been accepted")
        void withdrawAfterOtherOfferAccepted() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            UUID playerC = UUID.randomUUID();
            logic.placeOffer(regionId, WORLD_ID, playerC, 600.0);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, WORLD_ID, playerC);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.NoOffer.class, result);
        }
    }

    // --- Accept Offer ---

    @Nested
    @DisplayName("acceptOffer")
    class AcceptOffer {

        @Test
        @DisplayName("removes other offers on the region")
        void removesOtherOffers() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            UUID playerC = UUID.randomUUID();
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            logic.placeOffer(regionId, WORLD_ID, playerC, 600.0);

            logic.acceptOffer(regionId, WORLD_ID, PLAYER_B);

            RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, WORLD_ID, playerC);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.NoOffer.class, result);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 100.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("returns NoPaymentRecord when offerer does not match")
        void wrongOfferer() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            UUID otherPlayer = UUID.randomUUID();
            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, otherPlayer, 100.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("returns ExceedsAmountOwed when payment exceeds remaining")
        void exceedsAmountOwed() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 501.0);
            Assertions.assertInstanceOf(PayOfferResult.ExceedsAmountOwed.class, result);
            Assertions.assertEquals(500.0, ((PayOfferResult.ExceedsAmountOwed) result).amountOwed());
        }

        @Test
        @DisplayName("returns Success for partial payment")
        void partialPayment() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            Assertions.assertInstanceOf(PayOfferResult.FullyPaid.class, result);
        }

        @Test
        @DisplayName("returns FullyPaid after multiple partial payments")
        void fullyPaidAfterPartials() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
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
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            Assertions.assertNotNull(info.freehold());
            Assertions.assertEquals(PLAYER_B, info.freehold().titleHolderId());
        }

        @Test
        @DisplayName("full payment clears payment record")
        void clearsPaymentRecord() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 1.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("full payment removes accepted offer")
        void clearsAcceptedOffer() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            RealtyLogicImpl.WithdrawOfferResult withdrawn = logic.withdrawOffer(regionId, WORLD_ID, PLAYER_B);
            Assertions.assertInstanceOf(RealtyLogicImpl.WithdrawOfferResult.NoOffer.class, withdrawn);
        }
    }

    // --- Clear Expired Bid Payments ---

    @Nested
    @DisplayName("clearExpiredBidPayments")
    class ClearExpiredBidPayments {

        @Test
        @DisplayName("returns empty list when no payments exist")
        void noPayments() {
            List<ExpiredBidPayment> refunds = logic.clearExpiredBidPayments();
            Assertions.assertTrue(refunds.isEmpty());
        }

        @Test
        @DisplayName("returns empty list when no payments are expired")
        void noExpired() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().plusHours(1));

            List<ExpiredBidPayment> refunds = logic.clearExpiredBidPayments();
            Assertions.assertTrue(refunds.isEmpty());
        }

        @Test
        @DisplayName("clears expired payment and returns refund with zero amount when no partial payment")
        void expiredNoPartialPayment() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            List<ExpiredBidPayment> refunds = logic.clearExpiredBidPayments();
            Assertions.assertEquals(1, refunds.size());
            Assertions.assertEquals(PLAYER_B, refunds.getFirst().bidderId());
            Assertions.assertEquals(0.0, refunds.getFirst().refundAmount());
        }

        @Test
        @DisplayName("clears expired payment and returns partial payment as refund")
        void expiredWithPartialPayment() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().plusHours(1));
            applyPartialBidPayment(regionId, WORLD_ID, PLAYER_B, 75.0);

            // Now expire it by updating the deadline directly
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                FreeholdContractBidPaymentEntity payment = wrapper.freeholdContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(payment);
                wrapper.freeholdContractBidPaymentMapper().deleteByBidId(payment.bidId());
                wrapper.freeholdContractBidPaymentMapper().insertPayment(regionId, WORLD_ID, PLAYER_B, 0,
                        LocalDateTime.now().minusDays(1));
                wrapper.freeholdContractBidPaymentMapper().updatePayment(regionId, WORLD_ID, PLAYER_B, 75.0);
                session.commit();
            }

            List<ExpiredBidPayment> refunds = logic.clearExpiredBidPayments();
            Assertions.assertEquals(1, refunds.size());
            Assertions.assertEquals(PLAYER_B, refunds.getFirst().bidderId());
            Assertions.assertEquals(75.0, refunds.getFirst().refundAmount());
        }

        @Test
        @DisplayName("expired payment record is deleted")
        void paymentRecordDeleted() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            logic.clearExpiredBidPayments();

            PayBidResult result = logic.payBid(regionId, WORLD_ID, PLAYER_B, 1.0);
            Assertions.assertInstanceOf(PayBidResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("promotes next highest bidder after expiry")
        void promotesNextBidder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_C, 150.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            // PLAYER_B is highest bidder — create an expired payment for them
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            logic.clearExpiredBidPayments();

            // PLAYER_C should now have a payment record as the next highest bidder
            try (SqlSessionWrapper wrapper = database.openSession()) {
                FreeholdContractBidPaymentEntity nextPayment = wrapper.freeholdContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(nextPayment, "Next highest bidder should have a payment record");
                Assertions.assertEquals(PLAYER_C, nextPayment.bidderId());
                Assertions.assertEquals(150.0, nextPayment.bidPrice());
                Assertions.assertEquals(0.0, nextPayment.currentPayment());
                Assertions.assertTrue(nextPayment.paymentDeadline().isAfter(LocalDateTime.now()));
            }
        }

        @Test
        @DisplayName("no promotion when only one bidder exists")
        void noPromotionSingleBidder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId, WORLD_ID);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);
            insertBidPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            logic.clearExpiredBidPayments();

            try (SqlSessionWrapper wrapper = database.openSession()) {
                FreeholdContractBidPaymentEntity nextPayment = wrapper.freeholdContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNull(nextPayment, "No payment should exist when there is no next bidder");
            }
        }

        @Test
        @DisplayName("handles multiple expired payments across different regions")
        void multipleExpired() {
            String regionId1 = uniqueRegionId();
            String regionId2 = uniqueRegionId();
            createFreeholdRegion(regionId1, WORLD_ID, AUTHORITY, PLAYER_A);
            createFreeholdRegion(regionId2, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionOnRegion(regionId1, WORLD_ID);
            createAuctionOnRegion(regionId2, WORLD_ID);
            logic.performBid(regionId1, WORLD_ID, PLAYER_C, 150.0);
            logic.performBid(regionId2, WORLD_ID, PLAYER_B, 250.0);
            insertBidPaymentWithDeadline(regionId1, WORLD_ID, PLAYER_C, LocalDateTime.now().minusDays(1));
            insertBidPaymentWithDeadline(regionId2, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            List<ExpiredBidPayment> refunds = logic.clearExpiredBidPayments();
            Assertions.assertEquals(2, refunds.size());
        }
    }

    // --- Clear Expired Offer Payments ---

    @Nested
    @DisplayName("clearExpiredOfferPayments")
    class ClearExpiredOfferPayments {

        @Test
        @DisplayName("returns empty list when no payments exist")
        void noPayments() {
            List<ExpiredOfferPayment> refunds = logic.clearExpiredOfferPayments();
            Assertions.assertTrue(refunds.isEmpty());
        }

        @Test
        @DisplayName("returns empty list when no payments are expired")
        void noExpired() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            insertOfferPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().plusHours(1));

            List<ExpiredOfferPayment> refunds = logic.clearExpiredOfferPayments();
            Assertions.assertTrue(refunds.isEmpty());
        }

        @Test
        @DisplayName("clears expired payment and returns refund with zero amount when no partial payment")
        void expiredNoPartialPayment() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            insertOfferPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            List<ExpiredOfferPayment> refunds = logic.clearExpiredOfferPayments();
            Assertions.assertEquals(1, refunds.size());
            Assertions.assertEquals(PLAYER_B, refunds.getFirst().offererId());
            Assertions.assertEquals(0.0, refunds.getFirst().refundAmount());
        }

        @Test
        @DisplayName("clears expired payment and returns partial payment as refund")
        void expiredWithPartialPayment() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            insertOfferPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().plusHours(1));
            applyPartialOfferPayment(regionId, WORLD_ID, PLAYER_B, 150.0);

            // Expire it by re-inserting with past deadline
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                FreeholdContractOfferPaymentEntity payment = wrapper.freeholdContractOfferPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(payment);
                wrapper.freeholdContractOfferPaymentMapper().deleteByOfferId(payment.offerId());
                wrapper.freeholdContractOfferPaymentMapper().insertPayment(regionId, WORLD_ID, PLAYER_B, 0,
                        LocalDateTime.now().minusDays(1));
                wrapper.freeholdContractOfferPaymentMapper().updatePayment(regionId, WORLD_ID, PLAYER_B, 150.0);
                session.commit();
            }

            List<ExpiredOfferPayment> refunds = logic.clearExpiredOfferPayments();
            Assertions.assertEquals(1, refunds.size());
            Assertions.assertEquals(PLAYER_B, refunds.getFirst().offererId());
            Assertions.assertEquals(150.0, refunds.getFirst().refundAmount());
        }

        @Test
        @DisplayName("expired payment record is deleted")
        void paymentRecordDeleted() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            insertOfferPaymentWithDeadline(regionId, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            logic.clearExpiredOfferPayments();

            PayOfferResult result = logic.payOffer(regionId, WORLD_ID, PLAYER_B, 1.0);
            Assertions.assertInstanceOf(PayOfferResult.NoPaymentRecord.class, result);
        }

        @Test
        @DisplayName("handles multiple expired payments across different regions")
        void multipleExpired() {
            String regionId1 = uniqueRegionId();
            String regionId2 = uniqueRegionId();
            createFreeholdRegion(regionId1, WORLD_ID, AUTHORITY, PLAYER_A);
            createFreeholdRegion(regionId2, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId1, WORLD_ID, PLAYER_C, 400.0);
            logic.placeOffer(regionId2, WORLD_ID, PLAYER_B, 600.0);
            insertOfferPaymentWithDeadline(regionId1, WORLD_ID, PLAYER_C, LocalDateTime.now().minusDays(1));
            insertOfferPaymentWithDeadline(regionId2, WORLD_ID, PLAYER_B, LocalDateTime.now().minusDays(1));

            List<ExpiredOfferPayment> refunds = logic.clearExpiredOfferPayments();
            Assertions.assertEquals(2, refunds.size());
        }
    }
}
