package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPartiesTest {

    private static final Party GOV = Party.account(7, AccountKind.GOVERNMENT);
    private static final PartySetting GOV_SETTING = new PartySetting("GovSecurity", null, PartyFlag.GOVERNMENT);

    @Mock
    private PartyResolver resolver;

    private static Settings settings(PartySetting authority, PartySetting landlord, PartySetting titleholder) {
        return new Settings(authority, landlord, titleholder, new SimpleDateFormat("yyyy"),
                0, 0, 0, 0, List.of(), null, 0, 0, 0, 0, null);
    }

    private void govResolves() {
        when(resolver.resolve("GovSecurity", PartyFlag.GOVERNMENT, null))
                .thenReturn(new PartyResolver.Resolution.Resolved(GOV));
    }

    @Test
    void everythingResolves_noErrors() {
        govResolves();
        UUID steve = UUID.randomUUID();
        when(resolver.resolve("Steve", null, null))
                .thenReturn(new PartyResolver.Resolution.Resolved(Party.personal(steve)));

        DefaultParties parties = DefaultParties.resolve(
                settings(GOV_SETTING, GOV_SETTING, new PartySetting("Steve", null, null)), resolver);

        assertEquals(GOV, parties.freeholdAuthority());
        assertEquals(GOV, parties.leaseholdLandlord());
        assertEquals(steve, parties.freeholdTitleholder());
        assertFalse(parties.freeholdTitleholderUnresolved());
        assertTrue(parties.errors().isEmpty());
    }

    @Test
    void unknownAccount_isNullAndReported() {
        govResolves();
        when(resolver.resolve("Nowhere", PartyFlag.GOVERNMENT, null))
                .thenReturn(new PartyResolver.Resolution.Refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, "Nowhere"));

        DefaultParties parties = DefaultParties.resolve(
                settings(GOV_SETTING, new PartySetting("Nowhere", null, PartyFlag.GOVERNMENT), null), resolver);

        assertEquals(GOV, parties.freeholdAuthority());
        assertNull(parties.leaseholdLandlord());
        assertEquals(1, parties.errors().size());
        assertTrue(parties.errors().getFirst().contains("default-leasehold-landlord"));
    }

    @Test
    void playerByUuid_needsNoLookup() {
        UUID steve = UUID.randomUUID();
        PartySetting byUuid = new PartySetting(null, steve, null);

        DefaultParties parties = DefaultParties.resolve(settings(byUuid, byUuid, byUuid), resolver);

        assertEquals(Party.personal(steve), parties.freeholdAuthority());
        assertEquals(Party.personal(steve), parties.leaseholdLandlord());
        assertEquals(steve, parties.freeholdTitleholder());
        assertTrue(parties.errors().isEmpty());
        verifyNoInteractions(resolver);
    }

    @Test
    void titleholderWithAType_isAnError() {
        govResolves();

        DefaultParties parties = DefaultParties.resolve(settings(GOV_SETTING, GOV_SETTING, GOV_SETTING), resolver);

        assertNull(parties.freeholdTitleholder());
        assertTrue(parties.freeholdTitleholderUnresolved());
        assertEquals(1, parties.errors().size());
        assertTrue(parties.errors().getFirst().contains("default-freehold-titleholder"));
    }

    @Test
    void missingTitleholder_isNotAnError() {
        govResolves();

        DefaultParties parties = DefaultParties.resolve(settings(GOV_SETTING, GOV_SETTING, null), resolver);

        assertNull(parties.freeholdTitleholder());
        assertFalse(parties.freeholdTitleholderUnresolved());
        assertTrue(parties.errors().isEmpty());
    }

    @Test
    void unknownTitleholder_isUnresolvedNotAbsent() {
        govResolves();
        when(resolver.resolve("Nobody", null, null))
                .thenReturn(new PartyResolver.Resolution.Refused(MessageKeys.COMMON_PLAYER_NOT_FOUND, "Nobody"));

        DefaultParties parties = DefaultParties.resolve(
                settings(GOV_SETTING, GOV_SETTING, new PartySetting("Nobody", null, null)), resolver);

        assertNull(parties.freeholdTitleholder());
        assertTrue(parties.freeholdTitleholderUnresolved());
        assertEquals(1, parties.errors().size());
        assertTrue(parties.errors().getFirst().contains("default-freehold-titleholder"));
    }

    @Test
    void beforeTheFirstResolve_theTitleholderIsUnresolved() {
        assertTrue(DefaultParties.unresolved().freeholdTitleholderUnresolved());
    }

    @Test
    void missingAuthority_isReported() {
        govResolves();

        DefaultParties parties = DefaultParties.resolve(settings(null, GOV_SETTING, null), resolver);

        assertNull(parties.freeholdAuthority());
        assertEquals(1, parties.errors().size());
        assertTrue(parties.errors().getFirst().contains("default-freehold-authority"));
    }

    @Test
    void businessAccountWithoutAnId_isReportedWithoutALookup() {
        PartySetting business = new PartySetting("Acme", null, PartyFlag.BUSINESS);

        DefaultParties parties = DefaultParties.resolve(settings(business, business, null), resolver);

        assertNull(parties.freeholdAuthority());
        assertNull(parties.leaseholdLandlord());
        assertEquals(2, parties.errors().size());
        verifyNoInteractions(resolver);
    }
}
