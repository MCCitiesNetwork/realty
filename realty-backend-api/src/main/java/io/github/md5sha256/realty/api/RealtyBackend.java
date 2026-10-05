package io.github.md5sha256.realty.api;

import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RealtyBackend {

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

    /**
     * Invites a player to act as the freehold's agent. The invitee must not deal with its authority:
     * see {@link #executeBuy} for the conflict-of-interest rule, which answers
     * {@link InviteAgentResult.IsAuthority}. The invitee's groups may be unknown here, because the
     * permission plugin cannot tell the groups of a player who is offline, so
     * {@link #acceptAgentInvite} applies the rule again.
     *
     * @param invitee        the invited player and the parties they act for
     * @param bypassConflict whether the invitee holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull InviteAgentResult inviteAgent(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID inviterId,
                                           @NotNull ActorContext invitee,
                                           boolean bypassConflict);

    sealed interface AcceptAgentInviteResult {
        record Success(@NotNull UUID inviterId) implements AcceptAgentInviteResult {}
        record NotFound() implements AcceptAgentInviteResult {}
        record AlreadyAgent() implements AcceptAgentInviteResult {}
        /** The invitee may not deal with the freehold's authority; the invite stays pending. */
        record IsAuthority() implements AcceptAgentInviteResult {}
    }

    /**
     * Accepts an invite, applying the conflict-of-interest rule of {@link #executeBuy} again with
     * what is known of the invitee now.
     *
     * @param invitee        the invited player and the parties they act for
     * @param bypassConflict whether the invitee holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull AcceptAgentInviteResult acceptAgentInvite(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull ActorContext invitee,
                                                       boolean bypassConflict);

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
        /** The region already has at least one offer; offers and auctions are mutually exclusive. */
        record OffersExist() implements CreateAuctionResult {}
    }

    /**
     * Starts an auction with the acting player as the auctioneer. The actor must manage the freehold's
     * authority, hold its title, or be a sanctioned auctioneer; the admin bypass plays no part.
     */
    @NotNull CreateAuctionResult createAuction(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull ActorContext ctx,
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

    /**
     * Places a bid. A bidder who may not deal with the freehold's authority (see {@link #executeBuy}),
     * its titleholder and its auctioneer are refused with {@link BidResult.IsOwner}.
     *
     * @param bidder         the bidding player and the parties they act for
     * @param bypassConflict whether the bidder holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull BidResult performBid(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull ActorContext bidder,
                                  double bidAmount,
                                  boolean bypassConflict);

    // --- Set Price ---

    sealed interface SetPriceResult {
        record Success() implements SetPriceResult {}
        record NoContract() implements SetPriceResult {}
        record AuctionExists() implements SetPriceResult {}
        record OfferPaymentInProgress() implements SetPriceResult {}
        record BidPaymentInProgress() implements SetPriceResult {}
        record NotAuthorized() implements SetPriceResult {}
        record Occupied() implements SetPriceResult {}
        record UpdateFailed() implements SetPriceResult {}
    }

    /**
     * Sets the price of a freehold or a leasehold, with no check of who is acting and no
     * tenancy condition.
     */
    default @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             double price) {
        return setPrice(worldGuardRegionId, worldId, price, ActorContext.console(), false);
    }

    /**
     * Sets the price of a freehold or a leasehold. Only a manager of the holder (the lease's
     * landlord, the freehold's title holder, or its authority while it has no title holder) may,
     * and the write is refused if the holder changes between the check and the write.
     *
     * @param ctx        who is acting; a bypassing actor is not held to the holder check
     * @param vacantOnly whether a leased region with a tenant refuses the change ({@code Occupied});
     *                   a freehold ignores it
     */
    @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     double price,
                                     @NotNull ActorContext ctx,
                                     boolean vacantOnly);

    // --- Unset Price ---

    sealed interface UnsetPriceResult {
        record Success() implements UnsetPriceResult {}
        record NoFreeholdContract() implements UnsetPriceResult {}
        record OfferPaymentInProgress() implements UnsetPriceResult {}
        record BidPaymentInProgress() implements UnsetPriceResult {}
        record NotAuthorized() implements UnsetPriceResult {}
        record UpdateFailed() implements UnsetPriceResult {}
    }

    /** Unsets a freehold's price with no check of who is acting. */
    default @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId) {
        return unsetPrice(worldGuardRegionId, worldId, ActorContext.console());
    }

    /**
     * Unsets a freehold's price. Only a manager of the title holder (or of the authority while
     * there is no title holder) may, and the write is refused if the holder changes between the
     * check and the write.
     *
     * @param ctx who is acting; a bypassing actor is not held to the holder check
     */
    @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull ActorContext ctx);

    // --- Set Duration ---

    sealed interface SetDurationResult {
        record Success() implements SetDurationResult {}
        record NoLeaseholdContract() implements SetDurationResult {}
        record NotAuthorized() implements SetDurationResult {}
        record Occupied() implements SetDurationResult {}
        record UpdateFailed() implements SetDurationResult {}
    }

    /** Sets a leasehold's duration with no check of who is acting and no tenancy condition. */
    default @NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   long durationSeconds) {
        return setDuration(worldGuardRegionId, worldId, durationSeconds, ActorContext.console(), false);
    }

    /**
     * Sets a leasehold's duration. Only a manager of the landlord may, and the write is refused
     * if the landlord changes between the check and the write.
     *
     * @param ctx        who is acting; a bypassing actor is not held to the landlord check
     * @param vacantOnly whether a lease with a tenant refuses the change ({@code Occupied})
     */
    @NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           long durationSeconds,
                                           @NotNull ActorContext ctx,
                                           boolean vacantOnly);

    // --- Set Max Renewals ---

    sealed interface SetMaxRenewalsResult {
        record Success() implements SetMaxRenewalsResult {}
        record NoLeaseholdContract() implements SetMaxRenewalsResult {}
        record BelowCurrentExtensions(int currentExtensions) implements SetMaxRenewalsResult {}
        record NotAuthorized() implements SetMaxRenewalsResult {}
        record Occupied() implements SetMaxRenewalsResult {}
        record UpdateFailed() implements SetMaxRenewalsResult {}
    }

    /** Sets a leasehold's renewal limit with no check of who is acting and no tenancy condition. */
    default @NotNull SetMaxRenewalsResult setMaxRenewals(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         int maxRenewals) {
        return setMaxRenewals(worldGuardRegionId, worldId, maxRenewals, ActorContext.console(), false);
    }

    /**
     * Sets a leasehold's renewal limit. Only a manager of the landlord may, and the write is
     * refused if the landlord changes between the check and the write.
     *
     * @param ctx        who is acting; a bypassing actor is not held to the landlord check
     * @param vacantOnly whether a lease with a tenant refuses the change ({@code Occupied})
     */
    @NotNull SetMaxRenewalsResult setMaxRenewals(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 int maxRenewals,
                                                 @NotNull ActorContext ctx,
                                                 boolean vacantOnly);

    // --- Set Landlord ---

    sealed interface SetLandlordResult {
        record Success(@NotNull Party previousLandlord) implements SetLandlordResult {}
        record NoLeaseholdContract() implements SetLandlordResult {}
        record UpdateFailed() implements SetLandlordResult {}
        /** The lease has a tenant and the change was asked for only while it has none. */
        record Occupied() implements SetLandlordResult {}
        /** The actor may not hand the current landlord's role to another party. */
        record NotAllowedToReassign(@NotNull Party current) implements SetLandlordResult {}
        /** The actor does not manage the party the role would go to. */
        record NotAllowedToAssign(@NotNull Party requested) implements SetLandlordResult {}
    }

    /**
     * Hands the lease's landlord role to {@code newLandlord}. Unless {@code ctx} bypasses the rules,
     * the current landlord must be in {@link ActorContext#reassigns()} and the new one in
     * {@link ActorContext#manages()}, checked in that order. There is no tenancy condition.
     */
    default @NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull Party newLandlord,
                                                   @NotNull ActorContext ctx) {
        return setLandlord(worldGuardRegionId, worldId, newLandlord, ctx, false);
    }

    /**
     * Hands the lease's landlord role to {@code newLandlord}. The authority checks are those of
     * {@link #setLandlord(String, UUID, Party, ActorContext)}. The write is also refused with
     * {@link SetLandlordResult.NotAllowedToReassign} (carrying the landlord now stored) if the
     * landlord changes between the check and the write, unless {@code ctx} bypasses the rules.
     *
     * @param ctx        who is acting
     * @param vacantOnly whether a lease with a tenant refuses the change ({@code Occupied})
     */
    @NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull Party newLandlord,
                                           @NotNull ActorContext ctx,
                                           boolean vacantOnly);

    // --- Set Authority ---

    sealed interface SetAuthorityResult {
        record Success(@NotNull Party previousAuthority) implements SetAuthorityResult {}
        record NoFreeholdContract() implements SetAuthorityResult {}
        record UpdateFailed() implements SetAuthorityResult {}
    }

    @NotNull SetAuthorityResult setAuthority(@NotNull String worldGuardRegionId,
                                              @NotNull UUID worldId,
                                              @NotNull Party authority);

    // --- Set Title Holder ---

    sealed interface SetTitleHolderResult {
        record Success(@Nullable UUID previousTitleHolder) implements SetTitleHolderResult {}
        record NoFreeholdContract() implements SetTitleHolderResult {}
        record UpdateFailed() implements SetTitleHolderResult {}
        record NotAuthorized() implements SetTitleHolderResult {}
    }

    /** Sets the title holder with no check of who is acting. */
    default @NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         @Nullable UUID titleHolderId) {
        return setTitleHolder(worldGuardRegionId, worldId, titleHolderId, ActorContext.console());
    }

    /**
     * Sets or clears a freehold's title holder. Only a manager of the current holder (the title
     * holder, or the authority while there is none) may, and the write is refused if the holder
     * changes between the check and the write.
     *
     * @param ctx who is acting; a bypassing actor is not held to the holder check
     */
    @NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @Nullable UUID titleHolderId,
                                                 @NotNull ActorContext ctx);

    // --- Transfer Title Holder (sets title holder and clears price) ---

    @NotNull SetTitleHolderResult transferTitleHolder(@NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId,
                                                      @Nullable UUID titleHolderId);

    // --- Update Subregion Landlords ---

    void updateSubregionLandlords(@NotNull List<String> childRegionIds,
                                  @NotNull UUID worldId,
                                  @NotNull Party newLandlord);

    // --- Set Tenant ---

    sealed interface SetTenantResult {
        record Success(@Nullable UUID previousTenant, @NotNull Party landlord) implements SetTenantResult {}
        record NoLeaseholdContract() implements SetTenantResult {}
        record UpdateFailed() implements SetTenantResult {}
        record NotAuthorized() implements SetTenantResult {}
        record Occupied() implements SetTenantResult {}
    }

    /** Sets the tenant with no check of who is acting and no tenancy condition. */
    default @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @Nullable UUID tenantId) {
        return setTenant(worldGuardRegionId, worldId, tenantId, ActorContext.console(), false);
    }

    /**
     * Sets or clears a lease's tenant. Only a manager of the landlord may, and the write is
     * refused if the landlord changes between the check and the write.
     *
     * @param ctx        who is acting; a bypassing actor is not held to the landlord check
     * @param vacantOnly whether a lease with a tenant refuses the change ({@code Occupied})
     */
    @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @Nullable UUID tenantId,
                                       @NotNull ActorContext ctx,
                                       boolean vacantOnly);

    // --- Buy (fixed-price) ---

    sealed interface BuyResult {
        /**
         * @param undo what {@link #rollbackBuy} needs to put the region back as it was
         */
        record Success(double price, @NotNull Party authority, @Nullable UUID titleHolderId,
                       @NotNull BuyUndo undo) implements BuyResult {}
        record NoFreeholdContract() implements BuyResult {}
        record NotForFreehold() implements BuyResult {}
        record IsAuthority() implements BuyResult {}
        record IsTitleHolder() implements BuyResult {}
        record UpdateFailed() implements BuyResult {}
    }

    /**
     * What a reservation took away, kept so that it can be put back.
     *
     * @param historyId   the record of the sale
     * @param offers      the offers that were on the region
     * @param auctioneers everybody who was sanctioned to auction it
     */
    record BuyUndo(int historyId,
                   @NotNull List<WithdrawnOffer> offers,
                   @NotNull List<UUID> auctioneers) {}

    /** An offer as it stood when a reservation withdrew it. */
    record WithdrawnOffer(@NotNull UUID offererId, double offerPrice, @NotNull LocalDateTime offerTime) {}

    /**
     * Reserves the region for the buyer, ahead of payment. In one transaction: the title
     * passes to them, the asking price is cleared so that nobody else can buy it in the
     * meantime, the offers and sanctioned auctioneers the sale overtakes are withdrawn,
     * and the sale is recorded.
     *
     * <p>If the payment then fails, {@link #rollbackBuy} undoes every part of that.</p>
     *
     * <p>A region with an accepted offer or a winning bid that is being paid for is not
     * for sale, and this answers {@link BuyResult.NotForFreehold}. Selling it would take
     * the region from under somebody who has already paid part of its price.</p>
     *
     * <p>Conflict of interest: a buyer who is the authority itself is refused with
     * {@link BuyResult.IsAuthority}, whatever their permissions. A buyer who manages the
     * authority is refused too, unless {@code bypassConflict} is set. Bids, offers and agent
     * invites follow the same rule.</p>
     *
     * @param buyer          the buying player and the parties they act for; its admin bypass plays no part
     * @param bypassConflict whether the buyer holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull BuyResult executeBuy(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull ActorContext buyer,
                                  boolean bypassConflict);

    /**
     * Undoes a reservation made by {@link #executeBuy} that was not paid for. The region
     * is left as it was found: the title, the asking price, the offers and the
     * sanctioned auctioneers are put back, and the record of the sale is removed.
     *
     * <p>The region is put back only if the buyer still holds the title. If somebody
     * else has changed it since, the reservation is already gone, and putting the old
     * holder back would undo their change and not the buyer's. The record of the sale
     * is removed either way: the buyer did not buy the region.</p>
     */
    void rollbackBuy(@NotNull String worldGuardRegionId,
                     @NotNull UUID worldId,
                     @NotNull UUID buyerId,
                     @NotNull BuyResult.Success reserved);

    // --- Create Freehold ---

    boolean createFreehold(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           @Nullable Double price,
                           @NotNull Party authority,
                           @Nullable UUID titleHolder);

    // --- Create Leasehold ---

    boolean createLeasehold(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price,
                            long durationSeconds,
                            int maxRenewals,
                            @NotNull Party landlord);

    // --- Rent ---

    sealed interface RentResult {
        /**
         * @param historyId the record of the letting, for {@link #rollbackRent} to remove
         */
        record Success(double price, long durationSeconds, @NotNull Party landlord,
                       int historyId) implements RentResult {}
        record NoLeaseholdContract() implements RentResult {}
        record AlreadyOccupied() implements RentResult {}
        record NotAcceptingTenants() implements RentResult {}
        record UpdateFailed() implements RentResult {}
    }

    /**
     * Lets the region to the tenant, ahead of payment, and records the letting. If the
     * payment then fails, {@link #rollbackRent} undoes both.
     */
    @NotNull RentResult rentRegion(@NotNull String worldGuardRegionId,
                                   @NotNull UUID worldId,
                                   @NotNull UUID tenantId);

    // --- Set Rentable (accepting new tenants) ---

    sealed interface SetRentableResult {
        record Success(boolean acceptingTenants) implements SetRentableResult {}
        record NoLeaseholdContract() implements SetRentableResult {}
        record NotAuthorized() implements SetRentableResult {}
        record NoChange(boolean acceptingTenants) implements SetRentableResult {}
        record UpdateFailed() implements SetRentableResult {}
    }

    /** Sets whether a leasehold accepts new tenants. Only a manager of the landlord, or an admin, may. */
    @NotNull SetRentableResult setRentable(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull ActorContext ctx,
                                           boolean accepting);

    /**
     * Undoes a letting made by {@link #rentRegion} that was not paid for: the region has
     * no tenant again, and the record of the letting is removed.
     *
     * <p>The tenant is cleared only if it is still this tenant. The record is removed
     * either way: this tenant did not rent the region.</p>
     */
    void rollbackRent(@NotNull String worldGuardRegionId,
                      @NotNull UUID worldId,
                      @NotNull UUID tenantId,
                      @NotNull RentResult.Success reserved);

    // --- Unrent ---

    sealed interface UnrentResult {
        /**
         * @param previous  the tenancy as it stood before it was ended
         * @param historyId the record of its ending
         */
        record Success(double refund, @NotNull UUID tenantId, @NotNull Party landlord,
                       @NotNull Tenancy previous, int historyId) implements UnrentResult {}
        record NoLeaseholdContract() implements UnrentResult {}
        /** The lease is scheduled for termination; it can only end via the sweep on the effective date. */
        record Terminating() implements UnrentResult {}
        record UpdateFailed() implements UnrentResult {}
    }

    /**
     * The dates of a tenancy and how many of its extensions have been used.
     *
     * @param extensionsUsed null on a lease with no cap on extensions
     */
    record Tenancy(@Nullable LocalDateTime startDate,
                   @Nullable LocalDateTime endDate,
                   @Nullable Integer extensionsUsed) {}

    /**
     * Ends the tenancy, ahead of the refund, and records its ending. If the refund then
     * cannot be paid, {@link #rollbackUnrent} undoes both.
     */
    @NotNull UnrentResult unrentRegion(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @NotNull UUID tenantId);

    /**
     * Undoes an ending made by {@link #unrentRegion} whose refund could not be paid. The
     * tenancy is put back as it was, with the same dates and the same extensions used,
     * and the record of its ending is removed.
     *
     * <p>Does nothing if the region has a tenant already. Somebody has moved in since,
     * so this tenancy did end, and the record of that is left.</p>
     */
    void rollbackUnrent(@NotNull String worldGuardRegionId,
                        @NotNull UUID worldId,
                        @NotNull UUID tenantId,
                        @NotNull UnrentResult.Success ended);

    // --- Renew Leasehold ---

    sealed interface RenewLeaseholdResult {
        /**
         * @param undo what {@link #rollbackRenewLeasehold} needs to put the lease back
         */
        record Success(double price, @NotNull Party landlord,
                       @NotNull RenewUndo undo) implements RenewLeaseholdResult {}
        record NoLeaseholdContract() implements RenewLeaseholdResult {}
        record NoExtensionsRemaining() implements RenewLeaseholdResult {}
        /** The lease is scheduled for termination and can no longer be extended. */
        record Terminating() implements RenewLeaseholdResult {}
        record UpdateFailed() implements RenewLeaseholdResult {}
    }

    /**
     * What a renewal changed, kept so that it can be put back.
     *
     * @param historyId    the record of the renewal
     * @param appliedTerms the change of terms that fell due with it, or null if none did
     */
    record RenewUndo(int historyId, @Nullable AppliedTerms appliedTerms) {}

    /**
     * A change of terms that was applied, and the terms it replaced.
     *
     * @param modificationId the change that was applied
     * @param historyId      the record of its being applied
     */
    record AppliedTerms(int modificationId,
                        int historyId,
                        double previousPrice,
                        long previousDurationSeconds,
                        @Nullable Integer previousMaxExtensions,
                        @Nullable Integer previousExtensionsUsed) {}

    /**
     * Extends the lease by one period, ahead of payment, and records the renewal. A
     * landlord's change of terms that falls due with it is applied first, so that the
     * renewal is charged on the new terms.
     *
     * <p>If the payment then fails, {@link #rollbackRenewLeasehold} undoes all of it.</p>
     */
    @NotNull RenewLeaseholdResult renewLeasehold(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @NotNull UUID tenantId);

    /**
     * Undoes a renewal made by {@link #renewLeasehold} that was not paid for. The lease
     * ends when it did before and the extension is not used up. A change of terms that
     * was applied with the renewal is taken back and left pending, to fall due with the
     * next renewal that is paid for. The records of both are removed.
     *
     * <p>The lease is put back only if this tenant still holds it. The record of the
     * renewal is removed either way.</p>
     */
    void rollbackRenewLeasehold(@NotNull String worldGuardRegionId,
                                @NotNull UUID worldId,
                                @NotNull UUID tenantId,
                                @NotNull RenewLeaseholdResult.Success reserved);

    // --- Leasehold Modifications (pending term changes) ---

    sealed interface ProposeModificationResult {
        /** {@code active} is {@code true} for a landlord proposal (applies on next renewal), false when awaiting the landlord. */
        record Success(int modificationId, @NotNull String proposerRole, boolean active,
                       @NotNull Party landlord, @NotNull UUID tenantId) implements ProposeModificationResult {}
        record NoLeaseholdContract() implements ProposeModificationResult {}
        record NotOccupied() implements ProposeModificationResult {}
        record Terminating() implements ProposeModificationResult {}
        record NotAuthorized() implements ProposeModificationResult {}
        record UpdateFailed() implements ProposeModificationResult {}
    }

    /**
     * Proposes a change to a leasehold's terms ({@code null} fields are left unchanged and merge with any
     * existing same-role proposal). The proposer's role is derived from {@code ctx}: the tenant first,
     * then a manager of the landlord (or an admin), who acts as the landlord. The landlord's proposal
     * becomes {@code ACTIVE} (applies on the tenant's next renewal); the tenant's becomes
     * {@code AWAITING_LANDLORD}.
     */
    @NotNull ProposeModificationResult proposeModification(@NotNull String worldGuardRegionId,
                                                           @NotNull UUID worldId,
                                                           @NotNull ActorContext ctx,
                                                           @Nullable Double newPrice,
                                                           @Nullable Long newDurationSeconds,
                                                           @Nullable Integer newMaxExtensions);

    sealed interface ResolveModificationResult {
        /** {@code tenantId} is the lease's tenant, or {@code null} when the lease has none. */
        record Success(int modificationId, @Nullable UUID tenantId, @NotNull Party landlord,
                       @NotNull String proposerRole) implements ResolveModificationResult {}
        record NoLeaseholdContract() implements ResolveModificationResult {}
        record NoPendingProposal() implements ResolveModificationResult {}
        /** The pending modification is not a tenant proposal awaiting the landlord (accept/reject only). */
        record NotTenantProposal() implements ResolveModificationResult {}
        /**
         * The caller does not manage the landlord (accept/reject, and withdrawing a landlord proposal)
         * or is not the proposer of a tenant proposal (withdraw).
         */
        record NotAuthorized() implements ResolveModificationResult {}
        record UpdateFailed() implements ResolveModificationResult {}
    }

    /** A manager of the landlord (or an admin) accepts a tenant's pending proposal, promoting it to {@code ACTIVE}. */
    @NotNull ResolveModificationResult acceptModification(@NotNull String worldGuardRegionId,
                                                          @NotNull UUID worldId,
                                                          @NotNull ActorContext ctx);

    /** A manager of the landlord (or an admin) rejects a tenant's pending proposal. */
    @NotNull ResolveModificationResult rejectModification(@NotNull String worldGuardRegionId,
                                                          @NotNull UUID worldId,
                                                          @NotNull ActorContext ctx);

    /**
     * Withdraws the pending proposal. Any manager of the landlord (or an admin) may withdraw a landlord
     * proposal; only the proposer (or an admin) may withdraw a tenant proposal.
     */
    @NotNull ResolveModificationResult withdrawModification(@NotNull String worldGuardRegionId,
                                                            @NotNull UUID worldId,
                                                            @NotNull ActorContext ctx);

    /**
     * Tenant proposals awaiting a decision from any of the given landlords (inbox). A player's
     * inbox passes every party the player manages, so that a proposal on a lease of an account
     * reaches everyone who acts for the account.
     */
    @NotNull List<LeaseholdModificationView> listModificationsAwaitingLandlord(@NotNull Set<Party> landlords);

    /** The given player's own non-terminal proposals (outbox). */
    @NotNull List<LeaseholdModificationView> listPendingModificationsByProposer(@NotNull UUID proposerId);

    // --- Terminate Leasehold (with notice) ---

    sealed interface TerminateLeaseholdResult {
        record Success(@NotNull UUID tenantId, @NotNull Party landlord) implements TerminateLeaseholdResult {}
        record NoLeaseholdContract() implements TerminateLeaseholdResult {}
        record NotOccupied() implements TerminateLeaseholdResult {}
        record AlreadyTerminating() implements TerminateLeaseholdResult {}
        record UpdateFailed() implements TerminateLeaseholdResult {}
    }

    /**
     * Schedules an early termination. {@code newEndDate} (already &ge; {@code effectiveDate}) becomes the
     * paid-through end so the regular expiry never fires first; the lease actually ends at
     * {@code effectiveDate}. The caller (Paper layer) is responsible for charging any forced extensions
     * before invoking this, under the per-region lock.
     */
    @NotNull TerminateLeaseholdResult terminateLease(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @NotNull LocalDateTime newEndDate,
                                                     @NotNull LocalDateTime effectiveDate,
                                                     @NotNull String terminatedByRole);

    sealed interface CancelTerminationResult {
        /** {@code tenantId} is the lease's tenant, or {@code null} for a lease that has none. */
        record Success(@NotNull String terminatedByRole, @NotNull Party landlord,
                       @Nullable UUID tenantId) implements CancelTerminationResult {}
        record NoLeaseholdContract() implements CancelTerminationResult {}
        record NotTerminating() implements CancelTerminationResult {}
        /** The caller did not initiate the termination (and is not an admin). */
        record NotAuthorized() implements CancelTerminationResult {}
        record UpdateFailed() implements CancelTerminationResult {}
    }

    /**
     * Cancels a scheduled termination. Only the side that started it may: any manager of the landlord,
     * or the tenant. An admin may always.
     */
    @NotNull CancelTerminationResult cancelTermination(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull ActorContext ctx);

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

    /** Every party in the Party table that is not a player: each account and each group. */
    @NotNull List<Party> listNonPlayerParties();

    /**
     * The group's mapped party, or {@code null} if {@code /realty group map} has not created
     * one for it yet. The lookup is case-insensitive.
     */
    @Nullable Party.Group findGroupParty(@NotNull String groupName);

    /** The account party stored for this account id, or null when no contract has ever named it. */
    @Nullable Party.Account findAccountParty(int accountId);

    // --- Group mapping ---

    sealed interface MapGroupResult {
        record Created(@NotNull Party.Group group) implements MapGroupResult {}
        record Changed(@NotNull Party.Group previous, @NotNull Party.Group current) implements MapGroupResult {}
        record NoChange(@NotNull Party.Group group) implements MapGroupResult {}
    }

    /**
     * Gives a permission group an account, which makes the group a party. A group that already
     * has one is changed in place: its party id stays, so every contract that names the group
     * keeps naming it and pays or is paid through the new account from now on. The name is
     * stored in lower case.
     */
    @NotNull MapGroupResult mapGroup(@NotNull String groupName, @NotNull Party.Account account);

    sealed interface UnmapGroupResult {
        record Success(@NotNull Party.Group group) implements UnmapGroupResult {}
        record NotMapped() implements UnmapGroupResult {}
        record StillInUse(int contractCount, int historyCount) implements UnmapGroupResult {}
    }

    /**
     * Removes a group's account, and with it the group's party. Refused while any contract or
     * history entry names the group, since those rows point at the party.
     */
    @NotNull UnmapGroupResult unmapGroup(@NotNull String groupName);

    /** Every group that has an account, ordered by group name. */
    @NotNull List<GroupMapping> listGroupMappings();

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

    /**
     * Whether the region is registered with Realty at all, independent of any
     * contract on it.
     *
     * <p>This is not {@link #getRegionState}: that returns {@code null} both for an
     * unregistered region and for a registered one carrying no contract, so it cannot
     * answer this question.</p>
     */
    boolean isRegistered(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    // --- Authority Check ---

    /**
     * Whether the actor holds the freehold's title, rents the leasehold, or manages its landlord.
     * The freehold's authority does not count, and neither does the admin bypass.
     */
    boolean checkRegionAuthority(@NotNull String worldGuardRegionId,
                                 @NotNull UUID worldId,
                                 @NotNull ActorContext ctx);

    // --- List ---

    record ListResult(
            int ownedCount,
            int authorityCount,
            int landlordCount,
            int rentedCount,
            @NotNull List<RealtyRegionEntity> owned,
            @NotNull List<RealtyRegionEntity> authority,
            @NotNull List<RealtyRegionEntity> landlord,
            @NotNull List<RealtyRegionEntity> rented
    ) {
        public int totalCount() {
            return ownedCount + authorityCount + landlordCount + rentedCount;
        }
    }

    /**
     * The regions whose title {@code target} holds ({@code owned}), whose freehold authority it is
     * ({@code authority}), which it lets as the landlord of a lease ({@code landlord}) and which it
     * rents ({@code rented}), paged across the four in that order. Only a player holds a title or
     * rents, so for any other party those two are empty.
     */
    @NotNull ListResult listRegions(@NotNull Party target, int limit, int offset);

    default @NotNull ListResult listRegions(@NotNull UUID targetId, int limit, int offset) {
        return listRegions(Party.personal(targetId), limit, offset);
    }

    record SingleCategoryResult(
            int totalCount,
            @NotNull List<RealtyRegionEntity> regions
    ) {}

    /** The regions whose title {@code target} holds; none for a party that is not a player. */
    @NotNull SingleCategoryResult listOwnedRegions(@NotNull Party target, int limit, int offset);

    default @NotNull SingleCategoryResult listOwnedRegions(@NotNull UUID targetId, int limit, int offset) {
        return listOwnedRegions(Party.personal(targetId), limit, offset);
    }

    /** The freeholds whose authority {@code target} is. */
    @NotNull SingleCategoryResult listAuthorityRegions(@NotNull Party target, int limit, int offset);

    default @NotNull SingleCategoryResult listAuthorityRegions(@NotNull UUID targetId, int limit, int offset) {
        return listAuthorityRegions(Party.personal(targetId), limit, offset);
    }

    /** The leaseholds {@code target} lets as their landlord, whether a tenant rents them or not. */
    @NotNull SingleCategoryResult listLandlordRegions(@NotNull Party target, int limit, int offset);

    default @NotNull SingleCategoryResult listLandlordRegions(@NotNull UUID targetId, int limit, int offset) {
        return listLandlordRegions(Party.personal(targetId), limit, offset);
    }

    /** The regions {@code target} rents; none for a party that is not a player. */
    @NotNull SingleCategoryResult listRentedRegions(@NotNull Party target, int limit, int offset);

    default @NotNull SingleCategoryResult listRentedRegions(@NotNull UUID targetId, int limit, int offset) {
        return listRentedRegions(Party.personal(targetId), limit, offset);
    }

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
                                           @NotNull ActorContext ctx,
                                           @NotNull UUID offererId);

    sealed interface RejectAllOffersResult {
        record Success(@NotNull List<UUID> offererIds) implements RejectAllOffersResult {}
        record NotSanctioned() implements RejectAllOffersResult {}
        record NoFreeholdContract() implements RejectAllOffersResult {}
        record OfferAccepted() implements RejectAllOffersResult {}
    }

    @NotNull RejectAllOffersResult rejectAllOffers(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull ActorContext ctx);

    sealed interface OfferResult {
        record Success(@Nullable UUID titleHolderId) implements OfferResult {}
        record NoFreeholdContract() implements OfferResult {}
        record NotAcceptingOffers() implements OfferResult {}
        record IsOwner() implements OfferResult {}
        record AlreadyHasOffer() implements OfferResult {}
        record AuctionExists() implements OfferResult {}
        record InsertFailed() implements OfferResult {}
    }

    /**
     * Places an offer. An offerer who may not deal with the freehold's authority (see
     * {@link #executeBuy}) and its titleholder are refused with {@link OfferResult.IsOwner}.
     *
     * @param offerer        the offering player and the parties they act for
     * @param bypassConflict whether the offerer holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull OfferResult placeOffer(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull ActorContext offerer,
                                    double price,
                                    boolean bypassConflict);

    sealed interface ToggleOffersResult {
        record Success(boolean acceptingOffers) implements ToggleOffersResult {}
        record NotSanctioned() implements ToggleOffersResult {}
        record NoFreeholdContract() implements ToggleOffersResult {}
        record UpdateFailed() implements ToggleOffersResult {}
    }

    /** Like the other offer actions, and an admin may too. */
    @NotNull ToggleOffersResult toggleOffers(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull ActorContext ctx,
                                             boolean acceptingOffers);

    sealed interface AcceptOfferResult {
        record Success() implements AcceptOfferResult {}
        record NotSanctioned() implements AcceptOfferResult {}
        record NoOffer() implements AcceptOfferResult {}
        record AuctionExists() implements AcceptOfferResult {}
        record AlreadyAccepted() implements AcceptOfferResult {}
        record InsertFailed() implements AcceptOfferResult {}
    }

    /**
     * The actor must manage the freehold's authority, hold its title, or be a sanctioned auctioneer;
     * the admin bypass plays no part. The same holds for {@link #rejectOffer} and {@link #rejectAllOffers}.
     */
    @NotNull AcceptOfferResult acceptOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull ActorContext ctx,
                                           @NotNull UUID offererId);

    // --- Pay Offer ---

    sealed interface PayOfferResult {
        record Success(double newTotal, double remaining,
                       @NotNull Party authority, @Nullable UUID titleHolderId) implements PayOfferResult {}
        record FullyPaid(@NotNull Party authority, @Nullable UUID titleHolderId) implements PayOfferResult {}
        record NoPaymentRecord() implements PayOfferResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayOfferResult {}
    }

    @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     @NotNull UUID offererId,
                                     double amount);

    void rollbackPayOffer(@NotNull String worldGuardRegionId,
                          @NotNull UUID worldId,
                          @NotNull UUID offererId,
                          double amount);

    /**
     * Commit the ownership transfer for a fully-paid offer purchase. Called ONLY
     * after the economy payment has succeeded, so the region is never handed over
     * before the money moves. Idempotent and a no-op if the record isn't fully paid.
     */
    void finalizeOfferPurchase(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @NotNull UUID offererId);

    // --- Pay Bid ---

    sealed interface PayBidResult {
        record Success(double newTotal, double remaining,
                       @NotNull Party authority, @Nullable UUID titleHolderId) implements PayBidResult {}
        record FullyPaid(@NotNull Party authority, @Nullable UUID titleHolderId) implements PayBidResult {}
        record NoPaymentRecord() implements PayBidResult {}
        record PaymentExpired() implements PayBidResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayBidResult {}
    }

    @NotNull PayBidResult payBid(@NotNull String worldGuardRegionId,
                                 @NotNull UUID worldId,
                                 @NotNull UUID bidderId,
                                 double amount);

    void rollbackPayBid(@NotNull String worldGuardRegionId,
                        @NotNull UUID worldId,
                        @NotNull UUID bidderId,
                        double amount);

    /**
     * Commit the ownership transfer for a fully-paid auction bid. Called ONLY
     * after the economy payment has succeeded, so the region is never handed over
     * before the money moves. Idempotent and a no-op if the record isn't fully paid.
     */
    void finalizeBidPurchase(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @NotNull UUID bidderId);

    // --- Expired Bidding Auctions ---

    record ExpiredBiddingAuction(
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId,
            @Nullable UUID winnerId,
            @NotNull UUID auctioneerId
    ) {}

    @NotNull List<ExpiredBiddingAuction> clearExpiredBiddingAuctions();

    // --- Expired Bid Payments ---

    /**
     * @param worldId null if the region row has already been deleted
     */
    record ExpiredBidPayment(@NotNull UUID bidderId, double refundAmount, @NotNull String regionId,
                              @Nullable UUID worldId) {}

    @NotNull List<ExpiredBidPayment> clearExpiredBidPayments();

    // --- Expired Offer Payments ---

    /**
     * @param worldId null if the region row has already been deleted
     */
    record ExpiredOfferPayment(@NotNull UUID offererId, double refundAmount, @NotNull String regionId,
                                @Nullable UUID worldId) {}

    @NotNull List<ExpiredOfferPayment> clearExpiredOfferPayments();

    // --- Expired Leaseholds ---

    record ExpiredLeasehold(
            @NotNull UUID tenantId,
            @NotNull Party landlord,
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId
    ) {}

    @NotNull List<ExpiredLeasehold> clearExpiredLeaseholds();

    // --- Terminated Leaseholds (scheduled termination date elapsed) ---

    record TerminatedLeasehold(
            @NotNull UUID tenantId,
            @NotNull Party landlord,
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId,
            double refund,
            @NotNull String terminatedByRole
    ) {}

    /**
     * Ends leaseholds whose scheduled termination date has elapsed (clears the tenant, records history)
     * and returns each with the prorated refund of prepaid-but-unused time. The economy refund itself
     * (landlord &rarr; tenant) is performed by the Paper layer.
     */
    @NotNull List<TerminatedLeasehold> clearTerminatedLeaseholds();

    // --- Aggregate Statistics ---

    /**
     * Every figure the counters below report, read in one statement. The API's
     * dashboard route wants all of them at once; asking for each was ten round trips.
     */
    @NotNull StatisticsEntity statistics();

    int countAllRegions();

    int countAllFreeholdContracts();

    int countAllLeaseholdContracts();

    int countOccupiedFreeholdContracts();

    int countOccupiedLeaseholdContracts();

    int countActiveOffers();

    int countActiveAuctions();

    int countRegionsByAuthority(@NotNull Party authority);

    @NotNull List<String> listRegionNamesByTitleHolder(@NotNull UUID playerId);

    @NotNull List<String> listRegionNamesByTenant(@NotNull UUID playerId);

    @NotNull List<String> listRegionNamesByLandlord(@NotNull Party landlord);

    int countRegionsByTitleHolder(@NotNull UUID playerId);

    int countRegionsByLandlord(@NotNull Party landlord);

    int countRegionsByTenant(@NotNull UUID playerId);

    int countOccupiedLeaseholdsByLandlord(@NotNull Party landlord);

    long averageLeaseholdDurationSeconds();

    double averageFreeholdPrice();

    double averageLeaseholdPrice();

    // --- Region Tags ---

    @NotNull List<String> getAllTagIds();

    /**
     * Every tag in use with its region count, in one statement and in tag-id order --
     * what {@link #getAllTagIds()} followed by {@link #countRegionsByTag(String)} per
     * tag answers, without the query per tag.
     */
    @NotNull List<TagCountEntity> countRegionsPerTag();

    @NotNull List<String> getTagIdsByRegion(@NotNull String worldGuardRegionId);

    @NotNull List<String> getRegionIdsByTag(@NotNull String tagId);

    int countRegionsByTag(@NotNull String tagId);

    // --- History Search ---

    record HistoryResult(@NotNull List<HistoryEntry> entries, int totalCount) {}

    @NotNull HistoryResult searchHistory(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @Nullable String eventType,
                                         @Nullable LocalDateTime since,
                                         @Nullable UUID playerId,
                                         int limit,
                                         int offset);

    // --- Schematics ---

    /**
     * Stores {@code data} as the region's schematic, replacing any previous capture.
     *
     * @return {@code false} when no such region is registered, so nothing was stored
     */
    boolean storeSchematic(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           byte @NotNull [] data);

    /**
     * The region's most recent schematic, or {@code null} if it has never been captured.
     */
    byte @Nullable [] getSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
}
