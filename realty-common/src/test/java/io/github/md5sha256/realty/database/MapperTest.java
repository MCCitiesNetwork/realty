package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseView;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractBid;
import io.github.md5sha256.realty.database.entity.SaleContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.database.entity.SaleContractOfferEntity;
import io.github.md5sha256.realty.database.entity.SaleContractOfferPaymentEntity;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class MapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID PLAYER_C = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "mapper_region_" + REGION_COUNTER.incrementAndGet();
    }

    private static void createSaleRegion(String regionId, UUID authority, UUID titleHolder) {
        boolean created = logic.createSale(regionId, WORLD_ID, 1000.0, authority, titleHolder);
        Assertions.assertTrue(created);
    }

    private static void createLeaseRegion(String regionId, UUID landlord) {
        boolean created = logic.createRental(regionId, WORLD_ID, 200.0, 86400, 5, landlord);
        Assertions.assertTrue(created);
    }

    // ==================== RealtyRegionMapper ====================

    @Nested
    @DisplayName("RealtyRegionMapper")
    class RealtyRegionMapperTests {

        @Test
        @DisplayName("registerWorldGuardRegion inserts and returns region id")
        void register() {
            String regionId = uniqueRegionId();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int id = wrapper.realtyRegionMapper().registerWorldGuardRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertTrue(id > 0);
            }
        }

        @Test
        @DisplayName("selectByWorldGuardRegion returns inserted region")
        void selectByWg() {
            String regionId = uniqueRegionId();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.realtyRegionMapper().registerWorldGuardRegion(regionId, WORLD_ID);
                session.commit();

                RealtyRegionEntity entity = wrapper.realtyRegionMapper()
                        .selectByWorldGuardRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(regionId, entity.worldGuardRegionId());
                Assertions.assertEquals(WORLD_ID, entity.worldId());
            }
        }

        @Test
        @DisplayName("selectByWorldGuardRegion returns null for nonexistent")
        void selectByWgNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                RealtyRegionEntity entity = wrapper.realtyRegionMapper()
                        .selectByWorldGuardRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("selectById returns inserted region")
        void selectById() {
            String regionId = uniqueRegionId();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int id = wrapper.realtyRegionMapper().registerWorldGuardRegion(regionId, WORLD_ID);
                session.commit();

                RealtyRegionEntity entity = wrapper.realtyRegionMapper().selectById(id);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(regionId, entity.worldGuardRegionId());
            }
        }

        @Test
        @DisplayName("deleteByWorldGuardRegion returns 1 for existing region")
        void deleteByWg() {
            String regionId = uniqueRegionId();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.realtyRegionMapper().registerWorldGuardRegion(regionId, WORLD_ID);
                session.commit();

                int deleted = wrapper.realtyRegionMapper().deleteByWorldGuardRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(1, deleted);

                RealtyRegionEntity entity = wrapper.realtyRegionMapper()
                        .selectByWorldGuardRegion(regionId, WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("deleteByWorldGuardRegion returns 0 for nonexistent")
        void deleteByWgNonexistent() {
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int deleted = wrapper.realtyRegionMapper().deleteByWorldGuardRegion("nonexistent", WORLD_ID);
                session.commit();
                Assertions.assertEquals(0, deleted);
            }
        }

        @Test
        @DisplayName("deleteByRealtyRegionId returns 1 for existing region")
        void deleteById() {
            String regionId = uniqueRegionId();
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int id = wrapper.realtyRegionMapper().registerWorldGuardRegion(regionId, WORLD_ID);
                session.commit();

                int deleted = wrapper.realtyRegionMapper().deleteByRealtyRegionId(id);
                session.commit();
                Assertions.assertEquals(1, deleted);
            }
        }

        @Test
        @DisplayName("selectRegionsByTitleHolder returns owned regions")
        void selectByTitleHolder() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<RealtyRegionEntity> regions = wrapper.realtyRegionMapper()
                        .selectRegionsByTitleHolder(PLAYER_A, 10, 0);
                Assertions.assertFalse(regions.isEmpty());
            }
        }

        @Test
        @DisplayName("selectRegionsByAuthority returns authority regions")
        void selectByAuthority() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<RealtyRegionEntity> regions = wrapper.realtyRegionMapper()
                        .selectRegionsByAuthority(AUTHORITY, 10, 0);
                Assertions.assertFalse(regions.isEmpty());
            }
        }

        @Test
        @DisplayName("selectRegionsByTenant returns rented regions")
        void selectByTenant() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<RealtyRegionEntity> regions = wrapper.realtyRegionMapper()
                        .selectRegionsByTenant(PLAYER_A, 10, 0);
                Assertions.assertFalse(regions.isEmpty());
            }
        }

        @Test
        @DisplayName("countRegionsByTitleHolder returns correct count")
        void countByTitleHolder() {
            createSaleRegion(uniqueRegionId(), AUTHORITY, PLAYER_A);
            createSaleRegion(uniqueRegionId(), AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                int count = wrapper.realtyRegionMapper().countRegionsByTitleHolder(PLAYER_A);
                Assertions.assertEquals(2, count);
            }
        }

        @Test
        @DisplayName("countRegionsByAuthority returns correct count")
        void countByAuthority() {
            createSaleRegion(uniqueRegionId(), AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                int count = wrapper.realtyRegionMapper().countRegionsByAuthority(AUTHORITY);
                Assertions.assertTrue(count >= 1);
            }
        }

        @Test
        @DisplayName("countRegionsByTenant returns correct count")
        void countByTenant() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_B);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                int count = wrapper.realtyRegionMapper().countRegionsByTenant(PLAYER_B);
                Assertions.assertTrue(count >= 1);
            }
        }
    }

    // ==================== SaleContractMapper ====================

    @Nested
    @DisplayName("SaleContractMapper")
    class SaleContractMapperTests {

        @Test
        @DisplayName("selectByRegion returns sale contract")
        void selectByRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractEntity entity = wrapper.saleContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(AUTHORITY, entity.authorityId());
                Assertions.assertEquals(PLAYER_A, entity.titleHolderId());
                Assertions.assertEquals(1000.0, entity.price());
            }
        }

        @Test
        @DisplayName("selectByRegion returns null for nonexistent")
        void selectByRegionNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractEntity entity = wrapper.saleContractMapper()
                        .selectByRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("existsByRegionAndAuthority returns true for authority")
        void existsByAuthority() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertTrue(wrapper.saleContractMapper()
                        .existsByRegionAndAuthority(regionId, WORLD_ID, AUTHORITY));
            }
        }

        @Test
        @DisplayName("existsByRegionAndAuthority returns false for non-authority")
        void existsByAuthorityFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractMapper()
                        .existsByRegionAndAuthority(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("updatePriceByRegion sets new price")
        void updatePrice() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.saleContractMapper()
                        .updatePriceByRegion(regionId, WORLD_ID, 2000.0);
                session.commit();
                Assertions.assertEquals(1, updated);

                SaleContractEntity entity = wrapper.saleContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(2000.0, entity.price());
            }
        }

        @Test
        @DisplayName("updatePriceByRegion with null unsets price")
        void updatePriceNull() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractMapper().updatePriceByRegion(regionId, WORLD_ID, null);
                session.commit();

                SaleContractEntity entity = wrapper.saleContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNull(entity.price());
            }
        }

        @Test
        @DisplayName("updateSaleByRegion updates price and title holder")
        void updateSale() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.saleContractMapper()
                        .updateSaleByRegion(regionId, WORLD_ID, 3000.0, PLAYER_B);
                session.commit();
                Assertions.assertEquals(1, updated);

                SaleContractEntity entity = wrapper.saleContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(3000.0, entity.price());
                Assertions.assertEquals(PLAYER_B, entity.titleHolderId());
            }
        }

        @Test
        @DisplayName("updatePriceByRegion returns 0 for nonexistent region")
        void updatePriceNonexistent() {
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.saleContractMapper()
                        .updatePriceByRegion("nonexistent", WORLD_ID, 500.0);
                session.commit();
                Assertions.assertEquals(0, updated);
            }
        }
    }

    // ==================== LeaseContractMapper ====================

    @Nested
    @DisplayName("LeaseContractMapper")
    class LeaseContractMapperTests {

        @Test
        @DisplayName("selectByRegion returns lease contract")
        void selectByRegion() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(AUTHORITY, entity.landlordId());
                Assertions.assertEquals(200.0, entity.price());
                Assertions.assertEquals(86400, entity.durationSeconds());
            }
        }

        @Test
        @DisplayName("selectByRegion returns null for nonexistent")
        void selectByRegionNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("existsByRegionAndTenant returns true for tenant")
        void existsByTenant() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertTrue(wrapper.leaseContractMapper()
                        .existsByRegionAndTenant(regionId, WORLD_ID, PLAYER_A));
            }
        }

        @Test
        @DisplayName("existsByRegionAndTenant returns false for non-tenant")
        void existsByTenantFalse() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.leaseContractMapper()
                        .existsByRegionAndTenant(regionId, WORLD_ID, PLAYER_A));
            }
        }

        @Test
        @DisplayName("rentRegion sets tenant and returns 1")
        void rentRegion() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .rentRegion(regionId, WORLD_ID, PLAYER_A);
                session.commit();
                Assertions.assertEquals(1, updated);

                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(PLAYER_A, entity.tenantId());
            }
        }

        @Test
        @DisplayName("rentRegion returns 0 when tenant already set")
        void rentRegionAlreadyOccupied() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .rentRegion(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(0, updated);
            }
        }

        @Test
        @DisplayName("renewLease increments extension count")
        void renewLease() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .renewLease(regionId, WORLD_ID, PLAYER_A);
                session.commit();
                Assertions.assertEquals(1, updated);

                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(1, entity.currentMaxExtensions());
            }
        }

        @Test
        @DisplayName("renewLease returns 0 for wrong tenant")
        void renewLeaseWrongTenant() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .renewLease(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(0, updated);
            }
        }

        @Test
        @DisplayName("clearTenant sets tenant to null")
        void clearTenant() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                LeaseContractEntity lease = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                int updated = wrapper.leaseContractMapper().clearTenant(lease.leaseContractId());
                session.commit();
                Assertions.assertEquals(1, updated);

                LeaseContractEntity after = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNull(after.tenantId());
            }
        }

        @Test
        @DisplayName("selectExpiredLeases returns expired leases with tenants")
        void selectExpiredLeases() throws InterruptedException {
            String regionId = uniqueRegionId();
            logic.createRental(regionId, WORLD_ID, 100.0, 1, -1, AUTHORITY);
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            Thread.sleep(2500);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<ExpiredLeaseView> expired = wrapper.leaseContractMapper().selectExpiredLeases();
                Assertions.assertFalse(expired.isEmpty());
                boolean found = expired.stream()
                        .anyMatch(e -> e.worldGuardRegionId().equals(regionId));
                Assertions.assertTrue(found);
            }
        }

        @Test
        @DisplayName("updateDurationByRegion updates duration")
        void updateDuration() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .updateDurationByRegion(regionId, WORLD_ID, 172800);
                session.commit();
                Assertions.assertEquals(1, updated);

                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(172800, entity.durationSeconds());
            }
        }

        @Test
        @DisplayName("updateDurationByRegion returns 0 for nonexistent")
        void updateDurationNonexistent() {
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .updateDurationByRegion("nonexistent", WORLD_ID, 172800);
                session.commit();
                Assertions.assertEquals(0, updated);
            }
        }

        @Test
        @DisplayName("updateLandlordByRegion updates landlord")
        void updateLandlord() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .updateLandlordByRegion(regionId, WORLD_ID, PLAYER_A);
                session.commit();
                Assertions.assertEquals(1, updated);

                LeaseContractEntity entity = wrapper.leaseContractMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(PLAYER_A, entity.landlordId());
            }
        }

        @Test
        @DisplayName("updateLandlordByRegion returns 0 for nonexistent")
        void updateLandlordNonexistent() {
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int updated = wrapper.leaseContractMapper()
                        .updateLandlordByRegion("nonexistent", WORLD_ID, PLAYER_A);
                session.commit();
                Assertions.assertEquals(0, updated);
            }
        }
    }

    // ==================== SaleContractAuctionMapper ====================

    @Nested
    @DisplayName("SaleContractAuctionMapper")
    class SaleContractAuctionMapperTests {

        @Test
        @DisplayName("createAuction inserts and selectActiveByRegion returns it")
        void createAndSelect() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int id = wrapper.saleContractAuctionMapper().createAuction(
                        regionId, WORLD_ID, AUTHORITY, LocalDateTime.now(),
                        3600, 3600, 100.0, 10.0);
                session.commit();
                Assertions.assertTrue(id > 0);

                SaleContractAuctionEntity entity = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(100.0, entity.minBid());
                Assertions.assertEquals(10.0, entity.minStep());
            }
        }

        @Test
        @DisplayName("selectActiveByRegion returns null for nonexistent")
        void selectActiveNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractAuctionEntity entity = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("existsByRegion returns true when auction exists")
        void existsTrue() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertTrue(wrapper.saleContractAuctionMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("existsByRegion returns false when no auction")
        void existsFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractAuctionMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("deleteActiveAuctionByRegion removes auction")
        void deleteActive() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int deleted = wrapper.saleContractAuctionMapper()
                        .deleteActiveAuctionByRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(1, deleted);

                Assertions.assertFalse(wrapper.saleContractAuctionMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("markEnded sets ended flag")
        void markEnded() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                SaleContractAuctionEntity auction = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                int updated = wrapper.saleContractAuctionMapper()
                        .markEnded(auction.saleContractAuctionId());
                session.commit();
                Assertions.assertEquals(1, updated);

                SaleContractAuctionEntity after = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                Assertions.assertNull(after);
            }
        }

        @Test
        @DisplayName("selectExpiredBiddingAuctions returns auctions past bidding deadline")
        void selectExpiredBidding() throws InterruptedException {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 1, 3600, 100.0, 10.0);

            Thread.sleep(1500);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<SaleContractAuctionEntity> expired = wrapper.saleContractAuctionMapper()
                        .selectExpiredBiddingAuctions();
                Assertions.assertNotNull(expired);
                Assertions.assertFalse(expired.isEmpty());
            }
        }

        @Test
        @DisplayName("selectById returns auction by primary key")
        void selectById() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractAuctionEntity active = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(active);

                SaleContractAuctionEntity byId = wrapper.saleContractAuctionMapper()
                        .selectById(active.saleContractAuctionId());
                Assertions.assertNotNull(byId);
                Assertions.assertEquals(active.saleContractAuctionId(), byId.saleContractAuctionId());
            }
        }

        @Test
        @DisplayName("deleteAuction removes by id")
        void deleteById() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                SaleContractAuctionEntity auction = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                int deleted = wrapper.saleContractAuctionMapper()
                        .deleteAuction(auction.saleContractAuctionId());
                session.commit();
                Assertions.assertEquals(1, deleted);
            }
        }
    }

    // ==================== SaleContractBidMapper ====================

    @Nested
    @DisplayName("SaleContractBidMapper")
    class SaleContractBidMapperTests {

        @Test
        @DisplayName("performContractBid inserts a bid")
        void performBid() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                SaleContractAuctionEntity auction = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                SaleContractBid bid = new SaleContractBid(
                        auction.saleContractAuctionId(), PLAYER_B, 150.0, LocalDateTime.now());
                int inserted = wrapper.saleContractBidMapper().performContractBid(bid);
                session.commit();
                Assertions.assertEquals(1, inserted);
            }
        }

        @Test
        @DisplayName("selectHighestBid returns highest bid")
        void selectHighest() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_C, 150.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractBid highest = wrapper.saleContractBidMapper()
                        .selectHighestBid(regionId, WORLD_ID);
                Assertions.assertNotNull(highest);
                Assertions.assertEquals(200.0, highest.bidAmount());
                Assertions.assertEquals(PLAYER_B, highest.bidderId());
            }
        }

        @Test
        @DisplayName("selectHighestBid returns null when no bids")
        void selectHighestNull() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractBid highest = wrapper.saleContractBidMapper()
                        .selectHighestBid(regionId, WORLD_ID);
                Assertions.assertNull(highest);
            }
        }

        @Test
        @DisplayName("selectDistinctBidders returns all bidders")
        void selectDistinctBidders() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_C, 150.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<UUID> bidders = wrapper.saleContractBidMapper()
                        .selectDistinctBidders(regionId, WORLD_ID);
                Assertions.assertEquals(2, bidders.size());
                Assertions.assertTrue(bidders.contains(PLAYER_C));
                Assertions.assertTrue(bidders.contains(PLAYER_B));
            }
        }
    }

    // ==================== SaleContractOfferMapper ====================

    @Nested
    @DisplayName("SaleContractOfferMapper")
    class SaleContractOfferMapperTests {

        @Test
        @DisplayName("insertOffer inserts and selectByRegion returns it")
        void insertAndSelect() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.saleContractOfferMapper()
                        .insertOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
                session.commit();
                Assertions.assertEquals(1, inserted);

                List<SaleContractOfferEntity> offers = wrapper.saleContractOfferMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(offers);
                Assertions.assertEquals(1, offers.size());
                Assertions.assertEquals(PLAYER_B, offers.getFirst().offererId());
                Assertions.assertEquals(500.0, offers.getFirst().offerPrice());
            }
        }

        @Test
        @DisplayName("existsByOfferer returns true when offer exists")
        void existsTrue() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertTrue(wrapper.saleContractOfferMapper()
                        .existsByOfferer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("existsByOfferer returns false when no offer")
        void existsFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractOfferMapper()
                        .existsByOfferer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("selectByOfferer returns specific offer")
        void selectByOfferer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractOfferEntity offer = wrapper.saleContractOfferMapper()
                        .selectByOfferer(regionId, WORLD_ID, PLAYER_B);
                Assertions.assertNotNull(offer);
                Assertions.assertEquals(500.0, offer.offerPrice());
            }
        }

        @Test
        @DisplayName("deleteOfferByOfferer removes one offerer's offer")
        void deleteByOfferer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int deleted = wrapper.saleContractOfferMapper()
                        .deleteOfferByOfferer(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(1, deleted);

                Assertions.assertFalse(wrapper.saleContractOfferMapper()
                        .existsByOfferer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("deleteOffers removes all offers on region")
        void deleteAll() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            UUID playerC = UUID.randomUUID();
            logic.placeOffer(regionId, WORLD_ID, playerC, 600.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int deleted = wrapper.saleContractOfferMapper()
                        .deleteOffers(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(2, deleted);

                List<SaleContractOfferEntity> offers = wrapper.saleContractOfferMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertTrue(offers == null || offers.isEmpty());
            }
        }

        @Test
        @DisplayName("deleteOtherOffers keeps excluded offerer")
        void deleteOthers() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);
            UUID playerC = UUID.randomUUID();
            logic.placeOffer(regionId, WORLD_ID, playerC, 600.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int deleted = wrapper.saleContractOfferMapper()
                        .deleteOtherOffers(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(1, deleted);

                Assertions.assertTrue(wrapper.saleContractOfferMapper()
                        .existsByOfferer(regionId, WORLD_ID, PLAYER_B));
                Assertions.assertFalse(wrapper.saleContractOfferMapper()
                        .existsByOfferer(regionId, WORLD_ID, playerC));
            }
        }

        @Test
        @DisplayName("selectAllByOfferer returns outbound offers")
        void selectAllByOfferer() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<OutboundOfferView> offers = wrapper.saleContractOfferMapper()
                        .selectAllByOfferer(PLAYER_B);
                Assertions.assertFalse(offers.isEmpty());
                Assertions.assertEquals(regionId, offers.getFirst().worldGuardRegionId());
                Assertions.assertEquals(500.0, offers.getFirst().offerPrice());
            }
        }

        @Test
        @DisplayName("selectAllByAuthority returns inbound offers")
        void selectAllByAuthority() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                List<InboundOfferView> offers = wrapper.saleContractOfferMapper()
                        .selectAllByAuthority(AUTHORITY);
                Assertions.assertFalse(offers.isEmpty());
                Assertions.assertEquals(regionId, offers.getFirst().worldGuardRegionId());
                Assertions.assertEquals(PLAYER_B, offers.getFirst().offererId());
            }
        }
    }

    // ==================== SaleContractOfferPaymentMapper ====================

    @Nested
    @DisplayName("SaleContractOfferPaymentMapper")
    class SaleContractOfferPaymentMapperTests {

        @Test
        @DisplayName("insertPayment inserts and selectByRegion returns it")
        void insertAndSelect() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            LocalDateTime deadline = LocalDateTime.now().plusDays(1);
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, deadline);
                session.commit();
                Assertions.assertEquals(1, inserted);

                SaleContractOfferPaymentEntity entity = wrapper.saleContractOfferPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(PLAYER_B, entity.offererId());
            }
        }

        @Test
        @DisplayName("selectByRegion returns null when no payment")
        void selectByRegionNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractOfferPaymentEntity entity = wrapper.saleContractOfferPaymentMapper()
                        .selectByRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("existsByRegion returns true when payment exists")
        void existsTrue() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                Assertions.assertTrue(wrapper.saleContractOfferPaymentMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("existsByRegion returns false when no payment")
        void existsFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractOfferPaymentMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("updatePayment updates current payment amount")
        void updatePayment() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                int updated = wrapper.saleContractOfferPaymentMapper()
                        .updatePayment(regionId, WORLD_ID, PLAYER_B, 200.0);
                session.commit();
                Assertions.assertEquals(1, updated);

                SaleContractOfferPaymentEntity entity = wrapper.saleContractOfferPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(200.0, entity.currentPayment());
            }
        }

        @Test
        @DisplayName("deleteByRegion removes payment")
        void deleteByRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                int deleted = wrapper.saleContractOfferPaymentMapper()
                        .deleteByRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(1, deleted);

                Assertions.assertFalse(wrapper.saleContractOfferPaymentMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("deleteByOfferId removes payment by offer PK")
        void deleteByOfferId() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                SaleContractOfferPaymentEntity entity = wrapper.saleContractOfferPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                int deleted = wrapper.saleContractOfferPaymentMapper()
                        .deleteByOfferId(entity.offerId());
                session.commit();
                Assertions.assertEquals(1, deleted);
            }
        }

        @Test
        @DisplayName("selectAllExpired returns expired payments")
        void selectExpired() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.placeOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractOfferPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().minusDays(1));
                session.commit();

                List<SaleContractOfferPaymentEntity> expired = wrapper.saleContractOfferPaymentMapper()
                        .selectAllExpired();
                Assertions.assertFalse(expired.isEmpty());
            }
        }
    }

    // ==================== SaleContractBidPaymentMapper ====================

    @Nested
    @DisplayName("SaleContractBidPaymentMapper")
    class SaleContractBidPaymentMapperTests {

        @Test
        @DisplayName("insertPayment inserts and selectByRegion returns it")
        void insertAndSelect() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();
                Assertions.assertEquals(1, inserted);

                SaleContractBidPaymentEntity entity = wrapper.saleContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(PLAYER_B, entity.bidderId());
            }
        }

        @Test
        @DisplayName("selectByRegion returns null when no payment")
        void selectByRegionNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractBidPaymentEntity entity = wrapper.saleContractBidPaymentMapper()
                        .selectByRegion("nonexistent", WORLD_ID);
                Assertions.assertNull(entity);
            }
        }

        @Test
        @DisplayName("existsByRegion returns true when payment exists")
        void existsTrue() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                Assertions.assertTrue(wrapper.saleContractBidPaymentMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("existsByRegion returns false when no payment")
        void existsFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractBidPaymentMapper()
                        .existsByRegion(regionId, WORLD_ID));
            }
        }

        @Test
        @DisplayName("updatePayment updates current payment")
        void updatePayment() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                int updated = wrapper.saleContractBidPaymentMapper()
                        .updatePayment(regionId, WORLD_ID, PLAYER_B, 100.0);
                session.commit();
                Assertions.assertEquals(1, updated);

                SaleContractBidPaymentEntity entity = wrapper.saleContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertEquals(100.0, entity.currentPayment());
            }
        }

        @Test
        @DisplayName("deleteByRegion removes payment")
        void deleteByRegion() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                int deleted = wrapper.saleContractBidPaymentMapper()
                        .deleteByRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(1, deleted);
            }
        }

        @Test
        @DisplayName("deleteByBidId removes payment by bid PK")
        void deleteByBidId() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().plusDays(1));
                session.commit();

                SaleContractBidPaymentEntity entity = wrapper.saleContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                int deleted = wrapper.saleContractBidPaymentMapper()
                        .deleteByBidId(entity.bidId());
                session.commit();
                Assertions.assertEquals(1, deleted);
            }
        }

        @Test
        @DisplayName("selectAllExpired returns expired payments")
        void selectExpired() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractBidPaymentMapper()
                        .insertPayment(regionId, WORLD_ID, PLAYER_B, 0, LocalDateTime.now().minusDays(1));
                session.commit();

                List<SaleContractBidPaymentEntity> expired = wrapper.saleContractBidPaymentMapper()
                        .selectAllExpired();
                Assertions.assertFalse(expired.isEmpty());
            }
        }

        @Test
        @DisplayName("insertNextPayment inserts payment for next highest bidder")
        void insertNextPayment() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);
            logic.createAuction(regionId, WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_C, 150.0);
            logic.performBid(regionId, WORLD_ID, PLAYER_B, 200.0);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                SaleContractAuctionEntity auction = wrapper.saleContractAuctionMapper()
                        .selectActiveByRegion(regionId, WORLD_ID);
                int inserted = wrapper.saleContractBidPaymentMapper()
                        .insertNextPayment(auction.saleContractAuctionId(), PLAYER_B,
                                LocalDateTime.now().plusDays(1));
                session.commit();
                Assertions.assertEquals(1, inserted);

                SaleContractBidPaymentEntity entity = wrapper.saleContractBidPaymentMapper()
                        .selectByRegion(regionId, WORLD_ID);
                Assertions.assertNotNull(entity);
                Assertions.assertEquals(PLAYER_C, entity.bidderId());
                Assertions.assertEquals(150.0, entity.bidPrice());
            }
        }
    }

    // ==================== SaleContractSanctionedAuctioneerMapper ====================

    @Nested
    @DisplayName("SaleContractSanctionedAuctioneerMapper")
    class SanctionedAuctioneerMapperTests {

        @Test
        @DisplayName("insert and existsByRegionAndAuctioneer")
        void insertAndExists() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.saleContractSanctionedAuctioneerMapper()
                        .insert(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(1, inserted);

                Assertions.assertTrue(wrapper.saleContractSanctionedAuctioneerMapper()
                        .existsByRegionAndAuctioneer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("existsByRegionAndAuctioneer returns false when not inserted")
        void existsFalse() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession()) {
                Assertions.assertFalse(wrapper.saleContractSanctionedAuctioneerMapper()
                        .existsByRegionAndAuctioneer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("deleteByRegionAndAuctioneer removes one auctioneer")
        void deleteOne() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractSanctionedAuctioneerMapper()
                        .insert(regionId, WORLD_ID, PLAYER_B);
                session.commit();

                int deleted = wrapper.saleContractSanctionedAuctioneerMapper()
                        .deleteByRegionAndAuctioneer(regionId, WORLD_ID, PLAYER_B);
                session.commit();
                Assertions.assertEquals(1, deleted);

                Assertions.assertFalse(wrapper.saleContractSanctionedAuctioneerMapper()
                        .existsByRegionAndAuctioneer(regionId, WORLD_ID, PLAYER_B));
            }
        }

        @Test
        @DisplayName("deleteAllByRegion removes all auctioneers")
        void deleteAll() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleContractSanctionedAuctioneerMapper()
                        .insert(regionId, WORLD_ID, PLAYER_A);
                wrapper.saleContractSanctionedAuctioneerMapper()
                        .insert(regionId, WORLD_ID, PLAYER_B);
                session.commit();

                int deleted = wrapper.saleContractSanctionedAuctioneerMapper()
                        .deleteAllByRegion(regionId, WORLD_ID);
                session.commit();
                Assertions.assertEquals(2, deleted);
            }
        }
    }

    // ==================== SaleHistoryMapper ====================

    @Nested
    @DisplayName("SaleHistoryMapper")
    class SaleHistoryMapperTests {

        @Test
        @DisplayName("insert records sale history")
        void insert() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.saleHistoryMapper()
                        .insert(regionId, WORLD_ID, "BUY", PLAYER_B, AUTHORITY, 1000.0);
                session.commit();
                Assertions.assertEquals(1, inserted);
            }
        }

        @Test
        @DisplayName("selectLastSalePrice returns last sale price")
        void selectLastPrice() {
            String regionId = uniqueRegionId();
            createSaleRegion(regionId, AUTHORITY, PLAYER_A);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.saleHistoryMapper()
                        .insert(regionId, WORLD_ID, "BUY", PLAYER_B, AUTHORITY, 1000.0);
                wrapper.saleHistoryMapper()
                        .insert(regionId, WORLD_ID, "BUY", PLAYER_A, AUTHORITY, 2000.0);
                session.commit();

                Double lastPrice = wrapper.saleHistoryMapper()
                        .selectLastSalePrice(regionId, WORLD_ID);
                Assertions.assertNotNull(lastPrice);
                Assertions.assertEquals(2000.0, lastPrice);
            }
        }

        @Test
        @DisplayName("selectLastSalePrice returns null for no history")
        void selectLastPriceNull() {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                Double lastPrice = wrapper.saleHistoryMapper()
                        .selectLastSalePrice("nonexistent", WORLD_ID);
                Assertions.assertNull(lastPrice);
            }
        }
    }

    // ==================== LeaseHistoryMapper ====================

    @Nested
    @DisplayName("LeaseHistoryMapper")
    class LeaseHistoryMapperTests {

        @Test
        @DisplayName("insert records lease history")
        void insert() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.leaseHistoryMapper()
                        .insert(regionId, WORLD_ID, "RENT", PLAYER_A, AUTHORITY,
                                200.0, 86400L, 5);
                session.commit();
                Assertions.assertEquals(1, inserted);
            }
        }

        @Test
        @DisplayName("insert with null optional fields")
        void insertNulls() {
            String regionId = uniqueRegionId();
            createLeaseRegion(regionId, AUTHORITY);

            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                int inserted = wrapper.leaseHistoryMapper()
                        .insert(regionId, WORLD_ID, "LEASE_EXPIRY", PLAYER_A, AUTHORITY,
                                null, null, null);
                session.commit();
                Assertions.assertEquals(1, inserted);
            }
        }
    }
}
