package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.SetLandlordResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetTenantResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetTitleHolderResult;
import io.github.md5sha256.realty.database.RealtyBackendImpl.WriteRefusal;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class GuardedHolderSetterTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final UUID PLAYER_C = UUID.randomUUID();
    private static final Party.Account ACCOUNT = Party.account(43, AccountKind.GOVERNMENT);

    private static final ActorContext AS_A = ActorContext.player(PLAYER_A, false);
    private static final ActorContext AS_B = ActorContext.player(PLAYER_B, false);
    private static final ActorContext AS_C = ActorContext.player(PLAYER_C, false);
    private static final ActorContext AS_AUTHORITY = ActorContext.player(AUTHORITY, false);

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "guarded_holder_region_" + REGION_COUNTER.incrementAndGet();
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
        Assertions.assertEquals(PLAYER_B, lease(regionId).tenantId());
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

    /** The acting player, who also manages the party the landlord role would go to. */
    private static ActorContext managing(UUID player, Party party) {
        return new ActorContext(player, Set.of(party), Set.of(), false);
    }

    @Test
    void landlordSetsTenantOnVacantLease() {
        String id = vacantLease();
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(id, WORLD_ID, PLAYER_B, AS_A, true));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void strangerCannotSetTenant() {
        String id = vacantLease();
        Assertions.assertInstanceOf(SetTenantResult.NotAuthorized.class,
                logic.setTenant(id, WORLD_ID, PLAYER_B, AS_C, true));
        Assertions.assertNull(lease(id).tenantId());
    }

    @Test
    void replacingATenantNeedsVacantOnlyOff() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetTenantResult.Occupied.class,
                logic.setTenant(id, WORLD_ID, PLAYER_C, AS_A, true));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(id, WORLD_ID, PLAYER_C, AS_A, false));
        Assertions.assertEquals(PLAYER_C, lease(id).tenantId());
    }

    @Test
    void clearingATenantNeedsVacantOnlyOff() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetTenantResult.Occupied.class,
                logic.setTenant(id, WORLD_ID, null, AS_A, true));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(id, WORLD_ID, null, AS_A, false));
        Assertions.assertNull(lease(id).tenantId());
    }

    @Test
    void tenantCannotClearThemselves() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetTenantResult.NotAuthorized.class,
                logic.setTenant(id, WORLD_ID, null, AS_B, false));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void memberOfAnAccountLandlordSetsTenant() {
        String id = vacantLease(ACCOUNT);
        ActorContext member = managing(PLAYER_C, ACCOUNT);
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(id, WORLD_ID, PLAYER_B, member, true));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
        Assertions.assertEquals(ACCOUNT, lease(id).landlord());
    }

    @Test
    void outsiderOfAnAccountLandlordCannotSetTenant() {
        String id = vacantLease(ACCOUNT);
        Assertions.assertInstanceOf(SetTenantResult.NotAuthorized.class,
                logic.setTenant(id, WORLD_ID, PLAYER_B, AS_C, true));
        Assertions.assertNull(lease(id).tenantId());
    }

    @Test
    void rentedLeaseLandlordChangeIsOccupied() {
        String id = rentedLease();
        ActorContext asA = managing(PLAYER_A, Party.personal(PLAYER_C));
        Assertions.assertInstanceOf(SetLandlordResult.Occupied.class,
                logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), asA, true));
        Assertions.assertEquals(Party.personal(PLAYER_A), lease(id).landlord());
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void rentedLeaseLandlordChangesWhenNotVacantOnly() {
        String id = rentedLease();
        ActorContext asA = managing(PLAYER_A, Party.personal(PLAYER_C));
        Assertions.assertInstanceOf(SetLandlordResult.Success.class,
                logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), asA, false));
        Assertions.assertEquals(Party.personal(PLAYER_C), lease(id).landlord());
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
    }

    @Test
    void strangerCannotReassignLandlord() {
        String id = vacantLease();
        ActorContext asC = managing(PLAYER_C, Party.personal(PLAYER_C));
        Assertions.assertInstanceOf(SetLandlordResult.NotAllowedToReassign.class,
                logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), asC, true));
        Assertions.assertEquals(Party.personal(PLAYER_A), lease(id).landlord());
    }

    @Test
    void formerTitleHolderCannotReassignFreehold() {
        String id = freehold();
        logic.setTitleHolder(id, WORLD_ID, PLAYER_B);
        Assertions.assertInstanceOf(SetTitleHolderResult.NotAuthorized.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_C, AS_A));
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
        Assertions.assertInstanceOf(SetTitleHolderResult.NotAuthorized.class,
                logic.setTitleHolder(id, WORLD_ID, null, AS_A));
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void titleHolderReassignsFreehold() {
        String id = freehold();
        SetTitleHolderResult result = logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_A);
        SetTitleHolderResult.Success success = Assertions.assertInstanceOf(SetTitleHolderResult.Success.class, result);
        Assertions.assertEquals(PLAYER_A, success.previousTitleHolder());
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void managerOfTheAuthorityAssignsAnUnsoldFreehold() {
        String id = unsoldFreehold();
        Assertions.assertInstanceOf(SetTitleHolderResult.NotAuthorized.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_C));
        Assertions.assertNull(freeholdContract(id).titleHolderId());
        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_AUTHORITY));
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void authorityCannotReassignASoldFreehold() {
        String id = freehold();
        Assertions.assertInstanceOf(SetTitleHolderResult.NotAuthorized.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_AUTHORITY));
        Assertions.assertEquals(PLAYER_A, freeholdContract(id).titleHolderId());
    }

    @Test
    void bypassActsForAnyone() {
        ActorContext admin = ActorContext.player(PLAYER_C, true);
        String lease = vacantLease();
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(lease, WORLD_ID, PLAYER_B, admin, true));
        Assertions.assertEquals(PLAYER_B, lease(lease).tenantId());
        String freehold = freehold();
        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.setTitleHolder(freehold, WORLD_ID, PLAYER_B, admin));
        Assertions.assertEquals(PLAYER_B, freeholdContract(freehold).titleHolderId());
        Assertions.assertInstanceOf(SetLandlordResult.Success.class,
                logic.setLandlord(lease, WORLD_ID, Party.personal(PLAYER_C), admin, false));
        Assertions.assertEquals(Party.personal(PLAYER_C), lease(lease).landlord());
    }

    @Test
    void bypassIsStillHeldToVacantOnlyOnARentedLease() {
        String id = rentedLease();
        ActorContext admin = ActorContext.player(PLAYER_C, true);
        Assertions.assertInstanceOf(SetTenantResult.Occupied.class,
                logic.setTenant(id, WORLD_ID, null, admin, true));
        Assertions.assertInstanceOf(SetLandlordResult.Occupied.class,
                logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), admin, true));
        Assertions.assertEquals(PLAYER_B, lease(id).tenantId());
        Assertions.assertEquals(Party.personal(PLAYER_A), lease(id).landlord());
    }

    @Test
    void oldSignaturesStillApplyUnconditionally() {
        String id = rentedLease();
        Assertions.assertInstanceOf(SetTenantResult.Success.class,
                logic.setTenant(id, WORLD_ID, PLAYER_C));
        Assertions.assertEquals(PLAYER_C, lease(id).tenantId());
        Assertions.assertInstanceOf(SetLandlordResult.Success.class,
                logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_B), ActorContext.console()));
        Assertions.assertEquals(Party.personal(PLAYER_B), lease(id).landlord());
        String freehold = freehold();
        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.setTitleHolder(freehold, WORLD_ID, PLAYER_B));
        Assertions.assertEquals(PLAYER_B, freeholdContract(freehold).titleHolderId());
    }

    // --- The holder condition in the SQL: a second session changes the holder after the first read ---

    @Test
    void landlordChangedAfterTheFirstReadStopsSetTenant() {
        String id = vacantLease();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(Party.personal(PLAYER_A),
                    first.leaseholdContractMapper().selectByRegion(id, WORLD_ID).landlord());
            int landlordPartyId = first.partyMapper().findId(Party.personal(PLAYER_A));
            logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), ActorContext.console());
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.leaseholdContractMapper()
                    .updateTenantByRegion(id, WORLD_ID, PLAYER_B, landlordPartyId, false));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseLeaseholdRefusal(first, id, WORLD_ID, landlordPartyId, false));
        }
        Assertions.assertNull(lease(id).tenantId());
        Assertions.assertEquals(Party.personal(PLAYER_C), lease(id).landlord());
    }

    @Test
    void landlordChangedAfterTheFirstReadStopsSetLandlord() {
        String id = vacantLease();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(Party.personal(PLAYER_A),
                    first.leaseholdContractMapper().selectByRegion(id, WORLD_ID).landlord());
            int landlordPartyId = first.partyMapper().findId(Party.personal(PLAYER_A));
            int newPartyId = first.partyMapper().findOrInsert(Party.personal(PLAYER_B));
            logic.setLandlord(id, WORLD_ID, Party.personal(PLAYER_C), ActorContext.console());
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.leaseholdContractMapper()
                    .updateLandlordByRegion(id, WORLD_ID, newPartyId, landlordPartyId, false));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseLeaseholdRefusal(first, id, WORLD_ID, landlordPartyId, false));
        }
        Assertions.assertEquals(Party.personal(PLAYER_C), lease(id).landlord());
    }

    @Test
    void titleHolderChangedAfterTheFirstReadStopsSetTitleHolder() {
        String id = freehold();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(PLAYER_A, first.freeholdContractMapper().selectByRegion(id, WORLD_ID).titleHolderId());
            logic.setTitleHolder(id, WORLD_ID, PLAYER_B);
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.freeholdContractMapper()
                    .updateTitleHolderByRegion(id, WORLD_ID, PLAYER_C, true, PLAYER_A, null));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseFreeholdRefusal(first, id, WORLD_ID, true, PLAYER_A, null));
        }
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void titleAssignedAfterTheFirstReadStopsSetTitleHolderOnAnUnsoldFreehold() {
        String id = unsoldFreehold();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertNull(first.freeholdContractMapper().selectByRegion(id, WORLD_ID).titleHolderId());
            logic.setTitleHolder(id, WORLD_ID, PLAYER_B);
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.freeholdContractMapper()
                    .updateTitleHolderByRegion(id, WORLD_ID, PLAYER_C, true, null, null));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseFreeholdRefusal(first, id, WORLD_ID, true, null, null));
        }
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }

    @Test
    void authorityChangedAfterTheFirstReadStopsSetTitleHolderOnAnUnsoldFreehold() {
        String id = unsoldFreehold();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(Party.personal(AUTHORITY),
                    first.freeholdContractMapper().selectByRegion(id, WORLD_ID).authority());
            int authorityPartyId = first.partyMapper().findId(Party.personal(AUTHORITY));
            logic.setAuthority(id, WORLD_ID, Party.personal(PLAYER_C));
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.freeholdContractMapper()
                    .updateTitleHolderByRegion(id, WORLD_ID, PLAYER_B, true, null, authorityPartyId));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseFreeholdRefusal(first, id, WORLD_ID, true, null, authorityPartyId));
        }
        Assertions.assertNull(freeholdContract(id).titleHolderId());
        Assertions.assertEquals(Party.personal(PLAYER_C), freeholdContract(id).authority());
    }

    @Test
    void authorityChangedAfterTheFirstReadStopsSetPriceOnAnUnsoldFreehold() {
        String id = unsoldFreehold();
        try (SqlSessionWrapper first = database.openSession()) {
            Assertions.assertEquals(Party.personal(AUTHORITY),
                    first.freeholdContractMapper().selectByRegion(id, WORLD_ID).authority());
            int authorityPartyId = first.partyMapper().findId(Party.personal(AUTHORITY));
            logic.setAuthority(id, WORLD_ID, Party.personal(PLAYER_C));
            RealtyBackendImpl.endRead(first); // as the setters do between their reads and their write
            Assertions.assertEquals(0, first.freeholdContractMapper()
                    .updatePriceByRegion(id, WORLD_ID, 1.0, true, null, authorityPartyId));
            Assertions.assertEquals(WriteRefusal.HOLDER_DIFFERS,
                    RealtyBackendImpl.diagnoseFreeholdRefusal(first, id, WORLD_ID, true, null, authorityPartyId));
        }
        Assertions.assertEquals(500.0, freeholdContract(id).price());
        Assertions.assertEquals(Party.personal(PLAYER_C), freeholdContract(id).authority());
    }

    @Test
    void managerOfTheCurrentAuthorityStillAssignsAnUnsoldFreehold() {
        String id = unsoldFreehold();
        logic.setAuthority(id, WORLD_ID, Party.personal(PLAYER_C));
        Assertions.assertInstanceOf(SetTitleHolderResult.NotAuthorized.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_AUTHORITY));
        Assertions.assertNull(freeholdContract(id).titleHolderId());
        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.setTitleHolder(id, WORLD_ID, PLAYER_B, AS_C));
        Assertions.assertEquals(PLAYER_B, freeholdContract(id).titleHolderId());
    }
}
