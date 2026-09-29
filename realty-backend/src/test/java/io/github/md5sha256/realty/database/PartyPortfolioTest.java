package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyBackend.ListResult;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * The land of a party that is not a player: an account or a group is the authority of freeholds and
 * the landlord of leaseholds, but in stage 1 it never holds a title or rents.
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
        Assertions.assertEquals(2, result.landlordCount());
        Assertions.assertEquals(List.of(first, second), ids(result.landlord()).stream().sorted().toList());
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
        Assertions.assertEquals(1, result.landlordCount());
        Assertions.assertEquals(List.of(regionId), ids(result.landlord()));
        Assertions.assertEquals(1, result.totalCount());
    }

    @Test
    void player_matchesTheUuidForm() {
        freeholdOf(GOV, PLAYER_A);
        freeholdOf(new Party.Personal(PLAYER_A), PLAYER_B);
        String rented = leaseholdOf(GOV);
        logic.rentRegion(rented, WORLD_ID, PLAYER_A);

        Party.Personal a = new Party.Personal(PLAYER_A);
        ListResult byParty = logic.listRegions(a, 10, 0);
        Assertions.assertEquals(logic.listRegions(PLAYER_A, 10, 0), byParty);
        Assertions.assertEquals(3, byParty.totalCount());
        Assertions.assertEquals(logic.listOwnedRegions(PLAYER_A, 10, 0), logic.listOwnedRegions(a, 10, 0));
        Assertions.assertEquals(logic.listRentedRegions(PLAYER_A, 10, 0), logic.listRentedRegions(a, 10, 0));
    }

    @Test
    void countRegionsByLandlord_countsPerParty() {
        leaseholdOf(GOV);
        leaseholdOf(GOV);
        leaseholdOf(new Party.Personal(PLAYER_A));

        Assertions.assertEquals(2, logic.countRegionsByLandlord(GOV));
        Assertions.assertEquals(1, logic.countRegionsByLandlord(new Party.Personal(PLAYER_A)));
    }
}
