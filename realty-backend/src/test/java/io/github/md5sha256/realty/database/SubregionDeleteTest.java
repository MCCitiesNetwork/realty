package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult;
import io.github.md5sha256.realty.api.RealtyBackend.DeleteLeaseholdResult;
import io.github.md5sha256.realty.api.RealtyBackend.DeletedLeasehold;
import io.github.md5sha256.realty.api.RealtyBackend.MarkDeletionResult;
import io.github.md5sha256.realty.api.RealtyBackend.UnmarkDeletionResult;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Deleting a leasehold by its landlord: at once while vacant, or by a mark the sweep acts on once vacant. */
class SubregionDeleteTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final Party.Account ACCOUNT = Party.account(77, AccountKind.GOVERNMENT);

    private static final ActorContext AS_LANDLORD = ActorContext.player(LANDLORD, false);
    private static final ActorContext AS_STRANGER = ActorContext.player(STRANGER, false);
    private static final ActorContext AS_STAFF = ActorContext.player(STRANGER, true);

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String vacantLease() {
        return vacantLease(Party.personal(LANDLORD));
    }

    private static String vacantLease(Party landlord) {
        String regionId = "subregion_delete_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, 200.0, 3600, 5, landlord));
        return regionId;
    }

    private static String rentedLease() {
        String regionId = vacantLease();
        logic.rentRegion(regionId, WORLD_ID, TENANT);
        Assertions.assertEquals(TENANT, lease(regionId).tenantId());
        return regionId;
    }

    private static final LocalDateTime EFFECTIVE =
            LocalDateTime.now().plusDays(7).truncatedTo(ChronoUnit.SECONDS);

    private static String terminatingLease() {
        String regionId = rentedLease();
        logic.terminateLease(regionId, WORLD_ID, EFFECTIVE, EFFECTIVE, "landlord");
        return regionId;
    }

    /** Ends the tenancy as the termination sweep does. */
    private static void clearTenant(String regionId) {
        int contractId = lease(regionId).leaseholdContractId();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.leaseholdContractMapper().clearTenant(contractId);
            wrapper.session().commit();
        }
    }

    private static boolean swept(List<DeletedLeasehold> deleted, String regionId) {
        return deleted.stream().anyMatch(d -> d.worldGuardRegionId().equals(regionId));
    }

    private static LeaseholdContractEntity lease(String regionId) {
        return logic.getLeaseholdContract(regionId, WORLD_ID);
    }

    private static int leaseRows(int leaseholdContractId) throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession();
             PreparedStatement statement = wrapper.session().getConnection().prepareStatement(
                     "SELECT COUNT(*) FROM LeaseholdContract WHERE leaseholdContractId = ?")) {
            statement.setInt(1, leaseholdContractId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void vacantLeaseIsDeletedByLandlordManager() throws SQLException {
        String id = vacantLease();
        int contractId = lease(id).leaseholdContractId();
        Assertions.assertInstanceOf(DeleteLeaseholdResult.Success.class,
                logic.deleteVacantLeasehold(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertNull(lease(id));
        Assertions.assertEquals(0, leaseRows(contractId), "the lease row goes with the region");
    }

    @Test
    void rentedLeaseIsRefusedAsOccupied() {
        String id = rentedLease();
        Assertions.assertEquals(new DeleteLeaseholdResult.Occupied(null),
                logic.deleteVacantLeasehold(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertEquals(TENANT, lease(id).tenantId());
    }

    @Test
    void terminatingLeaseReportsItsEffectiveDate() {
        String id = rentedLease();
        LocalDateTime effective = LocalDateTime.now().plusDays(7).truncatedTo(ChronoUnit.SECONDS);
        logic.terminateLease(id, WORLD_ID, effective, effective, "landlord");
        Assertions.assertEquals(new DeleteLeaseholdResult.Occupied(effective),
                logic.deleteVacantLeasehold(id, WORLD_ID, AS_LANDLORD));
    }

    @Test
    void nonManagerIsRefused() {
        String id = vacantLease();
        Assertions.assertInstanceOf(DeleteLeaseholdResult.NotAuthorized.class,
                logic.deleteVacantLeasehold(id, WORLD_ID, AS_STRANGER));
        Assertions.assertNotNull(lease(id));
    }

    @Test
    void bypassDeletesVacantButNotRented() {
        String vacant = vacantLease();
        Assertions.assertInstanceOf(DeleteLeaseholdResult.Success.class,
                logic.deleteVacantLeasehold(vacant, WORLD_ID, AS_STAFF));
        String rented = rentedLease();
        Assertions.assertInstanceOf(DeleteLeaseholdResult.Occupied.class,
                logic.deleteVacantLeasehold(rented, WORLD_ID, AS_STAFF));
        Assertions.assertNotNull(lease(rented));
    }

    @Test
    void accountLandlordManagerMayDelete() {
        String id = vacantLease(ACCOUNT);
        ActorContext member = new ActorContext(STRANGER, Set.of(ACCOUNT), Set.of(), false);
        Assertions.assertInstanceOf(DeleteLeaseholdResult.Success.class,
                logic.deleteVacantLeasehold(id, WORLD_ID, member));
        Assertions.assertNull(lease(id));
    }

    @Test
    void freeholdIsNoLeaseholdContract() {
        String id = "subregion_delete_freehold_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(id, WORLD_ID, 500.0, Party.personal(LANDLORD), LANDLORD));
        Assertions.assertInstanceOf(DeleteLeaseholdResult.NoLeaseholdContract.class,
                logic.deleteVacantLeasehold(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertNotNull(logic.getFreeholdContract(id, WORLD_ID));
    }

    @Test
    void tenantArrivingAfterTheReadMakesNothingDeleted() {
        String id = rentedLease();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer landlordPartyId = wrapper.partyMapper().findId(Party.personal(LANDLORD));
            Assertions.assertEquals(0,
                    wrapper.leaseholdContractMapper().deleteVacantByRegion(id, WORLD_ID, landlordPartyId));
            wrapper.session().commit();
        }
        Assertions.assertEquals(TENANT, lease(id).tenantId());
    }

    @Test
    void markRefusedWhenNotTerminating() {
        String id = rentedLease();
        Assertions.assertInstanceOf(MarkDeletionResult.NotTerminating.class,
                logic.markForDeletion(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertFalse(logic.isMarkedForDeletion(id, WORLD_ID));
    }

    @Test
    void markRefusedForNonManager() {
        String id = terminatingLease();
        Assertions.assertInstanceOf(MarkDeletionResult.NotAuthorized.class,
                logic.markForDeletion(id, WORLD_ID, AS_STRANGER));
        Assertions.assertFalse(logic.isMarkedForDeletion(id, WORLD_ID));
    }

    @Test
    void markClosesLeaseToTenants() {
        String id = terminatingLease();
        Assertions.assertEquals(new MarkDeletionResult.Success(EFFECTIVE),
                logic.markForDeletion(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertTrue(logic.isMarkedForDeletion(id, WORLD_ID));
        Assertions.assertFalse(lease(id).acceptingTenants());
    }

    @Test
    void markTwiceReportsAlreadyMarked() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertEquals(new MarkDeletionResult.AlreadyMarked(EFFECTIVE),
                logic.markForDeletion(id, WORLD_ID, AS_LANDLORD));
    }

    @Test
    void sweepDeletesMarkedLeaseOnceVacant() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        clearTenant(id);
        List<DeletedLeasehold> deleted = logic.deleteMarkedVacantLeaseholds();
        Assertions.assertTrue(deleted.contains(new DeletedLeasehold(id, WORLD_ID, Party.personal(LANDLORD))));
        Assertions.assertNull(lease(id));
    }

    @Test
    void sweepLeavesMarkedRentedLease() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertFalse(swept(logic.deleteMarkedVacantLeaseholds(), id));
        Assertions.assertNotNull(lease(id));
    }

    @Test
    void sweepLeavesUnmarkedVacantLease() {
        String id = vacantLease();
        Assertions.assertFalse(swept(logic.deleteMarkedVacantLeaseholds(), id));
        Assertions.assertNotNull(lease(id));
    }

    @Test
    void cancelTerminationClearsMarkAndReopens() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertInstanceOf(CancelTerminationResult.Success.class,
                logic.cancelTermination(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertFalse(logic.isMarkedForDeletion(id, WORLD_ID));
        Assertions.assertTrue(lease(id).acceptingTenants());
    }

    @Test
    void cancelTerminationKeepsAClosedUnmarkedLeaseClosed() {
        String id = terminatingLease();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.leaseholdContractMapper().updateAcceptingTenantsByRegion(id, WORLD_ID, false);
            wrapper.session().commit();
        }
        logic.cancelTermination(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertFalse(lease(id).acceptingTenants());
    }

    @Test
    void unmarkReopensLease() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertInstanceOf(UnmarkDeletionResult.Success.class,
                logic.unmarkForDeletion(id, WORLD_ID, AS_LANDLORD));
        Assertions.assertFalse(logic.isMarkedForDeletion(id, WORLD_ID));
        Assertions.assertTrue(lease(id).acceptingTenants());
        Assertions.assertEquals(EFFECTIVE, lease(id).terminationEffectiveDate(), "the termination stands");
    }

    @Test
    void unmarkRefusedWhenNotMarked() {
        String id = terminatingLease();
        Assertions.assertInstanceOf(UnmarkDeletionResult.NotMarked.class,
                logic.unmarkForDeletion(id, WORLD_ID, AS_LANDLORD));
    }

    @Test
    void unmarkRefusedForNonManager() {
        String id = terminatingLease();
        logic.markForDeletion(id, WORLD_ID, AS_LANDLORD);
        Assertions.assertInstanceOf(UnmarkDeletionResult.NotAuthorized.class,
                logic.unmarkForDeletion(id, WORLD_ID, AS_STRANGER));
        Assertions.assertTrue(logic.isMarkedForDeletion(id, WORLD_ID));
    }

    @Test
    void newLeaseIsUnmarked() {
        Assertions.assertFalse(logic.isMarkedForDeletion(vacantLease(), WORLD_ID));
    }
}
