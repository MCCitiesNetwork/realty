package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.command.util.SetRouting.Kind;
import io.github.md5sha256.realty.command.util.SetRouting.Outcome;
import io.github.md5sha256.realty.command.util.SetRouting.Refusal;
import io.github.md5sha256.realty.command.util.SetRouting.Tenure;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One place decides whether a /realty set change applies now, waits for a renewal, becomes a
 * request, or is refused.
 */
class SetRoutingTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final Party AUTHORITY = Party.personal(UUID.randomUUID());
    private static final Party ACCOUNT = Party.account(7, AccountKind.BUSINESS);

    private static LeaseholdContractEntity lease(Party landlord, UUID tenant, LocalDateTime termination) {
        return new LeaseholdContractEntity(1, landlord, tenant, 100.0, 3600L, null, null,
                null, null, termination, null, true);
    }

    private static LeaseholdContractEntity lease(UUID tenant, LocalDateTime termination) {
        return lease(Party.personal(LANDLORD), tenant, termination);
    }

    private static FreeholdContractEntity freehold(UUID titleHolder) {
        return new FreeholdContractEntity(1, AUTHORITY, titleHolder, null, false);
    }

    private static Outcome applyNow(boolean vacantOnly) {
        return new Outcome.ApplyNow(vacantOnly);
    }

    private static Outcome refused(Refusal reason) {
        return new Outcome.Refused(reason);
    }

    private static Arguments row(Tenure tenure, Kind kind, boolean tenant, boolean holds, boolean mayNow,
                                 boolean flag, boolean unlimited, boolean canPropose, Outcome expected) {
        return Arguments.of(tenure, kind, tenant, holds, mayNow, flag, unlimited, canPropose, expected);
    }

    static Stream<Arguments> decisions() {
        Outcome schedule = new Outcome.Schedule();
        Outcome request = new Outcome.Request();
        Tenure none = Tenure.NONE;
        Tenure free = Tenure.FREEHOLD;
        Tenure vacant = Tenure.VACANT_LEASE;
        Tenure rented = Tenure.RENTED_LEASE;
        Tenure ending = Tenure.ENDING_LEASE;
        Kind term = Kind.TERM;
        Kind holder = Kind.HOLDER;
        return Stream.of(
                row(none, term, false, false, false, false, false, true, applyNow(true)),
                row(free, term, false, true, false, false, false, true, applyNow(true)),
                row(free, term, false, false, false, false, false, true, refused(Refusal.NOT_HOLDER)),
                row(free, holder, false, true, false, false, false, true, applyNow(true)),
                row(vacant, term, false, true, false, false, true, true, applyNow(true)),
                row(vacant, term, false, true, false, true, false, true, applyNow(true)),
                row(vacant, term, false, true, true, true, false, true, applyNow(false)),
                row(vacant, holder, false, false, false, false, false, true, refused(Refusal.NOT_HOLDER)),
                row(rented, term, false, true, false, false, false, true, schedule),
                row(rented, term, false, true, false, false, false, false, refused(Refusal.CONSOLE_NEEDS_NOW)),
                row(rented, term, true, false, false, false, false, true, request),
                row(rented, term, true, true, true, false, false, true, request),
                row(rented, term, true, true, true, true, false, true, applyNow(false)),
                row(rented, term, true, false, true, true, false, true, refused(Refusal.NOT_HOLDER)),
                row(rented, term, false, false, false, false, false, true, refused(Refusal.NOT_HOLDER)),
                row(rented, term, false, true, false, false, true, true, refused(Refusal.UNLIMITED_NEEDS_NOW)),
                row(rented, term, false, true, true, true, true, true, applyNow(false)),
                row(rented, term, false, true, false, true, false, true, refused(Refusal.NOW_NOT_PERMITTED)),
                row(rented, term, false, true, true, true, false, false, applyNow(false)),
                row(rented, holder, false, true, true, false, false, true, refused(Refusal.NEEDS_NOW)),
                row(rented, holder, false, true, false, false, false, true, refused(Refusal.NOW_NOT_PERMITTED)),
                row(rented, holder, false, true, true, true, false, true, applyNow(false)),
                row(rented, holder, true, false, true, false, false, true, refused(Refusal.NOT_HOLDER)),
                row(ending, term, false, true, false, false, false, true, refused(Refusal.LEASE_ENDING)),
                row(ending, term, true, false, false, false, false, true, refused(Refusal.LEASE_ENDING)),
                row(ending, term, false, true, true, true, false, true, applyNow(false)),
                row(ending, holder, false, true, true, false, false, true, refused(Refusal.NEEDS_NOW))
        );
    }

    @ParameterizedTest
    @MethodSource("decisions")
    void decide(Tenure tenure, Kind kind, boolean tenant, boolean holds, boolean mayNow,
                boolean flag, boolean unlimited, boolean canPropose, Outcome expected) {
        Assertions.assertEquals(expected,
                SetRouting.decide(tenure, kind, tenant, holds, mayNow, flag, unlimited, canPropose));
    }

    @Test
    void tenureOfLeaseWithoutTenantIsVacant() {
        Assertions.assertEquals(Tenure.VACANT_LEASE, SetRouting.tenureOf(null, lease(null, null)));
    }

    @Test
    void tenureOfLeaseWithTenantIsRented() {
        Assertions.assertEquals(Tenure.RENTED_LEASE, SetRouting.tenureOf(null, lease(TENANT, null)));
    }

    @Test
    void tenureOfLeaseWithTerminationDateIsEnding() {
        Assertions.assertEquals(Tenure.ENDING_LEASE,
                SetRouting.tenureOf(null, lease(TENANT, LocalDateTime.now())));
    }

    @Test
    void tenureOfFreehold() {
        Assertions.assertEquals(Tenure.FREEHOLD, SetRouting.tenureOf(freehold(TITLE_HOLDER), null));
    }

    @Test
    void tenureOfNothingIsNone() {
        Assertions.assertEquals(Tenure.NONE, SetRouting.tenureOf(null, null));
    }

    @Test
    void holderOfLeaseIsItsLandlord() {
        Assertions.assertEquals(Party.personal(LANDLORD),
                SetRouting.holderOf(freehold(TITLE_HOLDER), lease(TENANT, null)));
    }

    @Test
    void holderOfSoldFreeholdIsItsTitleHolder() {
        Assertions.assertEquals(Party.personal(TITLE_HOLDER),
                SetRouting.holderOf(freehold(TITLE_HOLDER), null));
    }

    @Test
    void holderOfUnsoldFreeholdIsItsAuthority() {
        Assertions.assertEquals(AUTHORITY, SetRouting.holderOf(freehold(null), null));
    }

    @Test
    void holderOfNothingIsNull() {
        Assertions.assertNull(SetRouting.holderOf(null, null));
    }

    @Test
    void tenantIsTheTenant() {
        Assertions.assertTrue(SetRouting.isTenant(ActorContext.player(TENANT, false), lease(TENANT, null)));
        Assertions.assertFalse(SetRouting.isTenant(ActorContext.player(LANDLORD, false), lease(TENANT, null)));
    }

    @Test
    void consoleIsNeverTheTenant() {
        Assertions.assertFalse(SetRouting.isTenant(ActorContext.console(), lease(TENANT, null)));
        Assertions.assertFalse(SetRouting.isTenant(ActorContext.console(), lease(null, null)));
    }

    @Test
    void playerCanAlwaysPropose() {
        Assertions.assertTrue(SetRouting.canPropose(ActorContext.player(TENANT, false),
                lease(ACCOUNT, TENANT, null)));
    }

    @Test
    void consoleCanProposeForAPlayerLandlord() {
        Assertions.assertTrue(SetRouting.canPropose(ActorContext.console(), lease(TENANT, null)));
    }

    @Test
    void consoleCannotProposeForAnAccountLandlord() {
        Assertions.assertFalse(SetRouting.canPropose(ActorContext.console(),
                lease(ACCOUNT, TENANT, null)));
    }
}
