package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.ListResult;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The land of a party: a player, an account or a group is the authority of freeholds and the landlord
 * of leaseholds, and a listing keeps the two apart. An account or a group never holds a title or
 * rents in this version.
 */
class PartyPortfolioTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final Party.Account GOV = new Party.Account(42, AccountKind.GOVERNMENT);

    private static int regionCounter;

    private static String freeholdOf(Party authority, UUID titleHolder) {
        String regionId = "portfolio_" + ++regionCounter;
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, authority, titleHolder));
        return regionId;
    }

    private static String leaseholdOf(Party landlord) {
        String regionId = "portfolio_" + ++regionCounter;
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, landlord));
        return regionId;
    }

    private static List<String> ids(List<RealtyRegionEntity> regions) {
        return regions.stream().map(RealtyRegionEntity::worldGuardRegionId).toList();
    }

    @Test
    void account_listsTheLandItIsAuthorityOf() {
        String first = freeholdOf(GOV, PLAYER_A);
        String second = freeholdOf(GOV, PLAYER_B);
        freeholdOf(new Party.Personal(PLAYER_A), PLAYER_B);

        ListResult result = logic.listRegions(GOV, 10, 0);
        Assertions.assertEquals(2, result.authorityCount());
        Assertions.assertEquals(Set.of(first, second), Set.copyOf(ids(result.authority())));
        Assertions.assertEquals(2, result.authority().size());
    }

    @Test
    void account_ownsAndRentsNothingInStage1() {
        freeholdOf(GOV, PLAYER_A);

        ListResult result = logic.listRegions(GOV, 10, 0);
        Assertions.assertEquals(0, result.ownedCount());
        Assertions.assertEquals(0, result.rentedCount());
        Assertions.assertEquals(List.of(), result.owned());
        Assertions.assertEquals(List.of(), result.rented());
        Assertions.assertEquals(new RealtyBackend.SingleCategoryResult(0, List.of()),
                logic.listOwnedRegions(GOV, 10, 0));
        Assertions.assertEquals(new RealtyBackend.SingleCategoryResult(0, List.of()),
                logic.listRentedRegions(GOV, 10, 0));
    }

    @Test
    void group_listsItsLand() {
        Party.Group wardens = new Party.Group("wardens", GOV.accountId(), GOV.kind());
        Assertions.assertInstanceOf(RealtyBackend.MapGroupResult.Created.class, logic.mapGroup("wardens", GOV));
        String regionId = freeholdOf(wardens, PLAYER_A);
        freeholdOf(GOV, PLAYER_A);

        ListResult result = logic.listRegions(wardens, 10, 0);
        Assertions.assertEquals(1, result.authorityCount());
        Assertions.assertEquals(List.of(regionId), ids(result.authority()));
        Assertions.assertEquals(1, result.totalCount());
    }

    @Test
    void player_matchesTheUuidForm() {
        freeholdOf(GOV, PLAYER_A);
        freeholdOf(new Party.Personal(PLAYER_A), PLAYER_B);
        leaseholdOf(new Party.Personal(PLAYER_A));
        String rented = leaseholdOf(GOV);
        logic.rentRegion(rented, WORLD_ID, PLAYER_A);

        Party.Personal a = new Party.Personal(PLAYER_A);
        ListResult byParty = logic.listRegions(a, 10, 0);
        Assertions.assertEquals(logic.listRegions(PLAYER_A, 10, 0), byParty);
        Assertions.assertEquals(4, byParty.totalCount());
        Assertions.assertEquals(logic.listOwnedRegions(PLAYER_A, 10, 0), logic.listOwnedRegions(a, 10, 0));
        Assertions.assertEquals(logic.listRentedRegions(PLAYER_A, 10, 0), logic.listRentedRegions(a, 10, 0));
        Assertions.assertEquals(logic.listAuthorityRegions(PLAYER_A, 10, 0), logic.listAuthorityRegions(a, 10, 0));
        Assertions.assertEquals(logic.listLandlordRegions(PLAYER_A, 10, 0), logic.listLandlordRegions(a, 10, 0));
    }

    @Test
    void countRegionsByLandlord_countsPerParty() {
        leaseholdOf(GOV);
        leaseholdOf(GOV);
        leaseholdOf(new Party.Personal(PLAYER_A));

        Assertions.assertEquals(2, logic.countRegionsByLandlord(GOV));
        Assertions.assertEquals(1, logic.countRegionsByLandlord(new Party.Personal(PLAYER_A)));
    }

    @Test
    void landlord_listsTheLeasesThePartyLets() {
        String first = leaseholdOf(GOV);
        String second = leaseholdOf(GOV);
        leaseholdOf(new Party.Personal(PLAYER_A));

        ListResult result = logic.listRegions(GOV, 10, 0);
        Assertions.assertEquals(2, result.landlordCount());
        Assertions.assertEquals(Set.of(first, second), Set.copyOf(ids(result.landlord())));
        Assertions.assertEquals(2, result.landlord().size());

        RealtyBackend.SingleCategoryResult single = logic.listLandlordRegions(GOV, 10, 0);
        Assertions.assertEquals(2, single.totalCount());
        Assertions.assertEquals(Set.of(first, second), Set.copyOf(ids(single.regions())));
    }

    @Test
    void authority_listsTheFreeholdsThePartyIsAuthorityOf() {
        String first = freeholdOf(GOV, PLAYER_A);
        String second = freeholdOf(GOV, PLAYER_B);
        String third = freeholdOf(GOV, null);
        freeholdOf(new Party.Personal(PLAYER_A), PLAYER_B);

        ListResult result = logic.listRegions(GOV, 10, 0);
        Assertions.assertEquals(3, result.authorityCount());
        Assertions.assertEquals(Set.of(first, second, third), Set.copyOf(ids(result.authority())));
        Assertions.assertEquals(3, result.authority().size());

        RealtyBackend.SingleCategoryResult single = logic.listAuthorityRegions(GOV, 10, 0);
        Assertions.assertEquals(3, single.totalCount());
        Assertions.assertEquals(Set.of(first, second, third), Set.copyOf(ids(single.regions())));
    }

    @Test
    void authorityAndLandlord_areSeparate() {
        Set<String> freeholds = Set.of(freeholdOf(GOV, PLAYER_A), freeholdOf(GOV, PLAYER_B), freeholdOf(GOV, null));
        Set<String> leases = Set.of(leaseholdOf(GOV), leaseholdOf(GOV));

        ListResult result = logic.listRegions(GOV, 10, 0);
        Assertions.assertEquals(freeholds, Set.copyOf(ids(result.authority())));
        Assertions.assertEquals(leases, Set.copyOf(ids(result.landlord())));
        Assertions.assertEquals(3, result.authorityCount());
        Assertions.assertEquals(2, result.landlordCount());
        Assertions.assertEquals(5, result.totalCount());
    }

    @Test
    void player_hasAllFourParts() {
        String owned = freeholdOf(GOV, PLAYER_A);
        String authority = freeholdOf(new Party.Personal(PLAYER_A), PLAYER_B);
        String let = leaseholdOf(new Party.Personal(PLAYER_A));
        String rented = leaseholdOf(GOV);
        logic.rentRegion(rented, WORLD_ID, PLAYER_A);

        ListResult result = logic.listRegions(PLAYER_A, 10, 0);
        Assertions.assertEquals(List.of(owned), ids(result.owned()));
        Assertions.assertEquals(List.of(authority), ids(result.authority()));
        Assertions.assertEquals(List.of(let), ids(result.landlord()));
        Assertions.assertEquals(List.of(rented), ids(result.rented()));
        Assertions.assertEquals(new ListResult(1, 1, 1, 1, result.owned(), result.authority(),
                result.landlord(), result.rented()), result);
    }

    @Test
    void paging_runsOverTheFourPartsInOrder() {
        Party.Personal a = new Party.Personal(PLAYER_A);
        Set<String> owned = Set.of(freeholdOf(GOV, PLAYER_A), freeholdOf(GOV, PLAYER_A));
        Set<String> authority = Set.of(freeholdOf(a, PLAYER_B), freeholdOf(a, PLAYER_B));
        Set<String> let = Set.of(leaseholdOf(a), leaseholdOf(a));
        String firstRented = leaseholdOf(GOV);
        String secondRented = leaseholdOf(GOV);
        logic.rentRegion(firstRented, WORLD_ID, PLAYER_A);
        logic.rentRegion(secondRented, WORLD_ID, PLAYER_A);

        ListResult first = logic.listRegions(a, 3, 0);
        Assertions.assertEquals(List.of(2, 1, 0, 0), sizes(first));
        ListResult second = logic.listRegions(a, 3, 3);
        Assertions.assertEquals(List.of(0, 1, 2, 0), sizes(second));
        ListResult third = logic.listRegions(a, 3, 6);
        Assertions.assertEquals(List.of(0, 0, 0, 2), sizes(third));

        Assertions.assertEquals(owned, Set.copyOf(ids(first.owned())));
        Assertions.assertEquals(authority, Set.of(ids(first.authority()).getFirst(), ids(second.authority()).getFirst()));
        Assertions.assertEquals(let, Set.copyOf(ids(second.landlord())));
        Assertions.assertEquals(Set.of(firstRented, secondRented), Set.copyOf(ids(third.rented())));
        for (ListResult page : List.of(first, second, third)) {
            Assertions.assertEquals(8, page.totalCount());
        }
    }

    @Test
    void unknownParty_isEmptyAndInsertsNothing() throws SQLException {
        leaseholdOf(GOV);
        freeholdOf(GOV, PLAYER_A);
        int partiesBefore = partyRows();

        Party.Account stranger = new Party.Account(99, AccountKind.BUSINESS);
        Assertions.assertEquals(new ListResult(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of()),
                logic.listRegions(stranger, 10, 0));
        Assertions.assertEquals(new RealtyBackend.SingleCategoryResult(0, List.of()),
                logic.listAuthorityRegions(stranger, 10, 0));
        Assertions.assertEquals(new RealtyBackend.SingleCategoryResult(0, List.of()),
                logic.listLandlordRegions(stranger, 10, 0));

        Assertions.assertEquals(partiesBefore, partyRows());
    }

    private static List<Integer> sizes(ListResult result) {
        return List.of(result.owned().size(), result.authority().size(),
                result.landlord().size(), result.rented().size());
    }

    private static int partyRows() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             Statement statement = wrapper.session().getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM Party")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
