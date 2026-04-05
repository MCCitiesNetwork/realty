package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.regions.Region;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RealtyPaperApi {

    // ═══════════════════════════════════════════════════
    // COMPLEX OPERATIONS (economy + WG + signs/flags)
    // ═══════════════════════════════════════════════════

    // --- Buy ---

    sealed interface BuyResult {
        record Success(double price, @NotNull String regionId,
                       @Nullable UUID previousTitleHolderId) implements BuyResult {}
        record NoFreeholdContract(@NotNull String regionId) implements BuyResult {}
        record NotForSale(@NotNull String regionId) implements BuyResult {}
        record IsAuthority() implements BuyResult {}
        record IsTitleHolder() implements BuyResult {}
        record InsufficientFunds(double price, double balance) implements BuyResult {}
        record PaymentFailed(@NotNull String error) implements BuyResult {}
        record TransferFailed(@NotNull String regionId) implements BuyResult {}
        record Error(@NotNull String message) implements BuyResult {}
    }

    @NotNull CompletableFuture<BuyResult> buy(@NotNull WorldGuardRegion region,
                                               @NotNull UUID buyerId);

    // --- Rent ---

    sealed interface RentResult {
        record Success(double price, long durationSeconds, @NotNull String regionId,
                       @NotNull UUID landlordId) implements RentResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements RentResult {}
        record AlreadyOccupied(@NotNull String regionId) implements RentResult {}
        record InsufficientFunds(double price, double balance) implements RentResult {}
        record PaymentFailed(@NotNull String error) implements RentResult {}
        record UpdateFailed(@NotNull String regionId) implements RentResult {}
        record Error(@NotNull String message) implements RentResult {}
    }

    @NotNull CompletableFuture<RentResult> rent(@NotNull WorldGuardRegion region,
                                                 @NotNull UUID tenantId);

    // --- Unrent ---

    sealed interface UnrentResult {
        record Success(double refund, @NotNull String regionId,
                       @NotNull UUID landlordId) implements UnrentResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements UnrentResult {}
        record RefundFailed(@NotNull String error) implements UnrentResult {}
        record UpdateFailed(@NotNull String regionId) implements UnrentResult {}
        record Error(@NotNull String message) implements UnrentResult {}
    }

    @NotNull CompletableFuture<UnrentResult> unrent(@NotNull WorldGuardRegion region,
                                                     @NotNull UUID tenantId);

    // --- Extend ---

    sealed interface ExtendResult {
        record Success(double price, @NotNull String regionId) implements ExtendResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements ExtendResult {}
        record NoExtensionsRemaining(@NotNull String regionId) implements ExtendResult {}
        record InsufficientFunds(double price, double balance) implements ExtendResult {}
        record PaymentFailed(@NotNull String error) implements ExtendResult {}
        record UpdateFailed(@NotNull String regionId) implements ExtendResult {}
        record Error(@NotNull String message) implements ExtendResult {}
    }

    @NotNull CompletableFuture<ExtendResult> extend(@NotNull WorldGuardRegion region,
                                                     @NotNull UUID tenantId);

    // --- PayBid ---

    sealed interface PayBidResult {
        record Success(double amount, double newTotal, double remaining,
                       @NotNull String regionId) implements PayBidResult {}
        record FullyPaid(double amount, @NotNull String regionId,
                         @Nullable UUID previousTitleHolderId) implements PayBidResult {}
        record NoPaymentRecord(@NotNull String regionId) implements PayBidResult {}
        record PaymentExpired(@NotNull String regionId) implements PayBidResult {}
        record ExceedsAmountOwed(double amount, double amountOwed,
                                 @NotNull String regionId) implements PayBidResult {}
        record InsufficientFunds(double balance) implements PayBidResult {}
        record PaymentFailed(@NotNull String error) implements PayBidResult {}
        record TransferFailed() implements PayBidResult {}
        record Error(@NotNull String message) implements PayBidResult {}
    }

    @NotNull CompletableFuture<PayBidResult> payBid(@NotNull WorldGuardRegion region,
                                                     @NotNull UUID bidderId,
                                                     double amount);

    // --- PayOffer ---

    sealed interface PayOfferResult {
        record Success(double amount, double newTotal, double remaining,
                       @NotNull String regionId) implements PayOfferResult {}
        record FullyPaid(double amount, @NotNull String regionId,
                         @Nullable UUID previousTitleHolderId) implements PayOfferResult {}
        record NoPaymentRecord(@NotNull String regionId) implements PayOfferResult {}
        record ExceedsAmountOwed(double amount, double amountOwed,
                                 @NotNull String regionId) implements PayOfferResult {}
        record InsufficientFunds(double balance) implements PayOfferResult {}
        record PaymentFailed(@NotNull String error) implements PayOfferResult {}
        record TransferFailed() implements PayOfferResult {}
        record Error(@NotNull String message) implements PayOfferResult {}
    }

    @NotNull CompletableFuture<PayOfferResult> payOffer(@NotNull WorldGuardRegion region,
                                                         @NotNull UUID offererId,
                                                         double amount);

    // --- SetTitleHolder ---

    sealed interface SetTitleHolderResult {
        record Success(@Nullable UUID previousTitleHolder,
                       @NotNull String regionId) implements SetTitleHolderResult {}
        record NoFreeholdContract(@NotNull String regionId) implements SetTitleHolderResult {}
        record UpdateFailed(@NotNull String regionId) implements SetTitleHolderResult {}
        record Error(@NotNull String message) implements SetTitleHolderResult {}
    }

    @NotNull CompletableFuture<SetTitleHolderResult> setTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable UUID titleHolderId);

    // --- SetTenant ---

    sealed interface SetTenantResult {
        record Success(@Nullable UUID previousTenant, @NotNull UUID landlordId,
                       @NotNull String regionId) implements SetTenantResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements SetTenantResult {}
        record UpdateFailed(@NotNull String regionId) implements SetTenantResult {}
        record Error(@NotNull String message) implements SetTenantResult {}
    }

    @NotNull CompletableFuture<SetTenantResult> setTenant(
            @NotNull WorldGuardRegion region, @Nullable UUID tenantId);

    // --- SetLandlord ---

    sealed interface SetLandlordResult {
        record Success(@NotNull UUID previousLandlord,
                       @NotNull String regionId) implements SetLandlordResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements SetLandlordResult {}
        record UpdateFailed(@NotNull String regionId) implements SetLandlordResult {}
        record Error(@NotNull String message) implements SetLandlordResult {}
    }

    @NotNull CompletableFuture<SetLandlordResult> setLandlord(
            @NotNull WorldGuardRegion region, @NotNull UUID landlordId);

    // --- Delete ---

    sealed interface DeleteResult {
        record Success() implements DeleteResult {}
        record NotRegistered() implements DeleteResult {}
        record WorldGuardSaveError(@NotNull String error) implements DeleteResult {}
        record Error(@NotNull String message) implements DeleteResult {}
    }

    @NotNull CompletableFuture<DeleteResult> deleteRegion(
            @NotNull WorldGuardRegion region, boolean includeWorldGuard);

    // --- Create/Register Freehold ---

    sealed interface CreateFreeholdResult {
        record Success(@NotNull String regionId) implements CreateFreeholdResult {}
        record AlreadyRegistered(@NotNull String regionId) implements CreateFreeholdResult {}
        record Error(@NotNull String message) implements CreateFreeholdResult {}
    }

    @NotNull CompletableFuture<CreateFreeholdResult> createFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull UUID authority,
            @Nullable UUID titleHolder);

    @NotNull CompletableFuture<CreateFreeholdResult> registerFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull UUID authority,
            @Nullable UUID titleHolder);

    // --- Create/Register Leasehold ---

    sealed interface CreateLeaseholdResult {
        record Success(@NotNull String regionId) implements CreateLeaseholdResult {}
        record AlreadyRegistered(@NotNull String regionId) implements CreateLeaseholdResult {}
        record Error(@NotNull String message) implements CreateLeaseholdResult {}
    }

    @NotNull CompletableFuture<CreateLeaseholdResult> createLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull UUID landlordId);

    @NotNull CompletableFuture<CreateLeaseholdResult> registerLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull UUID landlordId);

    // --- Subregion QuickCreate ---

    sealed interface QuickCreateSubregionResult {
        record Success(@NotNull String regionId,
                       @NotNull String parentId) implements QuickCreateSubregionResult {}
        record NoFreeholdContract(@NotNull String parentId) implements QuickCreateSubregionResult {}
        record RegionExists(@NotNull String regionId) implements QuickCreateSubregionResult {}
        record Error(@NotNull String message) implements QuickCreateSubregionResult {}
    }

    @NotNull CompletableFuture<QuickCreateSubregionResult> quickCreateSubregion(
            @NotNull WorldGuardRegion parentRegion,
            @NotNull String childName,
            @NotNull Region selection,
            double price, long durationSeconds,
            @NotNull UUID landlordId);

    // --- Sign Place ---

    sealed interface PlaceSignResult {
        record Success(@NotNull String regionId) implements PlaceSignResult {}
        record NotRegistered(@NotNull String regionId) implements PlaceSignResult {}
        record Error(@NotNull String message) implements PlaceSignResult {}
    }

    @NotNull CompletableFuture<PlaceSignResult> placeSign(
            @NotNull WorldGuardRegion region,
            @NotNull UUID signWorldId, int blockX, int blockY, int blockZ);

    // --- Sign Remove ---

    sealed interface RemoveSignResult {
        record Success() implements RemoveSignResult {}
        record NotRegistered() implements RemoveSignResult {}
        record Error(@NotNull String message) implements RemoveSignResult {}
    }

    @NotNull CompletableFuture<RemoveSignResult> removeSign(
            @NotNull UUID signWorldId, int blockX, int blockY, int blockZ);

    // --- Sign List ---

    @NotNull CompletableFuture<List<RealtySignEntity>> listSigns(
            @NotNull String regionId, @NotNull UUID worldId);

    // ═══════════════════════════════════════
    // SIMPLE PROXY OPERATIONS (DB-only)
    // ═══════════════════════════════════════

    // --- Agent ---

    @NotNull CompletableFuture<RealtyBackend.InviteAgentResult> inviteAgent(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID inviterId, @NotNull UUID inviteeId);

    @NotNull CompletableFuture<RealtyBackend.AcceptAgentInviteResult> acceptAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId);

    @NotNull CompletableFuture<RealtyBackend.WithdrawAgentInviteResult> withdrawAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId);

    @NotNull CompletableFuture<RealtyBackend.RejectAgentInviteResult> rejectAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId);

    @NotNull CompletableFuture<Integer> removeSanctionedAuctioneer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID auctioneerId, @NotNull UUID actorId);

    // --- Auction ---

    @NotNull CompletableFuture<RealtyBackend.CreateAuctionResult> createAuction(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID auctioneerId, long biddingDurationSeconds,
            long paymentDurationSeconds, double minBid, double minBidStep);

    @NotNull CompletableFuture<RealtyBackend.CancelAuctionResult> cancelAuction(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<RealtyBackend.BidResult> performBid(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID bidderId, double bidAmount);

    // --- Offer ---

    @NotNull CompletableFuture<RealtyBackend.OfferResult> placeOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID offererId, double price);

    @NotNull CompletableFuture<RealtyBackend.AcceptOfferResult> acceptOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.WithdrawOfferResult> withdrawOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.RejectOfferResult> rejectOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.RejectAllOffersResult> rejectAllOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId);

    @NotNull CompletableFuture<RealtyBackend.ToggleOffersResult> toggleOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, boolean accepting, boolean bypassAuth);

    @NotNull CompletableFuture<List<OutboundOfferView>> listOutboundOffers(@NotNull UUID offererId);

    @NotNull CompletableFuture<List<InboundOfferView>> listInboundOffers(@NotNull UUID titleHolderId);

    // --- Property Config ---

    @NotNull CompletableFuture<RealtyBackend.SetPriceResult> setPrice(
            @NotNull String regionId, @NotNull UUID worldId, double price);

    @NotNull CompletableFuture<RealtyBackend.UnsetPriceResult> unsetPrice(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<RealtyBackend.SetDurationResult> setDuration(
            @NotNull String regionId, @NotNull UUID worldId, long durationSeconds);

    @NotNull CompletableFuture<RealtyBackend.SetMaxRenewalsResult> setMaxRenewals(
            @NotNull String regionId, @NotNull UUID worldId, int maxRenewals);

    // --- Query ---

    @NotNull CompletableFuture<RealtyBackend.RegionInfo> getRegionInfo(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@Nullable FreeholdContractEntity> getFreeholdContract(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@Nullable LeaseholdContractEntity> getLeaseholdContract(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<RealtyBackend.ListResult> listRegions(
            @NotNull UUID targetId, int limit, int offset);

    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listOwnedRegions(
            @NotNull UUID targetId, int limit, int offset);

    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listRentedRegions(
            @NotNull UUID targetId, int limit, int offset);

    @NotNull CompletableFuture<RealtyBackend.HistoryResult> searchHistory(
            @NotNull String regionId, @NotNull UUID worldId,
            @Nullable String eventType, @Nullable LocalDateTime since,
            @Nullable UUID playerId, int limit, int offset);

    @NotNull CompletableFuture<RealtyBackend.RegionWithState> getRegionWithState(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@NotNull Map<String, String>> getRegionPlaceholders(
            @NotNull String regionId, @NotNull UUID worldId);
}
