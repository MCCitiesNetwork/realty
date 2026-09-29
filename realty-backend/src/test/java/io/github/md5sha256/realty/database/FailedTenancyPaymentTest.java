package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.ProposeModificationResult;
import io.github.md5sha256.realty.api.RealtyBackend.RenewLeaseholdResult;
import io.github.md5sha256.realty.api.RealtyBackend.RentResult;
import io.github.md5sha256.realty.api.RealtyBackend.UnrentResult;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdHistoryEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renting, renewing and unrenting each change the lease before any money moves, and are
 * undone if it does not. Like a purchase, each recorded itself at that first step, and
 * the undoing left the record there and put back less than had been changed. See
 * {@link FailedPurchaseTest}.
 */
class FailedTenancyPaymentTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final double RENT = 200.0;
    private static final long PERIOD_SECONDS = 86400;
    private static final int MAX_EXTENSIONS = 5;

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String regionToLet() {
        String regionId = "failed_tenancy_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createLeasehold(
                regionId, WORLD_ID, RENT, PERIOD_SECONDS, MAX_EXTENSIONS, new Party.Personal(LANDLORD)));
        return regionId;
    }

    private static RentResult.Success let(String regionId, UUID tenant) {
        return Assertions.assertInstanceOf(RentResult.Success.class,
                logic.rentRegion(regionId, WORLD_ID, tenant));
    }

    /** A region let to the tenant and paid for. */
    private static String regionLet() {
        String regionId = regionToLet();
        let(regionId, TENANT);
        return regionId;
    }

    private static RenewLeaseholdResult.Success renew(String regionId) {
        return Assertions.assertInstanceOf(RenewLeaseholdResult.Success.class,
                logic.renewLeasehold(regionId, WORLD_ID, TENANT));
    }

    private static void tryToRenewAndFailToPay(String regionId) {
        logic.rollbackRenewLeasehold(regionId, WORLD_ID, TENANT, renew(regionId));
    }

    private static UnrentResult.Success end(String regionId) {
        return Assertions.assertInstanceOf(UnrentResult.Success.class,
                logic.unrentRegion(regionId, WORLD_ID, TENANT));
    }

    /** An unrent whose refund the landlord could not pay. */
    private static void tryToUnrentAndFailToRefund(String regionId) {
        logic.rollbackUnrent(regionId, WORLD_ID, TENANT, end(regionId));
    }

    private static LeaseholdContractEntity lease(String regionId) {
        return logic.getRegionInfo(regionId, WORLD_ID).leasehold();
    }

    private static List<LeaseholdHistoryEntity> recorded(String regionId, HistoryEventType type) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdHistoryMapper().searchHistory(
                    regionId, WORLD_ID, type.name(), null, null, 100, 0);
        }
    }

    private static LeaseholdModificationEntity pendingChange(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdModificationMapper().selectActiveByRegion(regionId, WORLD_ID);
        }
    }

    // --- Rent ---

    @Test
    void aLettingThatWasNotPaidForLeavesNoTenantAndNothingInTheHistory() {
        String regionId = regionToLet();

        logic.rollbackRent(regionId, WORLD_ID, TENANT, let(regionId, TENANT));

        Assertions.assertNull(lease(regionId).tenantId());
        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.RENT));
    }

    @Test
    void aLettingIsRecordedWithTheTenantOnItsTerms() {
        String regionId = regionLet();

        List<LeaseholdHistoryEntity> lettings = recorded(regionId, HistoryEventType.RENT);
        Assertions.assertEquals(1, lettings.size());
        Assertions.assertEquals(TENANT, lettings.getFirst().tenantId());
        Assertions.assertEquals(new Party.Personal(LANDLORD), lettings.getFirst().landlord());
        Assertions.assertEquals(RENT, lettings.getFirst().price());
        Assertions.assertEquals(PERIOD_SECONDS, lettings.getFirst().durationSeconds());
    }

    @Test
    void undoingALettingLeavesAnEarlierLettingToTheSameTenantInTheHistory() {
        // They rented it, paid, and left. They came back and could not pay.
        String regionId = regionLet();
        end(regionId);

        logic.rollbackRent(regionId, WORLD_ID, TENANT, let(regionId, TENANT));

        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.RENT).size());
        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.UNRENT).size());
    }

    @Test
    void undoingALettingDoesNotEvictSomebodyWhoHasMovedInSince() {
        String regionId = regionToLet();
        RentResult.Success failed = let(regionId, TENANT);
        logic.rollbackRent(regionId, WORLD_ID, TENANT, failed);
        UUID someoneWithMoney = UUID.randomUUID();
        let(regionId, someoneWithMoney);

        logic.rollbackRent(regionId, WORLD_ID, TENANT, failed);

        Assertions.assertEquals(someoneWithMoney, lease(regionId).tenantId());
        List<LeaseholdHistoryEntity> lettings = recorded(regionId, HistoryEventType.RENT);
        Assertions.assertEquals(1, lettings.size());
        Assertions.assertEquals(someoneWithMoney, lettings.getFirst().tenantId());
    }

    // --- Renew ---

    @Test
    void aRenewalThatWasNotPaidForIsNotInTheHistory() {
        String regionId = regionLet();

        tryToRenewAndFailToPay(regionId);

        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.RENEW));
    }

    @Test
    void aRenewalThatWasNotPaidForLeavesTheLeaseAsItWas() {
        String regionId = regionLet();
        LeaseholdContractEntity before = lease(regionId);

        tryToRenewAndFailToPay(regionId);

        LeaseholdContractEntity after = lease(regionId);
        Assertions.assertEquals(before.endDate(), after.endDate());
        Assertions.assertEquals(before.currentMaxExtensions(), after.currentMaxExtensions());
    }

    @Test
    void aRenewalThatWasPaidForIsRecordedWithTheExtensionsLeftAfterIt() {
        String regionId = regionLet();

        renew(regionId);

        List<LeaseholdHistoryEntity> renewals = recorded(regionId, HistoryEventType.RENEW);
        Assertions.assertEquals(1, renewals.size());
        Assertions.assertEquals(MAX_EXTENSIONS - 1, renewals.getFirst().extensionsRemaining());
    }

    @Test
    void undoingAFailedRenewalLeavesTheRenewalsThatWerePaidFor() {
        String regionId = regionLet();
        renew(regionId);
        renew(regionId);

        tryToRenewAndFailToPay(regionId);

        List<Integer> left = recorded(regionId, HistoryEventType.RENEW).stream()
                .map(LeaseholdHistoryEntity::extensionsRemaining)
                .sorted()
                .toList();
        Assertions.assertEquals(List.of(MAX_EXTENSIONS - 2, MAX_EXTENSIONS - 1), left);
    }

    @Test
    void theRecordOfARenewalIsRemovedEvenIfTheTenantHasGone() {
        String regionId = regionLet();
        RenewLeaseholdResult.Success reserved = renew(regionId);
        logic.setTenant(regionId, WORLD_ID, null);

        logic.rollbackRenewLeasehold(regionId, WORLD_ID, TENANT, reserved);

        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.RENEW));
    }

    // --- Renew, with a change of terms falling due ---

    @Test
    void aChangeOfTermsIsNotAppliedByARenewalThatWasNotPaidFor() {
        // The landlord raised the rent from the next renewal. A tenant who renewed
        // without the money was left on the new rent for a period paid for at the old
        // one, and refunded at the new rent when they left.
        String regionId = regionLet();
        Assertions.assertInstanceOf(ProposeModificationResult.Success.class,
                logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, 1000.0, 172800L, 2));
        LeaseholdContractEntity before = lease(regionId);

        tryToRenewAndFailToPay(regionId);

        LeaseholdContractEntity after = lease(regionId);
        Assertions.assertEquals(RENT, after.price());
        Assertions.assertEquals(PERIOD_SECONDS, after.durationSeconds());
        Assertions.assertEquals(MAX_EXTENSIONS, after.maxExtensions());
        Assertions.assertEquals(before.currentMaxExtensions(), after.currentMaxExtensions());
        Assertions.assertEquals(before.endDate(), after.endDate());
    }

    @Test
    void aChangeOfTermsThatWasTakenBackIsPendingAgain() {
        String regionId = regionLet();
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, 1000.0, null, null);
        int proposed = pendingChange(regionId).modificationId();

        tryToRenewAndFailToPay(regionId);

        LeaseholdModificationEntity pending = pendingChange(regionId);
        Assertions.assertNotNull(pending, "the landlord's change was lost");
        Assertions.assertEquals(proposed, pending.modificationId());
        Assertions.assertNull(pending.resolvedAt());
        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.MODIFY_APPLY));
    }

    @Test
    void aChangeOfTermsTakenBackIsAppliedByTheNextRenewalThatIsPaidFor() {
        String regionId = regionLet();
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, 1000.0, null, null);
        tryToRenewAndFailToPay(regionId);

        RenewLeaseholdResult.Success paid = renew(regionId);

        Assertions.assertEquals(1000.0, paid.price());
        Assertions.assertEquals(1000.0, lease(regionId).price());
        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.MODIFY_APPLY).size());
        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.RENEW).size());
    }

    @Test
    void aChangeOfTermsIsNotLostIfTheLandlordProposesAnotherInTheMeantime() {
        // A rent rise was applied by a renewal that was then not paid for. Before it was
        // undone the landlord proposed a longer period. The rise was left marked as
        // applied, was never charged, and the record said it had taken effect.
        String regionId = regionLet();
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, 1000.0, null, null);
        RenewLeaseholdResult.Success reserved = renew(regionId);
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, null, 172800L, null);

        logic.rollbackRenewLeasehold(regionId, WORLD_ID, TENANT, reserved);

        Assertions.assertEquals(RENT, lease(regionId).price(), "not charged, so not yet in force");
        LeaseholdModificationEntity pending = pendingChange(regionId);
        Assertions.assertEquals(1000.0, pending.newPrice(), "the rise, carried forward");
        Assertions.assertEquals(172800L, pending.newDurationSeconds(), "the longer period");

        RenewLeaseholdResult.Success paid = renew(regionId);
        Assertions.assertEquals(1000.0, paid.price());
        Assertions.assertEquals(172800L, lease(regionId).durationSeconds());
    }

    @Test
    void aChangeOfTermsIsNotCarriedIntoOneFromTheOtherSide() {
        // The landlord's rise was applied, and before it was undone the tenant proposed
        // terms of their own. A proposal from the other side supersedes and takes nothing.
        String regionId = regionLet();
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, 1000.0, null, null);
        RenewLeaseholdResult.Success reserved = renew(regionId);
        logic.proposeModification(regionId, WORLD_ID, TENANT, false, null, 43200L, null);

        logic.rollbackRenewLeasehold(regionId, WORLD_ID, TENANT, reserved);

        LeaseholdModificationEntity pending = pendingChange(regionId);
        Assertions.assertNull(pending.newPrice());
        Assertions.assertEquals(43200L, pending.newDurationSeconds());
        Assertions.assertEquals(RENT, lease(regionId).price());
    }

    @Test
    void aChangeOfTermsAcceptedFromTheTenantIsTakenBackToo() {
        String regionId = regionLet();
        logic.proposeModification(regionId, WORLD_ID, TENANT, false, 150.0, null, null);
        logic.acceptModification(regionId, WORLD_ID, LANDLORD, false);

        tryToRenewAndFailToPay(regionId);

        Assertions.assertEquals(RENT, lease(regionId).price());
        Assertions.assertEquals(150.0, pendingChange(regionId).newPrice());
        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.MODIFY_APPLY));
    }

    @Test
    void aFailedRenewalDoesNotChangeWhatIsRefundedAfterwards() {
        String regionId = regionLet();
        double refundBefore = end(regionId).refund();
        String second = regionLet();
        logic.proposeModification(second, WORLD_ID, LANDLORD, false, 1000.0, 172800L, null);
        tryToRenewAndFailToPay(second);

        double refundAfter = end(second).refund();

        Assertions.assertEquals(refundBefore, refundAfter, 1.0,
                "refunded on terms the tenant never paid on");
    }

    // --- Unrent ---

    @Test
    void aTenancyWhoseRefundCouldNotBePaidIsNotRecordedAsEnded() {
        String regionId = regionLet();

        tryToUnrentAndFailToRefund(regionId);

        Assertions.assertEquals(List.of(), recorded(regionId, HistoryEventType.UNRENT));
    }

    @Test
    void aTenancyPutBackIsNotRecordedAsANewLetting() {
        // It used to be put back by letting the region to the tenant again.
        String regionId = regionLet();

        tryToUnrentAndFailToRefund(regionId);

        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.RENT).size());
    }

    @Test
    void aTenancyPutBackKeepsTheDatesAndExtensionsItHad() {
        // Three periods paid for ahead. Let again from now, the tenant was left with one
        // period and all five extensions.
        String regionId = regionLet();
        renew(regionId);
        renew(regionId);
        renew(regionId);
        LeaseholdContractEntity before = lease(regionId);

        tryToUnrentAndFailToRefund(regionId);

        LeaseholdContractEntity after = lease(regionId);
        Assertions.assertEquals(TENANT, after.tenantId());
        Assertions.assertEquals(before.startDate(), after.startDate());
        Assertions.assertEquals(before.endDate(), after.endDate());
        Assertions.assertEquals(3, after.currentMaxExtensions());
    }

    @Test
    void aTenancyIsPutBackEvenOnARegionNoLongerAcceptingTenants() {
        // The landlord closed the region to new tenants. This is not a new tenant. Let
        // again, they were refused, and lost the tenancy with no refund.
        String regionId = regionLet();
        logic.setRentable(regionId, WORLD_ID, LANDLORD, false, false);

        tryToUnrentAndFailToRefund(regionId);

        Assertions.assertEquals(TENANT, lease(regionId).tenantId());
    }

    @Test
    void aTenancyPutBackNeverHasMoreExtensionsUsedThanTheLeaseAllows() {
        // The landlord lowered the cap between the ending and its undoing.
        String regionId = regionLet();
        renew(regionId);
        renew(regionId);
        renew(regionId);
        UnrentResult.Success ended = end(regionId);
        logic.setMaxRenewals(regionId, WORLD_ID, 1);

        logic.rollbackUnrent(regionId, WORLD_ID, TENANT, ended);

        Assertions.assertEquals(1, lease(regionId).maxExtensions());
        Assertions.assertEquals(1, lease(regionId).currentMaxExtensions());
    }

    // --- A lease with no cap on extensions ---

    @Test
    void aLeaseWithNoCapIsPutBackWithNoCountOfExtensions() {
        // A count beside no cap is a row the database refuses.
        String regionId = "failed_tenancy_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, RENT, PERIOD_SECONDS, -1, new Party.Personal(LANDLORD)));
        let(regionId, TENANT);
        Assertions.assertNull(lease(regionId).maxExtensions(), "this test needs a lease with no cap");
        renew(regionId);
        LeaseholdContractEntity before = lease(regionId);

        tryToUnrentAndFailToRefund(regionId);
        tryToRenewAndFailToPay(regionId);

        LeaseholdContractEntity after = lease(regionId);
        Assertions.assertEquals(TENANT, after.tenantId());
        Assertions.assertNull(after.maxExtensions());
        Assertions.assertNull(after.currentMaxExtensions());
        Assertions.assertEquals(before.endDate(), after.endDate());
        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.RENEW).size());
    }

    @Test
    void aChangeOfTermsThatCappedALeaseLeavesItWithoutACapWhenTakenBack() {
        String regionId = "failed_tenancy_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createLeasehold(regionId, WORLD_ID, RENT, PERIOD_SECONDS, -1, new Party.Personal(LANDLORD)));
        let(regionId, TENANT);
        Assertions.assertNull(lease(regionId).maxExtensions(), "this test needs a lease with no cap");
        logic.proposeModification(regionId, WORLD_ID, LANDLORD, false, null, null, 3);

        tryToRenewAndFailToPay(regionId);

        Assertions.assertNull(lease(regionId).maxExtensions());
        Assertions.assertNull(lease(regionId).currentMaxExtensions());
    }

    @Test
    void undoingAnEndingDoesNotDisplaceATenantWhoHasMovedInSince() {
        String regionId = regionLet();
        UnrentResult.Success ended = end(regionId);
        UUID newTenant = UUID.randomUUID();
        let(regionId, newTenant);

        logic.rollbackUnrent(regionId, WORLD_ID, TENANT, ended);

        Assertions.assertEquals(newTenant, lease(regionId).tenantId());
        Assertions.assertEquals(1, recorded(regionId, HistoryEventType.UNRENT).size(),
                "the old tenancy did end, so the record of that stays");
    }

    @Test
    void aTenancyThatEndedWithItsRefundPaidIsRecordedOnce() {
        String regionId = regionLet();

        end(regionId);

        List<LeaseholdHistoryEntity> endings = recorded(regionId, HistoryEventType.UNRENT);
        Assertions.assertEquals(1, endings.size());
        Assertions.assertEquals(TENANT, endings.getFirst().tenantId());
        Assertions.assertNull(lease(regionId).tenantId());
    }
}
