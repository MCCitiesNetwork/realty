package io.github.md5sha256.realty.api;

import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RealtyApi {

    // --- Configuration ---

    void setOfferPaymentDurationSeconds(long offerPaymentDurationSeconds);

    // --- Sanctioned Auctioneers ---

    int removeSanctionedAuctioneer(@NotNull String worldGuardRegionId,
                                   @NotNull UUID worldId,
                                   @NotNull UUID auctioneerId,
                                   @NotNull UUID actorId);

    // --- Agent Invites ---

    sealed interface InviteAgentResult {
        record Success() implements InviteAgentResult {}
        record NoFreeholdContract() implements InviteAgentResult {}
        record NotTitleHolder() implements InviteAgentResult {}
        record IsTitleHolder() implements InviteAgentResult {}
        record IsAuthority() implements InviteAgentResult {}
        record AlreadyAgent() implements InviteAgentResult {}
        record AlreadyInvited() implements InviteAgentResult {}
    }

    @NotNull InviteAgentResult inviteAgent(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID inviterId,
                                           @NotNull UUID inviteeId);

    sealed interface AcceptAgentInviteResult {
        record Success(@NotNull UUID inviterId) implements AcceptAgentInviteResult {}
        record NotFound() implements AcceptAgentInviteResult {}
        record AlreadyAgent() implements AcceptAgentInviteResult {}
    }

    @NotNull AcceptAgentInviteResult acceptAgentInvite(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull UUID inviteeId);

    sealed interface WithdrawAgentInviteResult {
        record Success() implements WithdrawAgentInviteResult {}
        record NotFound() implements WithdrawAgentInviteResult {}
    }

    @NotNull WithdrawAgentInviteResult withdrawAgentInvite(@NotNull String worldGuardRegionId,
                                                           @NotNull UUID worldId,
                                                           @NotNull UUID inviteeId);

    sealed interface RejectAgentInviteResult {
        record Success(@NotNull UUID inviterId) implements RejectAgentInviteResult {}
        record NotFound() implements RejectAgentInviteResult {}
    }

    @NotNull RejectAgentInviteResult rejectAgentInvite(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull UUID inviteeId);

    // --- Auction ---

    sealed interface CreateAuctionResult {
        record Success() implements CreateAuctionResult {}
        record NotSanctioned() implements CreateAuctionResult {}
        record NoFreeholdContract() implements CreateAuctionResult {}
    }

    @NotNull CreateAuctionResult createAuction(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull UUID auctioneerId,
                                               long biddingDurationSeconds,
                                               long paymentDurationSeconds,
                                               double minBid,
                                               double minBidStep);

    record CancelAuctionResult(int deleted, @NotNull List<UUID> bidderIds) {}

    @NotNull CancelAuctionResult cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    // --- Bid ---

    sealed interface BidResult {
        record Success(@Nullable UUID previousBidderId) implements BidResult {}
        record NoAuction() implements BidResult {}
        record IsOwner() implements BidResult {}
        record BidTooLowMinimum(double minBid) implements BidResult {}
        record BidTooLowCurrent(double currentHighest) implements BidResult {}
        record AlreadyHighestBidder() implements BidResult {}
    }

    @NotNull BidResult performBid(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID bidderId,
                                  double bidAmount);

    // --- Set Price ---

    sealed interface SetPriceResult {
        record Success() implements SetPriceResult {}
        record NoFreeholdContract() implements SetPriceResult {}
        record AuctionExists() implements SetPriceResult {}
        record OfferPaymentInProgress() implements SetPriceResult {}
        record BidPaymentInProgress() implements SetPriceResult {}
        record UpdateFailed() implements SetPriceResult {}
    }

    @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     double price);

    // --- Unset Price ---

    sealed interface UnsetPriceResult {
        record Success() implements UnsetPriceResult {}
        record NoFreeholdContract() implements UnsetPriceResult {}
        record OfferPaymentInProgress() implements UnsetPriceResult {}
        record BidPaymentInProgress() implements UnsetPriceResult {}
        record UpdateFailed() implements UnsetPriceResult {}
    }

    @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId);

    // --- Set Duration ---

    sealed interface SetDurationResult {
        record Success() implements SetDurationResult {}
        record NoLeaseholdContract() implements SetDurationResult {}
        record UpdateFailed() implements SetDurationResult {}
    }

    @NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           long durationSeconds);

    // --- Set Max Renewals ---

    sealed interface SetMaxRenewalsResult {
        record Success() implements SetMaxRenewalsResult {}
        record NoLeaseholdContract() implements SetMaxRenewalsResult {}
        record BelowCurrentExtensions(int currentExtensions) implements SetMaxRenewalsResult {}
        record UpdateFailed() implements SetMaxRenewalsResult {}
    }

    @NotNull SetMaxRenewalsResult setMaxRenewals(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 int maxRenewals);

    // --- Set Landlord ---

    sealed interface SetLandlordResult {
        record Success(@NotNull UUID previousLandlord) implements SetLandlordResult {}
        record NoLeaseholdContract() implements SetLandlordResult {}
        record UpdateFailed() implements SetLandlordResult {}
    }

    @NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID landlordId);

    // --- Set Title Holder ---

    sealed interface SetTitleHolderResult {
        record Success(@Nullable UUID previousTitleHolder) implements SetTitleHolderResult {}
        record NoFreeholdContract() implements SetTitleHolderResult {}
        record UpdateFailed() implements SetTitleHolderResult {}
    }

    @NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @Nullable UUID titleHolderId);

    // --- Update Subregion Landlords ---

    void updateSubregionLandlords(@NotNull List<String> childRegionIds,
                                  @NotNull UUID worldId,
                                  @NotNull UUID newLandlord);

    // --- Set Tenant ---

    sealed interface SetTenantResult {
        record Success(@Nullable UUID previousTenant, @NotNull UUID landlordId) implements SetTenantResult {}
        record NoLeaseholdContract() implements SetTenantResult {}
        record UpdateFailed() implements SetTenantResult {}
    }

    @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @Nullable UUID tenantId);

    // --- Buy (fixed-price) ---

    sealed interface BuyValidation {
        record Eligible(double price, @NotNull UUID authorityId) implements BuyValidation {}
        record NoFreeholdContract() implements BuyValidation {}
        record NotForFreehold() implements BuyValidation {}
        record IsAuthority() implements BuyValidation {}
        record IsTitleHolder() implements BuyValidation {}
    }

    @NotNull BuyValidation validateBuy(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @NotNull UUID buyerId);

    sealed interface BuyResult {
        record Success(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements BuyResult {}
        record NoFreeholdContract() implements BuyResult {}
        record NotForFreehold() implements BuyResult {}
    }

    @NotNull BuyResult executeBuy(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID buyerId);

    // --- Create Freehold ---

    boolean createFreehold(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           @Nullable Double price,
                           @NotNull UUID authority,
                           @Nullable UUID titleHolder);

    // --- Create Leasehold ---

    boolean createLeasehold(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price,
                            long durationSeconds,
                            int maxRenewals,
                            @NotNull UUID landlordId);

    // --- Rent ---

    sealed interface RentResult {
        record Success(double price, long durationSeconds, @NotNull UUID landlordId) implements RentResult {}
        record NoLeaseholdContract() implements RentResult {}
        record AlreadyOccupied() implements RentResult {}
        record UpdateFailed() implements RentResult {}
    }

    @NotNull RentResult previewRent(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId);

    @NotNull RentResult rentRegion(@NotNull String worldGuardRegionId,
                                   @NotNull UUID worldId,
                                   @NotNull UUID tenantId);

    // --- Unrent ---

    sealed interface UnrentResult {
        record Success(double refund, @NotNull UUID tenantId, @NotNull UUID landlordId) implements UnrentResult {}
        record NoLeaseholdContract() implements UnrentResult {}
        record UpdateFailed() implements UnrentResult {}
    }

    @NotNull UnrentResult unrentRegion(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @NotNull UUID tenantId);

    // --- Renew Leasehold ---

    sealed interface RenewLeaseholdResult {
        record Success(double price, @NotNull UUID landlordId) implements RenewLeaseholdResult {}
        record NoLeaseholdContract() implements RenewLeaseholdResult {}
        record NoExtensionsRemaining() implements RenewLeaseholdResult {}
        record UpdateFailed() implements RenewLeaseholdResult {}
    }

    @NotNull RenewLeaseholdResult previewRenewLeasehold(@NotNull String worldGuardRegionId,
                                                        @NotNull UUID worldId);

    @NotNull RenewLeaseholdResult renewLeasehold(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @NotNull UUID tenantId);

    // --- Delete ---

    int deleteRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    // --- Info ---

    record RegionInfo(
            @Nullable FreeholdContractEntity freehold,
            @Nullable LeaseholdContractEntity leasehold,
            @Nullable FreeholdContractAuctionEntity auction,
            @Nullable Double lastSoldPrice,
            @Nullable FreeholdContractBid highestBid
    ) {}

    @Nullable FreeholdContractEntity getFreeholdContract(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId);

    @Nullable LeaseholdContractEntity getLeaseholdContract(@NotNull String worldGuardRegionId,
                                                           @NotNull UUID worldId);

    @NotNull RegionInfo getRegionInfo(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable RegionState getRegionState(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull Map<String, String> getRegionPlaceholders(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId);

    record RegionWithState(
            @NotNull RealtyRegionEntity region,
            @NotNull RegionState state,
            @NotNull Map<String, String> placeholders
    ) {}

    @NotNull List<RegionWithState> getAllRegionsWithState();

    @Nullable RegionWithState getRegionWithState(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId);

    // --- Authority Check ---

    boolean checkRegionAuthority(@NotNull String worldGuardRegionId,
                                 @NotNull UUID worldId,
                                 @NotNull UUID playerId);

    // --- List ---

    record ListResult(
            int ownedCount,
            int landlordCount,
            int rentedCount,
            @NotNull List<RealtyRegionEntity> owned,
            @NotNull List<RealtyRegionEntity> landlord,
            @NotNull List<RealtyRegionEntity> rented
    ) {
        public int totalCount() {
            return ownedCount + landlordCount + rentedCount;
        }
    }

    @NotNull ListResult listRegions(@NotNull UUID targetId, int limit, int offset);

    record SingleCategoryResult(
            int totalCount,
            @NotNull List<RealtyRegionEntity> regions
    ) {}

    @NotNull SingleCategoryResult listOwnedRegions(@NotNull UUID targetId, int limit, int offset);

    @NotNull SingleCategoryResult listRentedRegions(@NotNull UUID targetId, int limit, int offset);

    // --- Offers ---

    @NotNull List<OutboundOfferView> listOutboundOffers(@NotNull UUID offererId);

    @NotNull List<InboundOfferView> listInboundOffers(@NotNull UUID titleHolderId);

    sealed interface WithdrawOfferResult {
        record Success(@Nullable UUID titleHolderId) implements WithdrawOfferResult {}
        record NoOffer() implements WithdrawOfferResult {}
        record OfferAccepted() implements WithdrawOfferResult {}
    }

    @NotNull WithdrawOfferResult withdrawOffer(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull UUID offererId);

    sealed interface RejectOfferResult {
        record Success(@NotNull UUID offererId) implements RejectOfferResult {}
        record NotSanctioned() implements RejectOfferResult {}
        record NoOffer() implements RejectOfferResult {}
        record OfferAccepted() implements RejectOfferResult {}
    }

    @NotNull RejectOfferResult rejectOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID callerId,
                                           @NotNull UUID offererId);

    sealed interface RejectAllOffersResult {
        record Success(@NotNull List<UUID> offererIds) implements RejectAllOffersResult {}
        record NotSanctioned() implements RejectAllOffersResult {}
        record NoFreeholdContract() implements RejectAllOffersResult {}
        record OfferAccepted() implements RejectAllOffersResult {}
    }

    @NotNull RejectAllOffersResult rejectAllOffers(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull UUID callerId);

    sealed interface OfferResult {
        record Success(@Nullable UUID titleHolderId) implements OfferResult {}
        record NoFreeholdContract() implements OfferResult {}
        record NotAcceptingOffers() implements OfferResult {}
        record IsOwner() implements OfferResult {}
        record AlreadyHasOffer() implements OfferResult {}
        record AuctionExists() implements OfferResult {}
        record InsertFailed() implements OfferResult {}
    }

    @NotNull OfferResult placeOffer(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID offererId,
                                    double price);

    sealed interface ToggleOffersResult {
        record Success(boolean acceptingOffers) implements ToggleOffersResult {}
        record NotSanctioned() implements ToggleOffersResult {}
        record NoFreeholdContract() implements ToggleOffersResult {}
        record UpdateFailed() implements ToggleOffersResult {}
    }

    @NotNull ToggleOffersResult toggleOffers(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull UUID callerId,
                                             boolean acceptingOffers,
                                             boolean bypassAuth);

    sealed interface AcceptOfferResult {
        record Success() implements AcceptOfferResult {}
        record NotSanctioned() implements AcceptOfferResult {}
        record NoOffer() implements AcceptOfferResult {}
        record AuctionExists() implements AcceptOfferResult {}
        record AlreadyAccepted() implements AcceptOfferResult {}
        record InsertFailed() implements AcceptOfferResult {}
    }

    @NotNull AcceptOfferResult acceptOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID callerId,
                                           @NotNull UUID offererId);

    // --- Pay Offer ---

    sealed interface PayOfferResult {
        record Success(double newTotal, double remaining,
                       @NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayOfferResult {}
        record FullyPaid(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayOfferResult {}
        record NoPaymentRecord() implements PayOfferResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayOfferResult {}
    }

    @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     @NotNull UUID offererId,
                                     double amount);

    // --- Pay Bid ---

    sealed interface PayBidResult {
        record Success(double newTotal, double remaining,
                       @NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayBidResult {}
        record FullyPaid(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayBidResult {}
        record NoPaymentRecord() implements PayBidResult {}
        record PaymentExpired() implements PayBidResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayBidResult {}
    }

    @NotNull PayBidResult payBid(@NotNull String worldGuardRegionId,
                                 @NotNull UUID worldId,
                                 @NotNull UUID bidderId,
                                 double amount);

    // --- Expired Bidding Auctions ---

    record ExpiredBiddingAuction(
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId,
            @Nullable UUID winnerId,
            @NotNull UUID auctioneerId
    ) {}

    @NotNull List<ExpiredBiddingAuction> clearExpiredBiddingAuctions();

    // --- Expired Bid Payments ---

    record ExpiredBidPayment(@NotNull UUID bidderId, double refundAmount, @NotNull String regionId) {}

    @NotNull List<ExpiredBidPayment> clearExpiredBidPayments();

    // --- Expired Offer Payments ---

    record ExpiredOfferPayment(@NotNull UUID offererId, double refundAmount, @NotNull String regionId) {}

    @NotNull List<ExpiredOfferPayment> clearExpiredOfferPayments();

    // --- Expired Leaseholds ---

    record ExpiredLeasehold(
            @NotNull UUID tenantId,
            @NotNull UUID landlordId,
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId
    ) {}

    @NotNull List<ExpiredLeasehold> clearExpiredLeaseholds();

    // --- History Search ---

    record HistoryResult(@NotNull List<HistoryEntry> entries, int totalCount) {}

    @NotNull HistoryResult searchHistory(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @Nullable String eventType,
                                         @Nullable LocalDateTime since,
                                         @Nullable UUID playerId,
                                         int limit,
                                         int offset);
}
