package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Every event that carries a landlord now exposes it as a {@link Party}, alongside the
 * deprecated player-UUID getter kept for source compatibility. Tenant stays a player in
 * stage 1, but also gains a {@link Party}-typed getter for a uniform surface.
 */
class PartyGettersTest {

    // The events under test never touch the region themselves, so a bare, unmocked
    // instance is enough to satisfy the RealtyRegionEvent constructor.
    private static final WorldGuardRegion REGION = new WorldGuardRegion(null, null);

    @Test
    void playerLandlord_bothGettersAgree() {
        UUID landlordId = UUID.randomUUID();
        Party landlord = new Party.Personal(landlordId);
        RegionRentedEvent event = new RegionRentedEvent(REGION, UUID.randomUUID(), landlord, 10.0, 60L);

        Assertions.assertEquals(landlord, event.getLandlord());
        Assertions.assertEquals(landlordId, event.getLandlordId());
    }

    @Test
    void accountLandlord_deprecatedGetterIsNull() {
        Party landlord = new Party.Account(42, AccountKind.GOVERNMENT);
        RegionRentedEvent event = new RegionRentedEvent(REGION, UUID.randomUUID(), landlord, 10.0, 60L);

        Assertions.assertEquals(landlord, event.getLandlord());
        Assertions.assertNull(event.getLandlordId());
    }

    @Test
    void tenantIsAlwaysAPlayerParty() {
        UUID tenantId = UUID.randomUUID();
        RegionRentedEvent event = new RegionRentedEvent(
                REGION, tenantId, new Party.Personal(UUID.randomUUID()), 10.0, 60L);

        Assertions.assertEquals(new Party.Personal(tenantId), event.getTenant());
        Assertions.assertEquals(tenantId, event.getTenantId());
    }
}
