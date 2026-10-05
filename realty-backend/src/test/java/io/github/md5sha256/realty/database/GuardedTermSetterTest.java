package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.RenewLeaseholdResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetDurationResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetMaxRenewalsResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetPriceResult;
import io.github.md5sha256.realty.api.RealtyBackend.UnsetPriceResult;
import io.github.md5sha256.realty.database.RealtyBackendImpl.WriteRefusal;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class GuardedTermSetterTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID PLAYER_C = UUID.randomUUID();
    private static final Party.Account ACCOUNT = Party.account(42, AccountKind.GOVERNMENT);

    private static final ActorContext AS_A = ActorContext.player(PLAYER_A, false);
    private static final ActorContext AS_C = ActorContext.player(PLAYER_C, false);
    private static final ActorContext AS_AUTHORITY = ActorContext.player(AUTHORITY, false);

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "guarded_region_" + REGION_COUNTER.incrementAndGet();
    }

    private static String vacantLease() {
        return vacantLease(Party.personal(PLAYER_A));
    }

    private static String vacantLease(Party landlord) {
        String regionId = uniqueRegionId();
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 3600, 5, landlord));
        return regionId;
    }

    private static String rentedLease() {
        String regionId = vacantLease();
        logic.rentRegion(regionId, WORLD_ID, PLAYER_B);
        Assertions.assertNotNull(lease(regionId).tenantId());
        return regionId;
    }

    private static String freehold() {
        String regionId = uniqueRegionId();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 500.0, Party.personal(AUTHORITY), PLAYER_A));
        return regionId;
    }

    private static String unsoldFreehold() {
        String regionId = uniqueRegionId();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 500.0, Party.personal(AUTHORITY), null));
        return regionId;
    }

    private static LeaseholdContractEntity lease(String regionId) {
        return logic.getLeaseholdContract(regionId, WORLD_ID);
    }

    private static FreeholdContractEntity freeholdContract(String regionId) {
        return logic.getFreeholdContract(regionId, WORLD_ID);
    }

    private static void assertLeaseUnchanged(String id) {
        LeaseholdContractEntity lease = lease(id);
        Assertions.assertEquals(200.0, lease.price());
        Assertions.assertEquals(3600, lease.durationSeconds());
        Assertions.assertEquals(5, lease.maxExtensions());
    }

    @Test
    void landlordSetsPriceOnVacantLease() {
        String id = vacantLease();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 300.0, AS_A, true));
        Assertions.assertEquals(300.0, lease(id).price());
    }

    @Test
    void strangerIsRefusedOnLease() {
        String id = vacantLease();
        Assertions.assertInstanceOf(SetPriceResult.NotAuthorized.class,
                logic.setPrice(id, WORLD_ID, 300.0, AS_C, true));
        Assertions.assertInstanceOf(SetDurationResult.NotAuthorized.class,
                logic.setDuration(id, WORLD_ID, 7200, AS_C, true));
        Assertions.assertInstanceOf(SetMaxRenewalsResult.NotAuthorized.class,
                logic.setMaxRenewals(id, WORLD_ID, 9, AS_C, true));
        assertLeaseUnchanged(id);
    }

    @Test
    void memberOfAnAccountLandlordSetsTerms() {
        String id = vacantLease(ACCOUNT);
        ActorContext member = new ActorContext(PLAYER_C, Set.of(ACCOUNT), Set.of(), false);
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 300.0, member, true));
        Assertions.assertEquals(300.0, lease(id).price());
        Assertions.assertInstanceOf(SetDurationResult.Success.class,
                logic.setDuration(id, WORLD_ID, 7200, member, true));
        Assertions.assertEquals(7200, lease(id).durationSeconds());
        Assertions.assertInstanceOf(SetMaxRenewalsResult.Success.class,
                logic.setMaxRenewals(id, WORLD_ID, 9, member, true));
        Assertions.assertEquals(9, lease(id).maxExtensions());
        Assertions.assertEquals(ACCOUNT, lease(id).landlord());
    }

    @Test
    void outsiderOfAnAccountLandlordIsRefused() {
        String id = vacantLease(ACCOUNT);
        Assertions.assertInstanceOf(SetPriceResult.NotAuthorized.class,
                logic.setPrice(id, WORLD_ID, 300.0, AS_C, true));
        Assertions.assertInstanceOf(SetDurationResult.NotAuthorized.class,
                logic.setDuration(id, WORLD_ID, 7200, AS_C, true));
        Assertions.assertInstanceOf(SetMaxRenewalsResult.NotAuthorized.class,
                logic.setMaxRenewals(id, WORLD_ID, 9, AS_C, true));
        assertLeaseUnchanged(id);
    }

    @Test
    void rentedLeaseIsOccupiedWhenVacantOnly() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetPriceResult.Occupied.class,
                logic.setPrice(id, WORLD_ID, 300.0, AS_A, true));
        Assertions.assertInstanceOf(SetDurationResult.Occupied.class,
                logic.setDuration(id, WORLD_ID, 7200, AS_A, true));
        Assertions.assertInstanceOf(SetMaxRenewalsResult.Occupied.class,
                logic.setMaxRenewals(id, WORLD_ID, 9, AS_A, true));
        assertLeaseUnchanged(id);
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void rentedLeaseChangesWhenNotVacantOnly() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 300.0, AS_A, false));
        Assertions.assertEquals(300.0, lease(id).price());
    }

    @Test
    void bypassActsForAnyone() {
        String id = vacantLease();
        ActorContext admin = ActorContext.player(PLAYER_C, true);
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 300.0, admin, true));
        Assertions.assertEquals(300.0, lease(id).price());
    }

    @Test
    void bypassIsStillHeldToVacantOnlyOnARentedLease() {
        String id = rentedLease();
        ActorContext admin = ActorContext.player(PLAYER_C, true);
        Assertions.assertInstanceOf(SetPriceResult.Occupied.class,
                logic.setPrice(id, WORLD_ID, 300.0, admin, true));
        Assertions.assertEquals(200.0, lease(id).price());
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void bypassActsOnAFreeholdHeldBySomeoneElse() {
        String id = freehold();
        ActorContext admin = ActorContext.player(PLAYER_C, true);
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 750.0, admin, true));
        Assertions.assertEquals(750.0, freeholdContract(id).price());
        Assertions.assertInstanceOf(UnsetPriceResult.Success.class,
                logic.unsetPrice(id, WORLD_ID, admin));
        Assertions.assertNull(freeholdContract(id).price());
        Assertions.assertEquals(PLAYER_A, freeholdContract(id).titleHolderId());
    }

    @Test
    void strangerLearnsNothingAboutTheExtensionCount() {
        String id = rentedLease();
        Assertions.assertInstanceOf(RenewLeaseholdResult.Success.class,
                logic.renewLeasehold(id, WORLD_ID, PLAYER_B));
        Assertions.assertEquals(1, lease(id).currentMaxExtensions());
        Assertions.assertInstanceOf(SetMaxRenewalsResult.NotAuthorized.class,
                logic.setMaxRenewals(id, WORLD_ID, 0, AS_C, false));
        Assertions.assertEquals(5, lease(id).maxExtensions());
        Assertions.assertEquals(1, lease(id).currentMaxExtensions());
        // The landlord does learn it.
        Assertions.assertInstanceOf(SetMaxRenewalsResult.BelowCurrentExtensions.class,
                logic.setMaxRenewals(id, WORLD_ID, 0, AS_A, false));
    }

    @Test
    void strangerLearnsNothingAboutAnAuction() {
        String id = freehold();
        logic.setTitleHolder(id, WORLD_ID, null);
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(id, WORLD_ID, AS_AUTHORITY, 3600, 3600, 100.0, 10.0));
        Assertions.assertInstanceOf(SetPriceResult.NotAuthorized.class,
                logic.setPrice(id, WORLD_ID, 750.0, AS_C, true));
        Assertions.assertInstanceOf(UnsetPriceResult.NotAuthorized.class,
                logic.unsetPrice(id, WORLD_ID, AS_C));
        Assertions.assertEquals(500.0, freeholdContract(id).price());
        // The manager does learn it.
        Assertions.assertInstanceOf(SetPriceResult.AuctionExists.class,
                logic.setPrice(id, WORLD_ID, 750.0, AS_AUTHORITY, true));
    }

    @Test
    void formerTitleHolderCannotRepriceFreehold() {
        String id = freehold();
        logic.setTitleHolder(id, WORLD_ID, PLAYER_B);
        Assertions.assertInstanceOf(SetPriceResult.NotAuthorized.class,
                logic.setPrice(id, WORLD_ID, 1.0, AS_A, true));
        Assertions.assertEquals(500.0, freeholdContract(id).price());
        Assertions.assertInstanceOf(UnsetPriceResult.NotAuthorized.class,
                logic.unsetPrice(id, WORLD_ID, AS_A));
        Assertions.assertEquals(500.0, freeholdContract(id).price());
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void titleHolderRepricesFreehold() {
        String id = freehold();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 750.0, AS_A, true));
        Assertions.assertEquals(750.0, freeholdContract(id).price());
    }

    @Test
    void managerOfTheAuthorityRepricesAnUnsoldFreehold() {
        String id = unsoldFreehold();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 750.0, AS_AUTHORITY, true));
        Assertions.assertEquals(750.0, freeholdContract(id).price());
        Assertions.assertInstanceOf(UnsetPriceResult.Success.class,
                logic.unsetPrice(id, WORLD_ID, AS_AUTHORITY));
        Assertions.assertNull(freeholdContract(id).price());
    }

    @Test
    void authorityCannotRepriceASoldFreehold() {
        String id = freehold();
        Assertions.assertInstanceOf(SetPriceResult.NotAuthorized.class,
                logic.setPrice(id, WORLD_ID, 750.0, AS_AUTHORITY, true));
        Assertions.assertInstanceOf(UnsetPriceResult.NotAuthorized.class,
                logic.unsetPrice(id, WORLD_ID, AS_AUTHORITY));
        Assertions.assertEquals(500.0, freeholdContract(id).price());
    }

    @Test
    void settingTheSameValueSucceeds() {
        String id = vacantLease();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 200.0, AS_A, true));
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 200.0, AS_A, true));
        Assertions.assertEquals(200.0, lease(id).price());
        Assertions.assertInstanceOf(SetDurationResult.Success.class,
                logic.setDuration(id, WORLD_ID, 3600, AS_A, true));
        Assertions.assertEquals(3600, lease(id).durationSeconds());
    }

    @Test
    void oldSignaturesStillApplyUnconditionally() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetPriceResult.Success.class,
                logic.setPrice(id, WORLD_ID, 300.0));
        Assertions.assertEquals(300.0, lease(id).price());
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void rentCommittedAfterTheFirstReadIsAnsweredOccupied() {
        String id = vacantLease();
        try (SqlSessionWrapper first = database.openSession()) {
            // Takes the first session's snapshot: vacant, landlord A.
            Assertions.assertNull(first.leaseholdContractMapper().selectByRegion(id, WORLD_ID).tenantId());
            int landlordPartyId = first.partyMapper().findId(Party.personal(PLAYER_A));
            logic.rentRegion(id, WORLD_ID, PLAYER_B);
            first.session().rollback(true); // the setters end their read-only transaction before writing
            int updated = first.leaseholdContractMapper()
                    .updatePriceByRegion(id, WORLD_ID, 300.0, landlordPartyId, true);
            Assertions.assertEquals(0, updated);
            Assertions.assertEquals(WriteRefusal.OCCUPIED,
                    RealtyBackendImpl.diagnoseLeaseholdRefusal(first, id, WORLD_ID, landlordPartyId, true));
        }
        Assertions.assertEquals(200.0, lease(id).price());
    }

    @Test
    void landlordChangedAfterTheFirstReadIsAnsweredHolderDiffers() {
        String id = vacantLease();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(Party.personal(PLAYER_A),
                    first.leaseholdContractMapper().selectByRegion(id, WORLD_ID).landlord());
            int landlordPartyId = first.partyMapper().findId(Party.personal(PLAYER_A));
            logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), ActorContext.console());
            first.session().rollback(true); // the setters end their read-only transaction before writing
            int updated = first.leaseholdContractMapper()
                    .updatePriceByRegion(id, WORLD_ID, 300.0, landlordPartyId, true);
            Assertions.assertEquals(0, updated);
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseLeaseholdRefusal(first, id, WORLD_ID, landlordPartyId, true));
        }
        Assertions.assertEquals(200.0, lease(id).price());
        Assertions.assertEquals(Party.personal(PLAYER_C), lease(id).landlord());
    }

    @Test
    void titleHolderChangedAfterTheFirstReadIsAnsweredHolderDiffers() {
        String id = freehold();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(PLAYER_A, first.freeholdContractMapper().selectByRegion(id, WORLD_ID).titleHolderId());
            logic.setTitleHolder(id, WORLD_ID, PLAYER_B);
            first.session().rollback(true); // the setters end their read-only transaction before writing
            int updated = first.freeholdContractMapper()
                    .updatePriceByRegion(id, WORLD_ID, 1.0, true, PLAYER_A);
            Assertions.assertEquals(0, updated);
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseFreeholdRefusal(first, id, WORLD_ID, true, PLAYER_A));
        }
        Assertions.assertEquals(500.0, freeholdContract(id).price());
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }
}
