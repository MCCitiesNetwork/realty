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
// The deprecated getters are called on purpose: they must agree with the new ones.
@SuppressWarnings("removal")
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

    @Test
    void tenantEvents_exposeTheTenantAsAPlayerParty() {
        UUID tenantId = UUID.randomUUID();
        Party tenant = new Party.Personal(tenantId);

        LeaseExtendEvent extend = new LeaseExtendEvent(REGION, tenantId);
        LeaseExtendedEvent extended = new LeaseExtendedEvent(REGION, tenantId, 10.0);
        RegionRentEvent rent = new RegionRentEvent(REGION, tenantId);
        RegionUnrentEvent unrent = new RegionUnrentEvent(REGION, tenantId);

        Assertions.assertEquals(tenant, extend.getTenant());
        Assertions.assertEquals(tenantId, extend.getTenantId());
        Assertions.assertEquals(tenant, extended.getTenant());
        Assertions.assertEquals(tenantId, extended.getTenantId());
        Assertions.assertEquals(tenant, rent.getTenant());
        Assertions.assertEquals(tenantId, rent.getTenantId());
        Assertions.assertEquals(tenant, unrent.getTenant());
        Assertions.assertEquals(tenantId, unrent.getTenantId());
    }

    @Test
    void tenantSet_exposesNewAndPreviousTenant_andNoneAsNull() {
        UUID newTenantId = UUID.randomUUID();
        UUID previousTenantId = UUID.randomUUID();
        Party landlord = new Party.Account(42, AccountKind.GOVERNMENT);

        TenantSetEvent replaced = new TenantSetEvent(REGION, newTenantId, previousTenantId, landlord);
        TenantSetEvent cleared = new TenantSetEvent(REGION, null, previousTenantId, landlord);
        TenantSetEvent first = new TenantSetEvent(REGION, newTenantId, null, landlord);

        Assertions.assertEquals(new Party.Personal(newTenantId), replaced.getNewTenant());
        Assertions.assertEquals(newTenantId, replaced.getNewTenantId());
        Assertions.assertEquals(new Party.Personal(previousTenantId), replaced.getPreviousTenant());
        Assertions.assertEquals(previousTenantId, replaced.getPreviousTenantId());
        Assertions.assertNull(cleared.getNewTenant());
        Assertions.assertNull(first.getPreviousTenant());
    }

    @Test
    void purchaseEvents_exposeNewAndPreviousTitleHolder_andNoneAsNull() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Party buyer = new Party.Personal(buyerId);
        Party seller = new Party.Personal(sellerId);

        RegionBoughtEvent bought = new RegionBoughtEvent(REGION, buyerId, sellerId, 10.0);
        AuctionWonPurchaseEvent won = new AuctionWonPurchaseEvent(REGION, buyerId, sellerId, 10.0);
        OfferPurchaseCompletedEvent completed = new OfferPurchaseCompletedEvent(REGION, buyerId, sellerId, 10.0);

        Assertions.assertEquals(buyer, bought.getNewTitleHolder());
        Assertions.assertEquals(buyerId, bought.getBuyerId());
        Assertions.assertEquals(seller, bought.getPreviousTitleHolder());
        Assertions.assertEquals(sellerId, bought.getPreviousTitleHolderId());
        Assertions.assertEquals(buyer, won.getNewTitleHolder());
        Assertions.assertEquals(buyerId, won.getWinnerId());
        Assertions.assertEquals(seller, won.getPreviousTitleHolder());
        Assertions.assertEquals(sellerId, won.getPreviousTitleHolderId());
        Assertions.assertEquals(buyer, completed.getNewTitleHolder());
        Assertions.assertEquals(buyerId, completed.getOffererId());
        Assertions.assertEquals(seller, completed.getPreviousTitleHolder());
        Assertions.assertEquals(sellerId, completed.getPreviousTitleHolderId());

        Assertions.assertNull(new RegionBoughtEvent(REGION, buyerId, null, 10.0).getPreviousTitleHolder());
        Assertions.assertNull(new AuctionWonPurchaseEvent(REGION, buyerId, null, 10.0).getPreviousTitleHolder());
        Assertions.assertNull(new OfferPurchaseCompletedEvent(REGION, buyerId, null, 10.0).getPreviousTitleHolder());
    }

    @Test
    void offerEvents_exposeTheTitleHolder_andNoneAsNull() {
        UUID offererId = UUID.randomUUID();
        UUID titleHolderId = UUID.randomUUID();
        Party titleHolder = new Party.Personal(titleHolderId);

        OfferPlacedEvent placed = new OfferPlacedEvent(REGION, offererId, titleHolderId, 10.0);
        OfferWithdrawnEvent withdrawn = new OfferWithdrawnEvent(REGION, offererId, titleHolderId);

        Assertions.assertEquals(titleHolder, placed.getTitleHolder());
        Assertions.assertEquals(titleHolderId, placed.getTitleHolderId());
        Assertions.assertEquals(titleHolder, withdrawn.getTitleHolder());
        Assertions.assertEquals(titleHolderId, withdrawn.getTitleHolderId());
        Assertions.assertNull(new OfferPlacedEvent(REGION, offererId, null, 10.0).getTitleHolder());
        Assertions.assertNull(new OfferWithdrawnEvent(REGION, offererId, null).getTitleHolder());
    }
}
