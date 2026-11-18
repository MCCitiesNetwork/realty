package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.RenameRegionResult;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Renaming a region: the region row and every row keyed by its name move to the new name. */
class RegionRenameTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID OTHER_WORLD_ID = UUID.randomUUID();
    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID BIDDER = UUID.randomUUID();

    private static final ActorContext AS_LANDLORD = ActorContext.player(LANDLORD, false);
    private static final ActorContext AS_STRANGER = ActorContext.player(STRANGER, false);
    private static final ActorContext AS_STAFF = ActorContext.player(STRANGER, true);

    /** Name columns that keep the old name on purpose: each records the name at the time of a sale. */
    private static final Set<String> SNAPSHOT_COLUMNS = Set.of("SaleFee.regionName", "PendingRefund.regionName");

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String freshName() {
        return "region_rename_" + REGION_COUNTER.incrementAndGet();
    }

    private static String vacantLease() {
        String regionId = freshName();
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 3600, 5, Party.personal(LANDLORD)));
        return regionId;
    }

    private static String freehold() {
        String regionId = freshName();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, Party.personal(LANDLORD), LANDLORD));
        return regionId;
    }

    private static int landlordPartyId(SqlSessionWrapper wrapper) {
        return wrapper.partyMapper().findOrInsert(Party.personal(LANDLORD));
    }

    private static int realtyRegionId(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().selectByWorldGuardRegion(regionId, WORLD_ID).realtyRegionId();
        }
    }

    /** One history row of each kind for {@code regionId} in {@code worldId}. */
    private static void writeHistory(String regionId, UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            int landlord = landlordPartyId(wrapper);
            wrapper.freeholdHistoryMapper().insert(regionId, worldId, "BUY", TENANT, landlord, 1000.0);
            wrapper.leaseholdHistoryMapper().insert(regionId, worldId, "RENT", TENANT, landlord, 200.0, 3600L, 5);
            wrapper.agentHistoryMapper().insert(regionId, worldId, "AGENT_ADD", TENANT, LANDLORD);
        }
    }

    private static int historyRows(String regionId, UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdHistoryMapper().countHistory(regionId, worldId, null, null, null)
                    + wrapper.leaseholdHistoryMapper().countHistory(regionId, worldId, null, null, null)
                    + wrapper.agentHistoryMapper().countHistory(regionId, worldId, null, null, null);
        }
    }

    private static void tag(String tagId, String regionId) {
        tag(tagId, regionId, WORLD_ID);
    }

    private static void tag(String tagId, String regionId, UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.regionTagMapper().insert(tagId, regionId, worldId);
        }
    }

    private static List<String> tags(String regionId) {
        return tags(regionId, WORLD_ID);
    }

    private static List<String> tags(String regionId, UUID worldId) {
        return logic.getTagIdsByRegion(regionId, worldId).stream().sorted().toList();
    }

    private static SaleFeeEntity saleFeeLine(String regionId, int saleRef) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return new SaleFeeEntity(0, "bid", saleRef, realtyRegionId(regionId), regionId, WORLD_ID, TENANT,
                    "fee", "levy:stamp-duty", landlordPartyId(wrapper), FeeSide.BUYER, 5.0, 50.0,
                    "locked", null, 0, null, null);
        }
    }

    // --- Who may rename ---

    @Test
    void landlordManagerRenamesALeasehold() {
        String oldName = vacantLease();
        String newName = freshName();

        RenameRegionResult result = logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(new RenameRegionResult.Success(null), result);
        Assertions.assertNotNull(logic.getLeaseholdContract(newName, WORLD_ID));
        Assertions.assertNull(logic.getLeaseholdContract(oldName, WORLD_ID));
    }

    @Test
    void rentedLeaseholdReportsItsTenant() {
        String oldName = vacantLease();
        logic.rentRegion(oldName, WORLD_ID, TENANT);

        RenameRegionResult result = logic.renameRegion(oldName, freshName(), WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(new RenameRegionResult.Success(TENANT), result);
    }

    @Test
    void nonManagerIsRefused() {
        String oldName = vacantLease();

        RenameRegionResult result = logic.renameRegion(oldName, freshName(), WORLD_ID, AS_STRANGER);

        Assertions.assertInstanceOf(RenameRegionResult.NotAuthorized.class, result);
        Assertions.assertNotNull(logic.getLeaseholdContract(oldName, WORLD_ID));
    }

    @Test
    void freeholdNeedsTheBypass() {
        String oldName = freehold();
        String newName = freshName();

        Assertions.assertInstanceOf(RenameRegionResult.NotAuthorized.class,
                logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD));
        Assertions.assertInstanceOf(RenameRegionResult.Success.class,
                logic.renameRegion(oldName, newName, WORLD_ID, AS_STAFF));
        Assertions.assertNotNull(logic.getFreeholdContract(newName, WORLD_ID));
    }

    @Test
    void consoleRenamesAnyRegion() {
        String oldName = freehold();

        Assertions.assertInstanceOf(RenameRegionResult.Success.class,
                logic.renameRegion(oldName, freshName(), WORLD_ID, ActorContext.console()));
    }

    @Test
    void unknownRegionIsNoRegion() {
        Assertions.assertInstanceOf(RenameRegionResult.NoRegion.class,
                logic.renameRegion(freshName(), freshName(), WORLD_ID, AS_STAFF));
    }

    @Test
    void landlordChangedAfterTheReadRenamesNothing() {
        String oldName = vacantLease();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            int notTheLandlord = wrapper.partyMapper().findOrInsert(Party.personal(STRANGER));

            int renamed = wrapper.realtyRegionMapper().renameGuarded(oldName, freshName(), WORLD_ID, notTheLandlord);

            Assertions.assertEquals(0, renamed);
        }
    }

    // --- The new name ---

    @Test
    void takenNameInTheSameWorldIsRefused() {
        String oldName = vacantLease();
        String taken = vacantLease();

        RenameRegionResult result = logic.renameRegion(oldName, taken, WORLD_ID, AS_LANDLORD);

        Assertions.assertInstanceOf(RenameRegionResult.NameTaken.class, result);
        Assertions.assertNotNull(logic.getLeaseholdContract(oldName, WORLD_ID));
        Assertions.assertNotNull(logic.getLeaseholdContract(taken, WORLD_ID));
    }

    /** The rename reads this failure as "name taken" when another transaction takes the name first. */
    @Test
    void renamingOntoATakenNameFailsOnTheUniqueKey() {
        String oldName = vacantLease();
        String taken = vacantLease();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            PersistenceException failure = Assertions.assertThrows(PersistenceException.class,
                    () -> wrapper.realtyRegionMapper().renameGuarded(oldName, taken, WORLD_ID, null));

            Assertions.assertInstanceOf(SQLIntegrityConstraintViolationException.class, failure.getCause());
        }
    }

    @Test
    void sameNameInAnotherWorldIsAllowed() {
        String oldName = vacantLease();
        String newName = freshName();
        Assertions.assertTrue(logic.createLeasehold(newName, OTHER_WORLD_ID, 200.0, 3600, 5, Party.personal(LANDLORD)));

        Assertions.assertInstanceOf(RenameRegionResult.Success.class,
                logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD));
    }

    // --- What moves with the region ---

    @Test
    void historyMovesToTheNewName() {
        String oldName = vacantLease();
        String newName = freshName();
        writeHistory(oldName, WORLD_ID);

        logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(3, historyRows(newName, WORLD_ID));
        Assertions.assertEquals(0, historyRows(oldName, WORLD_ID));
    }

    @Test
    void historyOfTheSameNameInAnotherWorldStays() {
        String oldName = vacantLease();
        writeHistory(oldName, OTHER_WORLD_ID);

        logic.renameRegion(oldName, freshName(), WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(3, historyRows(oldName, OTHER_WORLD_ID));
    }

    @Test
    void tagsMoveToTheNewName() {
        String oldName = vacantLease();
        String newName = freshName();
        tag("commercial", oldName);
        tag("waterfront", oldName);

        logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(List.of("commercial", "waterfront"), tags(newName));
        Assertions.assertEquals(List.of(), tags(oldName));
    }

    @Test
    void tagsOfTheSameNameInAnotherWorldStay() {
        String oldName = vacantLease();
        String newName = freshName();
        tag("commercial", oldName);
        tag("island", oldName, OTHER_WORLD_ID);
        tag("harbour", newName, OTHER_WORLD_ID);

        logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(List.of("commercial"), tags(newName));
        Assertions.assertEquals(List.of("island"), tags(oldName, OTHER_WORLD_ID));
        Assertions.assertEquals(List.of("harbour"), tags(newName, OTHER_WORLD_ID));
    }

    /** No region has the new name, so a tag under it was left behind by a region delete. */
    @Test
    void tagsLeftUnderTheNewNameAreDropped() {
        String oldName = vacantLease();
        String newName = freshName();
        tag("shop", oldName);
        tag("shop", newName);
        tag("stale", newName);

        logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);

        Assertions.assertEquals(List.of("shop"), tags(newName));
        Assertions.assertEquals(List.of(), tags(oldName));
    }

    @Test
    void renamingBackRestoresExactlyTheTagsThatMoved() {
        String oldName = vacantLease();
        String newName = freshName();
        tag("commercial", oldName);
        tag("waterfront", oldName);
        tag("stale", newName);

        logic.renameRegion(oldName, newName, WORLD_ID, AS_LANDLORD);
        logic.renameRegion(newName, oldName, WORLD_ID, ActorContext.console());

        Assertions.assertEquals(List.of("commercial", "waterfront"), tags(oldName));
        Assertions.assertEquals(List.of(), tags(newName));
    }

    @Test
    @SuppressWarnings("deprecation")
    void theNameOnlyLookupStillAnswersAcrossWorlds() {
        String name = vacantLease();
        tag("commercial", name);
        tag("commercial", name, OTHER_WORLD_ID);
        tag("island", name, OTHER_WORLD_ID);

        Assertions.assertEquals(List.of("commercial", "island"),
                logic.getTagIdsByRegion(name).stream().sorted().toList());
        Assertions.assertEquals(List.of(name), logic.getRegionIdsByTag("commercial"));
    }

    @Test
    void signsSchematicAndFeesFollowTheRegion() {
        String oldName = freehold();
        String newName = freshName();
        int regionId = realtyRegionId(oldName);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.realtySignMapper().insert(WORLD_ID, 1, 64, 1, oldName, WORLD_ID);
            wrapper.realtySchematicMapper().upsert(oldName, WORLD_ID, new byte[]{1, 2, 3}, LocalDateTime.now());
            wrapper.regionFeeMapper().upsert(regionId, landlordPartyId(wrapper), FeeSide.SELLER, 3.0,
                    "titleholder", LANDLORD, true);
        }

        logic.renameRegion(oldName, newName, WORLD_ID, AS_STAFF);

        try (SqlSessionWrapper wrapper = database.openSession()) {
            Assertions.assertEquals(1, wrapper.realtySignMapper().selectByRegion(newName, WORLD_ID).size());
            Assertions.assertNotNull(wrapper.realtySchematicMapper().selectByWorldGuardRegion(newName, WORLD_ID));
            Assertions.assertEquals(1, wrapper.regionFeeMapper().selectByRegion(realtyRegionId(newName)).size());
        }
    }

    @Test
    void saleFeeKeepsTheOldName() {
        String oldName = freehold();
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.saleFeeMapper().insert(saleFeeLine(oldName, 31));
        }

        logic.renameRegion(oldName, freshName(), WORLD_ID, AS_STAFF);

        try (SqlSessionWrapper wrapper = database.openSession()) {
            Assertions.assertEquals(oldName, wrapper.saleFeeMapper().selectBySale("bid", 31).getFirst().regionName());
        }
    }

    /**
     * Guards against a name-keyed table the rename does not know about: after a rename, no column
     * that holds region names may still hold the old one, apart from the snapshots.
     */
    @Test
    void nothingKeepsTheOldName() throws SQLException {
        String oldName = freehold();
        int regionId = realtyRegionId(oldName);
        writeHistory(oldName, WORLD_ID);
        tag("commercial", oldName);
        tag("waterfront", oldName);
        try (SqlSessionWrapper wrapper = database.openSession(true)) {
            wrapper.realtySignMapper().insert(WORLD_ID, 2, 64, 2, oldName, WORLD_ID);
            wrapper.realtySchematicMapper().upsert(oldName, WORLD_ID, new byte[]{1}, LocalDateTime.now());
            wrapper.regionFeeMapper().upsert(regionId, landlordPartyId(wrapper), FeeSide.SELLER, 3.0,
                    "titleholder", LANDLORD, true);
            wrapper.saleFeeMapper().insert(saleFeeLine(oldName, 41));
        }
        openToOffers(oldName, WORLD_ID);
        logic.placeOffer(oldName, WORLD_ID, ActorContext.player(TENANT, false), 500.0, false);
        logic.createAuction(oldName, WORLD_ID, AS_LANDLORD, 3600, 3600, 100.0, 10.0);
        logic.performBid(oldName, WORLD_ID, ActorContext.player(BIDDER, false), 200.0, false);

        Assertions.assertInstanceOf(RenameRegionResult.Success.class,
                logic.renameRegion(oldName, freshName(), WORLD_ID, AS_STAFF));

        List<String> nameColumns = new ArrayList<>();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Connection connection = wrapper.session().getConnection();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT TABLE_NAME, COLUMN_NAME
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND COLUMN_NAME IN ('worldGuardRegionId', 'regionName')
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    nameColumns.add(rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME"));
                }
            }
            // A query that finds nothing would pass every table below.
            Assertions.assertTrue(nameColumns.containsAll(List.of(
                    "RealtyRegion.worldGuardRegionId", "FreeholdHistory.worldGuardRegionId",
                    "LeaseholdHistory.worldGuardRegionId", "AgentHistory.worldGuardRegionId",
                    "RegionTag.worldGuardRegionId", "SaleFee.regionName")), nameColumns.toString());
            for (String nameColumn : nameColumns) {
                if (SNAPSHOT_COLUMNS.contains(nameColumn)) {
                    continue;
                }
                String table = nameColumn.substring(0, nameColumn.indexOf('.'));
                String column = nameColumn.substring(nameColumn.indexOf('.') + 1);
                try (PreparedStatement count = connection.prepareStatement(
                        "SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` = ?")) {
                    count.setString(1, oldName);
                    try (ResultSet rs = count.executeQuery()) {
                        rs.next();
                        Assertions.assertEquals(0, rs.getInt(1), nameColumn + " still holds the old name");
                    }
                }
            }
        }
    }
}
