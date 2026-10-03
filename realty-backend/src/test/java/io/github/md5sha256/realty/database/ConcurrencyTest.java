package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.BuyResult;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.RegionInfo;
import io.github.md5sha256.realty.api.RealtyBackend.PayBidResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.RenewLeaseholdResult;
import io.github.md5sha256.realty.api.RealtyBackend.RentResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

class ConcurrencyTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "conc_region_" + REGION_COUNTER.incrementAndGet();
    }

    private static void createFreeholdRegion(String regionId, UUID worldId, UUID authority, UUID titleHolder) {
        boolean created = logic.createFreehold(regionId, worldId, 1000.0, Party.personal(authority), titleHolder);
        Assertions.assertTrue(created, "Expected freehold region to be created");
    }

    private static void placeAndAcceptOffer(String regionId, UUID worldId, UUID offererId, double price) {
        OfferResult offerResult = logic.placeOffer(regionId, worldId, ActorContext.player(offererId, false), price, false);
        Assertions.assertInstanceOf(OfferResult.Success.class, offerResult);
        AcceptOfferResult acceptResult = logic.acceptOffer(regionId, worldId, ActorContext.player(AUTHORITY, false), offererId);
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class, acceptResult);
    }

    /**
     * Outcome of a single thread in a race: either a result value or a DB exception.
     * MariaDB may reject concurrent modifications via deadlock detection or
     * "record has changed since last read" errors — both are valid rejection
     * mechanisms alongside application-level result codes.
     */
    private sealed interface RaceOutcome<T> {
        record Value<T>(T result) implements RaceOutcome<T> {}
        record DbException<T>(Exception exception) implements RaceOutcome<T> {}
    }

    /**
     * Runs two operations concurrently using a barrier and returns both outcomes.
     * DB exceptions (deadlock, optimistic lock at DB level) are captured as
     * {@link RaceOutcome.DbException} rather than propagated as test failures.
     */
    private static <T> List<RaceOutcome<T>> racePair(RaceSupplier<T> op1, RaceSupplier<T> op2) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<RaceOutcome<T>> result1 = new AtomicReference<>();
        AtomicReference<RaceOutcome<T>> result2 = new AtomicReference<>();

        Thread t1 = Thread.ofVirtual().start(() -> {
            try {
                barrier.await();
                result1.set(new RaceOutcome.Value<>(op1.get()));
            } catch (Exception ex) {
                result1.set(new RaceOutcome.DbException<>(ex));
            }
        });
        Thread t2 = Thread.ofVirtual().start(() -> {
            try {
                barrier.await();
                result2.set(new RaceOutcome.Value<>(op2.get()));
            } catch (Exception ex) {
                result2.set(new RaceOutcome.DbException<>(ex));
            }
        });
        t1.join(10_000);
        t2.join(10_000);
        Assertions.assertNotNull(result1.get(), "Thread 1 did not complete in time");
        Assertions.assertNotNull(result2.get(), "Thread 2 did not complete in time");
        return List.of(result1.get(), result2.get());
    }

    @FunctionalInterface
    private interface RaceSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Counts how many outcomes returned a value matching the predicate.
     */
    private static <T> long countValues(List<RaceOutcome<T>> outcomes, Predicate<T> predicate) {
        return outcomes.stream()
                .filter(o -> o instanceof RaceOutcome.Value<T> v && predicate.test(v.result()))
                .count();
    }

    /**
     * Counts how many outcomes were DB-level rejections (deadlock, record changed, etc).
     */
    private static <T> long countDbExceptions(List<RaceOutcome<T>> outcomes) {
        return outcomes.stream()
                .filter(o -> o instanceof RaceOutcome.DbException<T>)
                .count();
    }

    // ═══════════════════════════════════════════════════
    // Concurrent Buy
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent executeBuy")
    class ConcurrentBuy {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent buyers succeeds")
        void onlyOneSucceeds() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            logic.setPrice(regionId, WORLD_ID, 1000.0);

            List<RaceOutcome<BuyResult>> outcomes = racePair(
                    () -> logic.executeBuy(regionId, WORLD_ID, ActorContext.player(PLAYER_A, false), false),
                    () -> logic.executeBuy(regionId, WORLD_ID, ActorContext.player(PLAYER_B, false), false)
            );

            long successes = countValues(outcomes, r -> r instanceof BuyResult.Success);
            long appRejections = countValues(outcomes, r ->
                    r instanceof BuyResult.UpdateFailed
                            || r instanceof BuyResult.NotForFreehold
                            || r instanceof BuyResult.IsTitleHolder);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one buyer should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one buyer should be rejected, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent Rent
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent rentRegion")
    class ConcurrentRent {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent renters succeeds")
        void onlyOneSucceeds() throws Exception {
            String regionId = uniqueRegionId();
            logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, Party.personal(AUTHORITY));

            List<RaceOutcome<RentResult>> outcomes = racePair(
                    () -> logic.rentRegion(regionId, WORLD_ID, PLAYER_A),
                    () -> logic.rentRegion(regionId, WORLD_ID, PLAYER_B)
            );

            long successes = countValues(outcomes, r -> r instanceof RentResult.Success);
            long appRejections = countValues(outcomes, r ->
                    r instanceof RentResult.AlreadyOccupied
                            || r instanceof RentResult.UpdateFailed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one renter should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one renter should be rejected, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent Extend (Renew Leasehold)
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent renewLeasehold")
    class ConcurrentExtend {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent extensions succeeds when one remains")
        void onlyOneSucceedsOnLastExtension() throws Exception {
            String regionId = uniqueRegionId();
            // maxRenewals=1 means only one extension possible
            logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 1, Party.personal(AUTHORITY));
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            List<RaceOutcome<RenewLeaseholdResult>> outcomes = racePair(
                    () -> logic.renewLeasehold(regionId, WORLD_ID, PLAYER_A),
                    () -> logic.renewLeasehold(regionId, WORLD_ID, PLAYER_A)
            );

            long successes = countValues(outcomes, r -> r instanceof RenewLeaseholdResult.Success);
            long appRejections = countValues(outcomes, r ->
                    r instanceof RenewLeaseholdResult.NoExtensionsRemaining
                            || r instanceof RenewLeaseholdResult.UpdateFailed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one extension should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one extension should fail, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent Unrent
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent unrentRegion")
    class ConcurrentUnrent {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent unrent calls succeeds")
        void onlyOneSucceeds() throws Exception {
            String regionId = uniqueRegionId();
            logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, Party.personal(AUTHORITY));
            logic.rentRegion(regionId, WORLD_ID, PLAYER_A);

            List<RaceOutcome<RealtyBackend.UnrentResult>> outcomes = racePair(
                    () -> logic.unrentRegion(regionId, WORLD_ID, PLAYER_A),
                    () -> logic.unrentRegion(regionId, WORLD_ID, PLAYER_A)
            );

            long successes = countValues(outcomes, r ->
                    r instanceof RealtyBackend.UnrentResult.Success);
            long appRejections = countValues(outcomes, r ->
                    r instanceof RealtyBackend.UnrentResult.UpdateFailed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one unrent should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one unrent should fail, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent PayOffer
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent payOffer")
    class ConcurrentPayOffer {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent full payments succeeds")
        void onlyOneFullPaymentSucceeds() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            List<RaceOutcome<PayOfferResult>> outcomes = racePair(
                    () -> logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0),
                    () -> logic.payOffer(regionId, WORLD_ID, PLAYER_B, 500.0)
            );

            long fullyPaid = countValues(outcomes, r -> r instanceof PayOfferResult.FullyPaid);
            long appRejections = countValues(outcomes, r ->
                    r instanceof PayOfferResult.NoPaymentRecord
                            || r instanceof PayOfferResult.ExceedsAmountOwed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, fullyPaid,
                    "Exactly one payment should be FullyPaid, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one payment should be rejected, got: " + outcomes);
        }

        @RepeatedTest(5)
        @DisplayName("concurrent partial payments do not exceed total owed")
        void partialPaymentsDoNotExceedTotal() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            placeAndAcceptOffer(regionId, WORLD_ID, PLAYER_B, 500.0);

            List<RaceOutcome<PayOfferResult>> outcomes = racePair(
                    () -> logic.payOffer(regionId, WORLD_ID, PLAYER_B, 300.0),
                    () -> logic.payOffer(regionId, WORLD_ID, PLAYER_B, 300.0)
            );

            long successes = countValues(outcomes, r ->
                    r instanceof PayOfferResult.Success
                            || r instanceof PayOfferResult.FullyPaid);
            long appRejections = countValues(outcomes, r ->
                    r instanceof PayOfferResult.NoPaymentRecord
                            || r instanceof PayOfferResult.ExceedsAmountOwed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one partial payment should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one partial payment should be rejected, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent PayBid
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent payBid")
    class ConcurrentPayBid {

        @RepeatedTest(5)
        @DisplayName("only one of two concurrent full payments succeeds")
        void onlyOneFullPaymentSucceeds() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionAndBidPayment(regionId, WORLD_ID, PLAYER_B, 500.0);

            List<RaceOutcome<PayBidResult>> outcomes = racePair(
                    () -> logic.payBid(regionId, WORLD_ID, PLAYER_B, 500.0),
                    () -> logic.payBid(regionId, WORLD_ID, PLAYER_B, 500.0)
            );

            long fullyPaid = countValues(outcomes, r -> r instanceof PayBidResult.FullyPaid);
            long appRejections = countValues(outcomes, r ->
                    r instanceof PayBidResult.NoPaymentRecord
                            || r instanceof PayBidResult.ExceedsAmountOwed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, fullyPaid,
                    "Exactly one payment should be FullyPaid, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one payment should be rejected, got: " + outcomes);
        }

        @RepeatedTest(5)
        @DisplayName("concurrent partial payments do not exceed total owed")
        void partialPaymentsDoNotExceedTotal() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);
            createAuctionAndBidPayment(regionId, WORLD_ID, PLAYER_B, 500.0);

            List<RaceOutcome<PayBidResult>> outcomes = racePair(
                    () -> logic.payBid(regionId, WORLD_ID, PLAYER_B, 300.0),
                    () -> logic.payBid(regionId, WORLD_ID, PLAYER_B, 300.0)
            );

            long successes = countValues(outcomes, r ->
                    r instanceof PayBidResult.Success
                            || r instanceof PayBidResult.FullyPaid);
            long appRejections = countValues(outcomes, r ->
                    r instanceof PayBidResult.NoPaymentRecord
                            || r instanceof PayBidResult.ExceedsAmountOwed);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertEquals(1, successes,
                    "Exactly one partial payment should succeed, got: " + outcomes);
            Assertions.assertEquals(1, appRejections + dbRejections,
                    "Exactly one partial payment should be rejected, got: " + outcomes);
        }
    }

    // ═══════════════════════════════════════════════════
    // Concurrent placeOffer vs createAuction
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("concurrent placeOffer vs createAuction")
    class OfferVersusAuction {

        /**
         * Offers and auctions are mutually exclusive. Both sides check the other under
         * the freehold row lock, so whichever commits first forces the other to refuse.
         * Without that lock both snapshot reads see an empty region and both commit.
         */
        @RepeatedTest(10)
        @DisplayName("a region never ends up with both an offer and an auction")
        void offerAndAuctionAreMutuallyExclusive() throws Exception {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId, WORLD_ID, AUTHORITY, PLAYER_A);

            List<RaceOutcome<Boolean>> outcomes = racePair(
                    () -> logic.placeOffer(regionId, WORLD_ID, ActorContext.player(PLAYER_B, false), 500.0, false)
                            instanceof OfferResult.Success,
                    () -> logic.createAuction(regionId, WORLD_ID, ActorContext.player(AUTHORITY, false), 3600, 3600, 100.0, 10.0)
                            instanceof CreateAuctionResult.Success);

            long succeeded = countValues(outcomes, Boolean::booleanValue);
            long dbRejections = countDbExceptions(outcomes);
            Assertions.assertTrue(succeeded + dbRejections <= 2,
                    "Both operations cannot succeed");

            // The invariant that actually matters: the committed state is exclusive.
            RegionInfo info = logic.getRegionInfo(regionId, WORLD_ID);
            boolean hasAuction = info.auction() != null;
            boolean hasOffer;
            try (SqlSessionWrapper wrapper = database.openSession()) {
                hasOffer = wrapper.freeholdContractOfferMapper().existsByRegion(regionId, WORLD_ID);
            }
            Assertions.assertFalse(hasOffer && hasAuction,
                    "Region ended up with both an offer and an auction");
            Assertions.assertEquals(1, succeeded + dbRejections,
                    "Exactly one side should have won (or been rejected by the DB)");
        }
    }

    // ═══════════════════════════════════════════════════
    // A new party named by many callers at once
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("first use of a new party")
    class NewParty {

        private static final int THREADS = 8;
        private static final int ROUNDS = 10;

        /**
         * Every caller creates its own region and names the same party that no row
         * exists for yet. The party's row must be created once and shared, and no
         * caller may fail because another created the row a moment earlier.
         */
        private static void nameOneNewPartyAtOnce(Party landlord, String countQuery) throws Exception {
            CyclicBarrier barrier = new CyclicBarrier(THREADS);
            ConcurrentLinkedQueue<Object> failures = new ConcurrentLinkedQueue<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String regionId = uniqueRegionId();
                threads.add(Thread.ofVirtual().start(() -> {
                    try {
                        barrier.await();
                        if (!logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, landlord)) {
                            failures.add("createLeasehold returned false for " + regionId);
                        }
                    } catch (Exception ex) {
                        failures.add(ex);
                    }
                }));
            }
            for (Thread thread : threads) {
                thread.join(10_000);
            }
            Assertions.assertEquals(List.of(), List.copyOf(failures));
            Assertions.assertEquals(1, queryInt(countQuery));
            Assertions.assertEquals(queryInt("SELECT COUNT(*) FROM Party"),
                    queryInt("SELECT (SELECT COUNT(*) FROM PersonalParty) + (SELECT COUNT(*) FROM AccountParty) + (SELECT COUNT(*) FROM GroupParty)"),
                    "no base row without its kind row survives a race");
            Assertions.assertEquals(THREADS, queryInt(
                    "SELECT COUNT(*) FROM LeaseholdContract WHERE landlordPartyId IN ("
                            + countQuery.replace("COUNT(*)", "partyId") + ")"));
        }

        @Test
        @DisplayName("many callers naming one new account all succeed and share one party")
        void manyCallersNamingOneNewAccount_allSucceedAndShareOneParty() throws Exception {
            for (int round = 0; round < ROUNDS; round++) {
                int accountId = 4200 + round;
                nameOneNewPartyAtOnce(Party.account(accountId, AccountKind.GOVERNMENT),
                        "SELECT COUNT(*) FROM AccountParty WHERE accountId = " + accountId);
            }
        }

        @Test
        @DisplayName("many callers naming one new player all succeed and share one party")
        void manyCallersNamingOneNewPlayer_allSucceedAndShareOneParty() throws Exception {
            for (int round = 0; round < ROUNDS; round++) {
                UUID player = UUID.randomUUID();
                nameOneNewPartyAtOnce(Party.personal(player), "SELECT COUNT(*) FROM PersonalParty WHERE playerUuid = '" + player + "'");
            }
        }

        /**
         * A history row names the landlord the lease already has, so writing it must
         * neither add a party row nor use up a party id.
         */
        @Test
        @DisplayName("history writes do not use up party ids")
        void historyWrites_doNotUseUpPartyIds() throws Exception {
            String regionId = uniqueRegionId();
            Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 25,
                    Party.personal(AUTHORITY)));
            int rowsBefore = queryInt("SELECT COUNT(*) FROM Party");
            long nextIdBefore = nextPartyId();

            Assertions.assertInstanceOf(RentResult.Success.class, logic.rentRegion(regionId, WORLD_ID, PLAYER_A));
            for (int i = 0; i < 20; i++) {
                Assertions.assertInstanceOf(RenewLeaseholdResult.Success.class,
                        logic.renewLeasehold(regionId, WORLD_ID, PLAYER_A));
            }

            Assertions.assertEquals(rowsBefore, queryInt("SELECT COUNT(*) FROM Party"));
            Assertions.assertEquals(nextIdBefore, nextPartyId(), "a history write used up a party id");
        }

        private static long nextPartyId() throws SQLException {
            return queryInt("""
                    SELECT AUTO_INCREMENT FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Party'
                    """);
        }
    }

    private static int queryInt(String sql) throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /**
     * Creates an auction, places a bid, and sets up a bid payment record
     * with a future deadline so the payment is not expired.
     */
    private static void createAuctionAndBidPayment(String regionId, UUID worldId,
                                                    UUID bidderId, double bidAmount) {
        logic.createAuction(regionId, worldId, ActorContext.player(AUTHORITY, false), 3600, 3600, 100.0, 10.0);
        logic.performBid(regionId, worldId, ActorContext.player(bidderId, false), bidAmount, false);
        // Replace the auction-generated payment deadline with a future one
        // since the auction bidding period may expire during test
        try (SqlSessionWrapper wrapper = database.openSession();
             org.apache.ibatis.session.SqlSession session = wrapper.session()) {
            // Delete existing payment and re-insert with a far-future deadline
            wrapper.freeholdContractBidPaymentMapper().deleteByRegion(regionId, worldId);
            wrapper.freeholdContractBidPaymentMapper().insertPayment(
                    regionId, worldId, bidderId, bidAmount,
                    java.time.LocalDateTime.now().plusDays(7));
            session.commit();
        }
    }
}
