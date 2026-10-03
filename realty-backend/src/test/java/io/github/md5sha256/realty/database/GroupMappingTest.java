package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.MapGroupResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetLandlordResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetRentableResult;
import io.github.md5sha256.realty.api.RealtyBackend.UnmapGroupResult;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class GroupMappingTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();

    private static final Party.Account GOV_42 = new Party.Account(42, AccountKind.GOVERNMENT);
    private static final Party.Account BUSINESS_77 = new Party.Account(77, AccountKind.BUSINESS);
    private static final Party.Group POLICE_ON_42 = new Party.Group("police", 42, AccountKind.GOVERNMENT);
    private static final Party.Group POLICE_ON_77 = new Party.Group("police", 77, AccountKind.BUSINESS);

    private static void createLease(String regionId, Party landlord) {
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, landlord));
    }

    private static int landlordPartyIdOfTheOnlyLease() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT landlordPartyId FROM LeaseholdContract")) {
            Assertions.assertTrue(resultSet.next());
            int id = resultSet.getInt(1);
            Assertions.assertFalse(resultSet.next());
            return id;
        }
    }

    private static int groupRowCount() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM Party WHERE kind = 'GROUP'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    @Test
    void map_createsTheGroupParty() {
        Assertions.assertEquals(new MapGroupResult.Created(POLICE_ON_42), logic.mapGroup("police", GOV_42));

        Assertions.assertEquals(POLICE_ON_42, logic.findGroupParty("police"));
    }

    @Test
    void map_storesTheNameInLowerCase() {
        Assertions.assertEquals(new MapGroupResult.Created(POLICE_ON_42), logic.mapGroup("Police", GOV_42));

        Assertions.assertEquals(new MapGroupResult.NoChange(POLICE_ON_42), logic.mapGroup("police", GOV_42));
    }

    @Test
    void map_nameTooLongForTheColumn_failsAndStoresNothing() throws SQLException {
        String longName = "g".repeat(65);

        Assertions.assertThrows(RuntimeException.class, () -> logic.mapGroup(longName, GOV_42));
        Assertions.assertEquals(0, groupRowCount());
    }

    @Test
    void map_again_withTheSameAccount_isNoChange() throws SQLException {
        logic.mapGroup("police", GOV_42);

        Assertions.assertEquals(new MapGroupResult.NoChange(POLICE_ON_42), logic.mapGroup("police", GOV_42));
        Assertions.assertEquals(1, groupRowCount());
    }

    @Test
    void map_toAnotherAccount_keepsThePartyId() throws SQLException {
        logic.mapGroup("police", GOV_42);
        String regionId = "police_station";
        createLease(regionId, POLICE_ON_42);
        int partyIdBefore = landlordPartyIdOfTheOnlyLease();

        Assertions.assertEquals(new MapGroupResult.Changed(POLICE_ON_42, POLICE_ON_77),
                logic.mapGroup("police", BUSINESS_77));

        Assertions.assertEquals(POLICE_ON_77, logic.getLeaseholdContract(regionId, WORLD_ID).landlord());
        Assertions.assertEquals(partyIdBefore, landlordPartyIdOfTheOnlyLease());
        Assertions.assertEquals(1, groupRowCount());
    }

    @Test
    void afterRemapping_membersStillManageTheLease() {
        logic.mapGroup("police", GOV_42);
        String regionId = "police_station";
        createLease(regionId, POLICE_ON_42);
        logic.mapGroup("police", BUSINESS_77);

        // The plugin builds the context from the group as it reads it now.
        Party.Group asReadNow = logic.findGroupParty("police");
        Assertions.assertNotNull(asReadNow);
        ActorContext member = new ActorContext(PLAYER, Set.of(asReadNow), Set.of(), false);

        Assertions.assertEquals(new SetRentableResult.Success(false),
                logic.setRentable(regionId, WORLD_ID, member, false));
    }

    @Test
    void unmap_unusedGroup_deletesTheRow() throws SQLException {
        logic.mapGroup("police", GOV_42);

        Assertions.assertEquals(new UnmapGroupResult.Success(POLICE_ON_42), logic.unmapGroup("Police"));

        Assertions.assertNull(logic.findGroupParty("police"));
        Assertions.assertEquals(0, groupRowCount());
    }

    @Test
    void unmap_whileAContractUsesIt_isRefused() {
        logic.mapGroup("police", GOV_42);
        String regionId = "police_station";
        createLease(regionId, POLICE_ON_42);
        // Renting writes a history row that names the landlord.
        logic.rentRegion(regionId, WORLD_ID, PLAYER);

        Assertions.assertEquals(new UnmapGroupResult.StillInUse(1, 1), logic.unmapGroup("police"));
        Assertions.assertEquals(POLICE_ON_42, logic.findGroupParty("police"));
    }

    @Test
    void unmap_whileOnlyHistoryUsesIt_isRefused() {
        logic.mapGroup("police", GOV_42);
        String regionId = "police_station";
        createLease(regionId, new Party.Personal(PLAYER));
        ActorContext console = ActorContext.console();
        Assertions.assertInstanceOf(SetLandlordResult.Success.class,
                logic.setLandlord(regionId, WORLD_ID, POLICE_ON_42, console));
        Assertions.assertInstanceOf(SetLandlordResult.Success.class,
                logic.setLandlord(regionId, WORLD_ID, new Party.Personal(PLAYER), console));

        UnmapGroupResult result = logic.unmapGroup("police");

        UnmapGroupResult.StillInUse stillInUse = Assertions.assertInstanceOf(UnmapGroupResult.StillInUse.class, result);
        Assertions.assertEquals(0, stillInUse.contractCount());
        Assertions.assertTrue(stillInUse.historyCount() > 0);
        Assertions.assertEquals(POLICE_ON_42, logic.findGroupParty("police"));
    }

    @Test
    void unmap_waitsForAnAssignmentInFlight() throws Exception {
        logic.mapGroup("police", GOV_42);
        createLease("police_station", new Party.Personal(PLAYER));
        int groupPartyId;
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT partyId FROM GroupParty WHERE groupName = 'police'")) {
            Assertions.assertTrue(resultSet.next());
            groupPartyId = resultSet.getInt(1);
        }

        try (Connection assigning = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())) {
            assigning.setAutoCommit(false);
            try (Statement statement = assigning.createStatement()) {
                // Not committed: the contract now names the group, and the foreign key holds a
                // shared lock on the group's base row.
                statement.executeUpdate("UPDATE LeaseholdContract SET landlordPartyId = " + groupPartyId);
            }

            CompletableFuture<UnmapGroupResult> unmapping = CompletableFuture.supplyAsync(
                    () -> logic.unmapGroup("police"));
            Thread.sleep(500);
            Assertions.assertFalse(unmapping.isDone(), "unmap must wait for the assignment in flight");
            assigning.commit();

            UnmapGroupResult result = unmapping.get(30, TimeUnit.SECONDS);
            UnmapGroupResult.StillInUse stillInUse = Assertions.assertInstanceOf(
                    UnmapGroupResult.StillInUse.class, result);
            Assertions.assertEquals(1, stillInUse.contractCount());
        }
        Assertions.assertEquals(POLICE_ON_42, logic.findGroupParty("police"));
    }

    @Test
    void unmap_unknownGroup() {
        Assertions.assertEquals(new UnmapGroupResult.NotMapped(), logic.unmapGroup("mafia"));
    }

    @Test
    void list_countsContractsPerGroup() {
        logic.mapGroup("police", GOV_42);
        logic.mapGroup("army", BUSINESS_77);
        logic.mapGroup("bank", GOV_42);
        createLease("station_1", POLICE_ON_42);
        createLease("station_2", POLICE_ON_42);
        Assertions.assertTrue(logic.createFreehold("barracks", WORLD_ID, 1000.0,
                new Party.Group("army", 77, AccountKind.BUSINESS), null));
        Assertions.assertTrue(logic.createFreehold("hq", WORLD_ID, 1000.0, POLICE_ON_42, null));

        Assertions.assertEquals(List.of(
                        new GroupMapping(new Party.Group("army", 77, AccountKind.BUSINESS), 1),
                        new GroupMapping(new Party.Group("bank", 42, AccountKind.GOVERNMENT), 0),
                        new GroupMapping(POLICE_ON_42, 3)),
                logic.listGroupMappings());
    }

    @Test
    void list_isEmptyWithoutGroups() {
        logic.mapGroup("police", GOV_42);
        logic.unmapGroup("police");
        // An account party is not a group.
        createLease("station", GOV_42);

        Assertions.assertEquals(List.of(), logic.listGroupMappings());
    }

    @Test
    void twoGroups_mayShareAnAccount() {
        Assertions.assertInstanceOf(MapGroupResult.Created.class, logic.mapGroup("police", GOV_42));
        Assertions.assertInstanceOf(MapGroupResult.Created.class, logic.mapGroup("wardens", GOV_42));

        Assertions.assertEquals(POLICE_ON_42, logic.findGroupParty("police"));
        Assertions.assertEquals(new Party.Group("wardens", 42, AccountKind.GOVERNMENT),
                logic.findGroupParty("wardens"));
    }
}
