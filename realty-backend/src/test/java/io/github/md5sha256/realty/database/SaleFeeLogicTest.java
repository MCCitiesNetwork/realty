package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.PartyKind;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptAgentInviteResult;
import io.github.md5sha256.realty.api.RealtyBackend.AcceptOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.BidResult;
import io.github.md5sha256.realty.api.RealtyBackend.CreateAuctionResult;
import io.github.md5sha256.realty.api.RealtyBackend.FeeListing;
import io.github.md5sha256.realty.api.RealtyBackend.InviteAgentResult;
import io.github.md5sha256.realty.api.RealtyBackend.OfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayBidResult;
import io.github.md5sha256.realty.api.RealtyBackend.PayOfferResult;
import io.github.md5sha256.realty.api.RealtyBackend.RemoveFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.ResolveFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetFeeResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetLevyResult;
import io.github.md5sha256.realty.api.RealtyBackend.SetTitleHolderResult;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

class SaleFeeLogicTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID AGENT = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID NEW_HOLDER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();

    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);
    private static final ActorContext MANAGER = new ActorContext(MANAGER_ID, Set.of(GOV), Set.of(), false);

    private static final LevyDefinition DEFAULT_LEVY = levy("national-levy", true,
            List.of(new LevyTier(0, 2), new LevyTier(5, 8)));
    private static final LevyDefinition TOWN_LEVY = levy("town-levy", false, List.of(new LevyTier(0, 5)));
    private static final LevyDefinition REGION_LEVY = levy("region-levy", false, List.of(new LevyTier(0, 1.5)));

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static LevyDefinition levy(String name, boolean isDefault, List<LevyTier> tiers) {
        return levy(name, isDefault, tiers, null);
    }

    private static LevyDefinition levy(String name, boolean isDefault, List<LevyTier> tiers, PartyKind authorityKind) {
        return new LevyDefinition(name, true, isDefault, FeeSide.BUYER, null, authorityKind,
                List.of(new LevyRule(Set.of(), Set.of(), tiers)));
    }

    /** A catalog over the given levies, matching names case-insensitively. */
    private static LevyCatalog catalog(LevyDefinition... definitions) {
        Map<String, LevyDefinition> byName = java.util.Arrays.stream(definitions)
                .collect(Collectors.toMap(d -> d.name().toLowerCase(Locale.ROOT), Function.identity()));
        List<LevyDefinition> all = List.of(definitions);
        return new LevyCatalog() {
            @Override
            public @NotNull Optional<LevyDefinition> find(@NotNull String name) {
                return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
            }

            @Override
            public @NotNull Optional<LevyDefinition> defaultLevy() {
                return all.stream().filter(LevyDefinition::isDefault).findFirst();
            }

            @Override
            public @NotNull List<LevyDefinition> all() {
                return all;
            }
        };
    }

    private static String freehold(UUID titleHolder) {
        String regionId = "fee_region_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 1000.0, GOV, titleHolder));
        return regionId;
    }

    private static void makeAgent(String regionId, UUID agent) {
        Assertions.assertInstanceOf(InviteAgentResult.Success.class,
                logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, ActorContext.player(agent, false), false));
        Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class,
                logic.acceptAgentInvite(regionId, WORLD_ID, ActorContext.player(agent, false), false));
    }

    private static ActorContext player(UUID id) {
        return ActorContext.player(id, false);
    }

    private static FeeListing feeTo(String regionId, Party recipient) {
        return logic.listFees(regionId, WORLD_ID).stream()
                .filter(listing -> recipient.equals(listing.recipient()) && listing.role() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no fee to " + recipient));
    }

    private static int realtyRegionId(String regionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().selectByWorldGuardRegion(regionId, WORLD_ID).realtyRegionId();
        }
    }

    @Test
    void titleholder_setsSellerFee_approvedAtOnce() {
        String regionId = freehold(TITLE_HOLDER);

        SetFeeResult result = logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);

        SetFeeResult.Success success = Assertions.assertInstanceOf(SetFeeResult.Success.class, result);
        Assertions.assertEquals(Party.personal(TITLE_HOLDER), success.recipient());
        Assertions.assertFalse(success.needsApproval());
        Assertions.assertNull(success.previousPercent());
        FeeListing listing = feeTo(regionId, Party.personal(TITLE_HOLDER));
        Assertions.assertTrue(listing.approved());
        Assertions.assertEquals("titleholder", listing.role());
        Assertions.assertEquals("3%", listing.rate());
        Assertions.assertEquals(FeeSide.SELLER, listing.side());
    }

    @Test
    void agent_setsSellerFee_needsApproval_thenApprove() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);

        SetFeeResult result = logic.setFee(regionId, WORLD_ID, player(AGENT), 2.5, FeeSide.SELLER, false);

        SetFeeResult.Success success = Assertions.assertInstanceOf(SetFeeResult.Success.class, result);
        Assertions.assertTrue(success.needsApproval());
        FeeListing pending = feeTo(regionId, Party.personal(AGENT));
        Assertions.assertFalse(pending.approved());
        Assertions.assertEquals("agent", pending.role());
        Assertions.assertEquals("2.5%", pending.rate());

        Assertions.assertInstanceOf(ResolveFeeResult.NotSeller.class,
                logic.approveFee(regionId, WORLD_ID, player(AGENT), Party.personal(AGENT)));
        ResolveFeeResult approved = logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT));
        ResolveFeeResult.Success resolved = Assertions.assertInstanceOf(ResolveFeeResult.Success.class, approved);
        Assertions.assertEquals(FeeSide.SELLER, resolved.side());
        Assertions.assertEquals(2.5, resolved.percent());
        Assertions.assertTrue(feeTo(regionId, Party.personal(AGENT)).approved());
        Assertions.assertInstanceOf(ResolveFeeResult.NoPendingFee.class,
                logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT)));
    }

    @Test
    void rejectFee_deletesIt() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.setFee(regionId, WORLD_ID, player(AGENT), 4, FeeSide.SELLER, false);

        Assertions.assertInstanceOf(ResolveFeeResult.Success.class,
                logic.rejectFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT)));

        Assertions.assertTrue(logic.listFees(regionId, WORLD_ID).isEmpty());
    }

    @Test
    void authorityManager_asAuthority_recipientIsAuthority() {
        String regionId = freehold(null);

        SetFeeResult result = logic.setFee(regionId, WORLD_ID, MANAGER, 5, FeeSide.SELLER, true);

        SetFeeResult.Success success = Assertions.assertInstanceOf(SetFeeResult.Success.class, result);
        Assertions.assertEquals(GOV, success.recipient());
        Assertions.assertFalse(success.needsApproval());
        Assertions.assertEquals("authority", feeTo(regionId, GOV).role());
        Assertions.assertInstanceOf(SetFeeResult.NotAuthorityManager.class,
                logic.setFee(regionId, WORLD_ID, player(STRANGER), 5, FeeSide.SELLER, true));
        Assertions.assertInstanceOf(RemoveFeeResult.NotAuthorityManager.class,
                logic.removeFee(regionId, WORLD_ID, player(STRANGER), true));
        Assertions.assertInstanceOf(RemoveFeeResult.Success.class,
                logic.removeFee(regionId, WORLD_ID, MANAGER, true));
        Assertions.assertInstanceOf(RemoveFeeResult.NoFee.class,
                logic.removeFee(regionId, WORLD_ID, MANAGER, true));
    }

    @Test
    void stranger_notAllowed() {
        String regionId = freehold(TITLE_HOLDER);

        Assertions.assertInstanceOf(SetFeeResult.NotAllowed.class,
                logic.setFee(regionId, WORLD_ID, player(STRANGER), 3, FeeSide.BUYER, false));
        Assertions.assertInstanceOf(SetFeeResult.NoFreehold.class,
                logic.setFee("no_such_region", WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.BUYER, false));
        Assertions.assertTrue(logic.listFees(regionId, WORLD_ID).isEmpty());
    }

    @Test
    void setTwice_replaces_reportsPrevious() {
        String regionId = freehold(TITLE_HOLDER);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);

        SetFeeResult result = logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 4, FeeSide.BUYER, false);

        SetFeeResult.Success success = Assertions.assertInstanceOf(SetFeeResult.Success.class, result);
        Assertions.assertEquals(3.0, success.previousPercent());
        List<FeeListing> fees = logic.listFees(regionId, WORLD_ID);
        Assertions.assertEquals(1, fees.size());
        Assertions.assertEquals("4%", fees.getFirst().rate());
        Assertions.assertEquals(FeeSide.BUYER, fees.getFirst().side());
    }

    @Test
    void sameAgain_noChange() {
        String regionId = freehold(TITLE_HOLDER);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);

        Assertions.assertInstanceOf(SetFeeResult.NoChange.class,
                logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false));
    }

    @Test
    void removeAgent_deletesTheirFee() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.BUYER, false);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);

        Assertions.assertEquals(1, logic.removeSanctionedAuctioneer(regionId, WORLD_ID, AGENT, TITLE_HOLDER));

        List<FeeListing> fees = logic.listFees(regionId, WORLD_ID);
        Assertions.assertEquals(1, fees.size());
        Assertions.assertEquals(Party.personal(TITLE_HOLDER), fees.getFirst().recipient());
    }

    @Test
    void titleChange_deletesOldTitleholderFee_unapprovesOtherSellerFees() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);
        logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false);
        logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT));
        logic.setFee(regionId, WORLD_ID, MANAGER, 1, FeeSide.BUYER, true);
        Assertions.assertTrue(feeTo(regionId, Party.personal(AGENT)).approved());

        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.setTitleHolder(regionId, WORLD_ID, NEW_HOLDER, player(TITLE_HOLDER)));

        List<FeeListing> fees = logic.listFees(regionId, WORLD_ID);
        Assertions.assertEquals(2, fees.size());
        Assertions.assertTrue(fees.stream().noneMatch(f -> Party.personal(TITLE_HOLDER).equals(f.recipient())));
        Assertions.assertFalse(feeTo(regionId, Party.personal(AGENT)).approved());
        Assertions.assertTrue(feeTo(regionId, GOV).approved(), "a buyer-paid fee needs no approval");
    }

    @Test
    void transferTitle_toNobody_keepsAuthorityFeesApproved() {
        String regionId = freehold(TITLE_HOLDER);
        logic.setFee(regionId, WORLD_ID, MANAGER, 1, FeeSide.SELLER, true);
        logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), GOV);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);

        Assertions.assertInstanceOf(SetTitleHolderResult.Success.class,
                logic.transferTitleHolder(regionId, WORLD_ID, null));

        List<FeeListing> fees = logic.listFees(regionId, WORLD_ID);
        Assertions.assertEquals(1, fees.size());
        Assertions.assertTrue(feeTo(regionId, GOV).approved(), "the authority is now the seller");
    }

    @Test
    void levyFallback_regionThenAuthorityThenDefault() {
        levies = catalog(DEFAULT_LEVY, TOWN_LEVY, REGION_LEVY);
        String regionId = freehold(TITLE_HOLDER);

        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));
        FeeListing levyListing = logic.listFees(regionId, WORLD_ID).getFirst();
        Assertions.assertEquals("national-levy", levyListing.source());
        Assertions.assertEquals("2–8% by plots held", levyListing.rate());
        Assertions.assertNull(levyListing.recipient());
        Assertions.assertNull(levyListing.role());

        SetLevyResult authority = logic.setAuthorityLevy(GOV, MANAGER, "Town-Levy");
        Assertions.assertEquals(new SetLevyResult.Success(null), authority);
        Assertions.assertEquals("town-levy", logic.chosenLevy(regionId, WORLD_ID));
        Assertions.assertEquals("5%", logic.listFees(regionId, WORLD_ID).getFirst().rate());
        Assertions.assertInstanceOf(SetLevyResult.NoChange.class, logic.setAuthorityLevy(GOV, MANAGER, "town-levy"));

        Assertions.assertEquals(new SetLevyResult.Success(null),
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "region-levy"));
        Assertions.assertEquals("region-levy", logic.chosenLevy(regionId, WORLD_ID));
        Assertions.assertEquals("1.5%", logic.listFees(regionId, WORLD_ID).getFirst().rate());

        Assertions.assertEquals(new SetLevyResult.Success("region-levy"),
                logic.clearRegionLevy(regionId, WORLD_ID, MANAGER));
        Assertions.assertEquals("town-levy", logic.chosenLevy(regionId, WORLD_ID));
        Assertions.assertInstanceOf(SetLevyResult.NoChange.class, logic.clearRegionLevy(regionId, WORLD_ID, MANAGER));

        Assertions.assertEquals(new SetLevyResult.Success("town-levy"), logic.clearAuthorityLevy(GOV, MANAGER));
        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));

        Assertions.assertInstanceOf(SetLevyResult.NotAuthorityManager.class,
                logic.setRegionLevy(regionId, WORLD_ID, player(TITLE_HOLDER), "region-levy"));
        Assertions.assertInstanceOf(SetLevyResult.NotAuthorityManager.class,
                logic.setAuthorityLevy(GOV, player(STRANGER), "town-levy"));
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setRegionLevy(regionId, WORLD_ID, ActorContext.console(), "region-levy"));
        Assertions.assertInstanceOf(SetLevyResult.NoFreehold.class,
                logic.setRegionLevy("no_such_region", WORLD_ID, MANAGER, "region-levy"));
        Assertions.assertEquals(List.of(DEFAULT_LEVY, TOWN_LEVY, REGION_LEVY), logic.listLevies());
    }

    @Test
    void unknownLevy_refused() {
        levies = catalog(DEFAULT_LEVY);
        String regionId = freehold(TITLE_HOLDER);

        Assertions.assertInstanceOf(SetLevyResult.UnknownLevy.class,
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "nope"));
        Assertions.assertInstanceOf(SetLevyResult.UnknownLevy.class,
                logic.setAuthorityLevy(GOV, MANAGER, "nope"));
        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));
    }

    @Test
    void removedLevyChoice_ignoredButKept() {
        levies = catalog(DEFAULT_LEVY, REGION_LEVY);
        String regionId = freehold(TITLE_HOLDER);
        logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "region-levy");
        Assertions.assertEquals("region-levy", logic.chosenLevy(regionId, WORLD_ID));

        levies = catalog(DEFAULT_LEVY);

        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Assertions.assertEquals("region-levy",
                    wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId(regionId)));
        }
    }

    /**
     * A titleholder's seller fee, an approved agent's seller fee and an approved seller fee paid
     * to the authority, ready for a sale to clean up.
     */
    private static String freeholdWithSellerFees() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.setFee(regionId, WORLD_ID, player(TITLE_HOLDER), 3, FeeSide.SELLER, false);
        logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false);
        logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), Party.personal(AGENT));
        logic.setFee(regionId, WORLD_ID, MANAGER, 1, FeeSide.SELLER, true);
        logic.approveFee(regionId, WORLD_ID, player(TITLE_HOLDER), GOV);
        Assertions.assertEquals(3, logic.listFees(regionId, WORLD_ID).stream().filter(FeeListing::approved).count());
        return regionId;
    }

    private static void assertSaleCleanedUp(String regionId) {
        List<FeeListing> fees = logic.listFees(regionId, WORLD_ID);
        Assertions.assertEquals(1, fees.size(), "the titleholder's and the agent's fees are gone");
        Assertions.assertEquals(GOV, fees.getFirst().recipient());
        Assertions.assertFalse(fees.getFirst().approved(), "the new seller has not approved it");
    }

    @Test
    void offerCompletion_deletesAgentAndOldTitleholderFees_unapprovesOtherSellerFees() {
        String regionId = freeholdWithSellerFees();
        openToOffers(regionId, WORLD_ID);
        Assertions.assertInstanceOf(OfferResult.Success.class,
                logic.placeOffer(regionId, WORLD_ID, player(BUYER), 500.0, false));
        Assertions.assertInstanceOf(AcceptOfferResult.Success.class,
                logic.acceptOffer(regionId, WORLD_ID, player(TITLE_HOLDER), BUYER));
        Assertions.assertInstanceOf(PayOfferResult.FullyPaid.class, logic.payOffer(regionId, WORLD_ID, BUYER, 500.0));

        logic.finalizeOfferPurchase(regionId, WORLD_ID, BUYER);

        Assertions.assertEquals(BUYER, logic.getFreeholdContract(regionId, WORLD_ID).titleHolderId());
        assertSaleCleanedUp(regionId);
    }

    @Test
    void auctionFinalise_deletesAgentAndOldTitleholderFees_unapprovesOtherSellerFees() {
        String regionId = freeholdWithSellerFees();
        Assertions.assertInstanceOf(CreateAuctionResult.Success.class,
                logic.createAuction(regionId, WORLD_ID, player(TITLE_HOLDER), 3600, 3600, 100.0, 10.0));
        Assertions.assertInstanceOf(BidResult.Success.class,
                logic.performBid(regionId, WORLD_ID, player(BUYER), 500.0, false));
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.freeholdContractBidPaymentMapper().insertPayment(regionId, WORLD_ID, BUYER, 500.0,
                    LocalDateTime.now().plusDays(1), 500.0);
            wrapper.session().commit();
        }
        Assertions.assertInstanceOf(PayBidResult.FullyPaid.class, logic.payBid(regionId, WORLD_ID, BUYER, 500.0));

        logic.finalizeBidPurchase(regionId, WORLD_ID, BUYER);

        Assertions.assertEquals(BUYER, logic.getFreeholdContract(regionId, WORLD_ID).titleHolderId());
        assertSaleCleanedUp(regionId);
    }

    @Test
    void removedAgent_laterSetFee_notAllowed() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.removeSanctionedAuctioneer(regionId, WORLD_ID, AGENT, TITLE_HOLDER);

        Assertions.assertInstanceOf(SetFeeResult.NotAllowed.class,
                logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false));
    }

    @Test
    void sameFee_underNewRole_updatesRoleAndApproval() {
        String regionId = freehold(TITLE_HOLDER);
        makeAgent(regionId, AGENT);
        logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false);
        logic.setTitleHolder(regionId, WORLD_ID, AGENT, player(TITLE_HOLDER));
        Assertions.assertFalse(feeTo(regionId, Party.personal(AGENT)).approved());

        SetFeeResult result = logic.setFee(regionId, WORLD_ID, player(AGENT), 2, FeeSide.SELLER, false);

        SetFeeResult.Success success = Assertions.assertInstanceOf(SetFeeResult.Success.class, result);
        Assertions.assertFalse(success.needsApproval());
        FeeListing fee = feeTo(regionId, Party.personal(AGENT));
        Assertions.assertEquals("titleholder", fee.role());
        Assertions.assertTrue(fee.approved());
    }

    @Test
    void levyForAnotherAuthorityKind_refused() {
        LevyDefinition businessLevy = levy("business-levy", false, List.of(new LevyTier(0, 4)), PartyKind.BUSINESS);
        LevyDefinition governmentLevy = levy("government-levy", false, List.of(new LevyTier(0, 4)), PartyKind.GOVERNMENT);
        levies = catalog(DEFAULT_LEVY, businessLevy, governmentLevy);
        String regionId = freehold(TITLE_HOLDER);

        Assertions.assertInstanceOf(SetLevyResult.WrongAuthorityKind.class,
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "business-levy"));
        Assertions.assertInstanceOf(SetLevyResult.WrongAuthorityKind.class,
                logic.setAuthorityLevy(GOV, MANAGER, "business-levy"));
        Assertions.assertEquals("national-levy", logic.chosenLevy(regionId, WORLD_ID));
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setRegionLevy(regionId, WORLD_ID, MANAGER, "government-levy"));
        Assertions.assertInstanceOf(SetLevyResult.Success.class,
                logic.setAuthorityLevy(GOV, MANAGER, "government-levy"));
    }
}
