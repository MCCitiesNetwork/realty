package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.regions.Region;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public interface RealtyPaperApi {

    /**
     * Replaces the safe-teleport-location predicate used when finding a safe
     * block to teleport a player to. Adapter modules (e.g. an EssentialsX
     * integration) call this once during their own startup.
     *
     * @param predicate predicate that tests the feet-level block; returns
     *                  {@code true} if safe to teleport to
     */
    void setSafeBlockPredicate(@NotNull Predicate<Block> predicate);

    /**
     * Player name and UUID resolution backed by the server's usercache and Realty's profile cache.
     * Modules and other plugins should use this rather than {@code Bukkit.getOfflinePlayer}.
     */
    @NotNull PlayerNameService playerNameService();

    /**
     * Display names of Treasury accounts, for modules that cannot reach Treasury themselves.
     * Every name is empty on a server without Treasury.
     */
    @NotNull AccountNameService accountNameService();

    /**
     * Builds the context of a player acting on {@code region}: which parties the player may act
     * for, and which of them the player may hand to another party. Every method of this API that
     * takes an {@link ActorContext} should be given one built here. Use it rather than
     * {@link ActorContext#player}, which knows no account and no group, so that a manager of an
     * account or group landlord is refused as a stranger and the conflict-of-interest rule is
     * not applied to an authority the player acts for.
     *
     * <p>The parties tested are the region's landlord and authority, plus {@code extra}: pass
     * the party the player is about to assign, if any. Treasury and the permission plugin are
     * asked on Realty's database thread, and the future completes there.</p>
     *
     * @param player the acting player
     * @param bypass whether the player holds the admin permission of the action, which lets
     *               them act for any party
     * @param region the region the action concerns
     * @param extra  further parties to test, such as a new landlord being assigned
     */
    @NotNull CompletableFuture<ActorContext> actorContext(@NotNull OfflinePlayer player,
                                                          boolean bypass,
                                                          @NotNull WorldGuardRegion region,
                                                          @NotNull Party... extra);

    // ═══════════════════════════════════════════════════
    // COMPLEX OPERATIONS (economy + WG + signs/flags)
    // ═══════════════════════════════════════════════════

    // --- Buy ---

    sealed interface BuyResult {
        record Success(double price, @NotNull String regionId,
                       @Nullable Party previousTitleHolder) implements BuyResult {}
        record NoFreeholdContract(@NotNull String regionId) implements BuyResult {}
        record NotForSale(@NotNull String regionId) implements BuyResult {}
        record IsAuthority() implements BuyResult {}
        record IsTitleHolder() implements BuyResult {}
        record InsufficientFunds(double price, double balance) implements BuyResult {}
        record PaymentFailed(@NotNull String error) implements BuyResult {}
        record TransferFailed(@NotNull String regionId) implements BuyResult {}
        record Error(@NotNull String message) implements BuyResult {}
    }

    /**
     * Buys the region at its asking price. See {@link RealtyBackend#executeBuy} for the
     * conflict-of-interest rule.
     *
     * @param buyer          the buying player and the parties they act for
     * @param bypassConflict whether the buyer holds {@code realty.bypass.conflict-of-interest}
     */
    @NotNull CompletableFuture<BuyResult> buy(@NotNull WorldGuardRegion region,
                                               @NotNull ActorContext buyer,
                                               boolean bypassConflict);

    // --- Rent ---

    sealed interface RentResult {
        record Success(double price, long durationSeconds, @NotNull String regionId,
                       @NotNull Party landlord) implements RentResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements RentResult {}
        record AlreadyOccupied(@NotNull String regionId) implements RentResult {}
        record NotAcceptingTenants(@NotNull String regionId) implements RentResult {}
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
                       @NotNull Party landlord) implements UnrentResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements UnrentResult {}
        /** The lease is scheduled for termination and can only end on the effective date. */
        record Terminating(@NotNull String regionId) implements UnrentResult {}
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
        record Terminating(@NotNull String regionId) implements ExtendResult {}
        record InsufficientFunds(double price, double balance) implements ExtendResult {}
        record PaymentFailed(@NotNull String error) implements ExtendResult {}
        record UpdateFailed(@NotNull String regionId) implements ExtendResult {}
        record Error(@NotNull String message) implements ExtendResult {}
    }

    @NotNull CompletableFuture<ExtendResult> extend(@NotNull WorldGuardRegion region,
                                                     @NotNull UUID tenantId);

    // --- Terminate (with notice) ---

    sealed interface TerminateResult {
        /** {@code charged} is any forced-extension rent the tenant paid to cover the notice period. */
        record Success(@NotNull String regionId, @NotNull LocalDateTime effectiveDate, double charged,
                       @NotNull Party landlord, @NotNull Party tenant,
                       @NotNull String terminatedByRole) implements TerminateResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements TerminateResult {}
        record NotOccupied(@NotNull String regionId) implements TerminateResult {}
        record AlreadyTerminating(@NotNull String regionId) implements TerminateResult {}
        record NotAuthorized(@NotNull String regionId) implements TerminateResult {}
        record InsufficientFunds(double price, double balance) implements TerminateResult {}
        record PaymentFailed(@NotNull String error) implements TerminateResult {}
        record UpdateFailed(@NotNull String regionId) implements TerminateResult {}
        record Error(@NotNull String message) implements TerminateResult {}
    }

    /**
     * Schedules an early termination of {@code region}'s lease, honouring the configured minimum notice.
     * The initiating role is derived from {@code ctx}: the tenant first, then a manager of the landlord
     * (or an admin, who acts as the landlord); a tenant pays for any whole extensions needed to cover the notice, a landlord
     * does not. When {@code immediate} is true the notice is skipped (the lease ends at once and the tenant
     * is refunded all remaining prepaid time) — a staff power, gated by the caller.
     */
    @NotNull CompletableFuture<TerminateResult> terminate(@NotNull WorldGuardRegion region,
                                                          @NotNull ActorContext ctx,
                                                          boolean immediate);

    // --- PayBid ---

    sealed interface PayBidResult {
        record Success(double amount, double newTotal, double remaining,
                       @NotNull String regionId) implements PayBidResult {}
        record FullyPaid(double amount, @NotNull String regionId,
                         @Nullable Party previousTitleHolder) implements PayBidResult {}
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
                         @Nullable Party previousTitleHolder) implements PayOfferResult {}
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
        record Success(@Nullable Party previousTitleHolder,
                       @NotNull String regionId) implements SetTitleHolderResult {}
        record NoFreeholdContract(@NotNull String regionId) implements SetTitleHolderResult {}
        /** The actor does not manage the region's current holder. */
        record NotAuthorized(@NotNull String regionId) implements SetTitleHolderResult {}
        record UpdateFailed(@NotNull String regionId) implements SetTitleHolderResult {}
        record Error(@NotNull String message) implements SetTitleHolderResult {}
    }

    /**
     * Sets the title holder, or clears it when {@code titleHolder} is {@code null}, as the console
     * does: no check of who is acting.
     */
    default @NotNull CompletableFuture<SetTitleHolderResult> setTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable Party titleHolder) {
        return setTitleHolder(region, titleHolder, ActorContext.console());
    }

    /**
     * Sets the title holder, or clears it when {@code titleHolder} is {@code null}. Only a player
     * can hold a title in this version: any other party fails the future with an
     * {@link IllegalArgumentException}.
     *
     * @param ctx who is acting; they must manage the current holder (the title holder, or the
     *            authority while there is none) unless they bypass the check. A refusal gives
     *            {@link SetTitleHolderResult.NotAuthorized} and changes nothing.
     */
    @NotNull CompletableFuture<SetTitleHolderResult> setTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable Party titleHolder, @NotNull ActorContext ctx);

    /**
     * @deprecated use {@link #setTitleHolder(WorldGuardRegion, Party)}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    default @NotNull CompletableFuture<SetTitleHolderResult> setTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable UUID titleHolderId) {
        return setTitleHolder(region, titleHolderId == null ? null : Party.personal(titleHolderId));
    }

    // --- TransferTitleHolder (sets title holder and clears price) ---

    /**
     * Sets the title holder and clears the asking price. Only a player can hold a title in this
     * version: any other party fails the future with an {@link IllegalArgumentException}.
     */
    @NotNull CompletableFuture<SetTitleHolderResult> transferTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable Party titleHolder);

    /**
     * @deprecated use {@link #transferTitleHolder(WorldGuardRegion, Party)}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    default @NotNull CompletableFuture<SetTitleHolderResult> transferTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable UUID titleHolderId) {
        return transferTitleHolder(region, titleHolderId == null ? null : Party.personal(titleHolderId));
    }

    // --- SetTenant ---

    sealed interface SetTenantResult {
        record Success(@Nullable Party previousTenant, @NotNull Party landlord,
                       @NotNull String regionId) implements SetTenantResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements SetTenantResult {}
        /** The actor does not manage the lease's landlord. */
        record NotAuthorized(@NotNull String regionId) implements SetTenantResult {}
        /** The lease has a tenant and the change was asked for only while it has none. */
        record Occupied(@NotNull String regionId) implements SetTenantResult {}
        record UpdateFailed(@NotNull String regionId) implements SetTenantResult {}
        record Error(@NotNull String message) implements SetTenantResult {}
    }

    /**
     * Sets the tenant, or clears it when {@code tenant} is {@code null}, as the console does: no
     * check of who is acting and no tenancy condition.
     */
    default @NotNull CompletableFuture<SetTenantResult> setTenant(
            @NotNull WorldGuardRegion region, @Nullable Party tenant) {
        return setTenant(region, tenant, ActorContext.console(), false);
    }

    /**
     * Sets the tenant, or clears it when {@code tenant} is {@code null}. Only a player can rent
     * in this version: any other party fails the future with an {@link IllegalArgumentException}.
     *
     * @param ctx        who is acting; they must manage the landlord unless they bypass the check.
     *                   A refusal gives {@link SetTenantResult.NotAuthorized} and changes nothing.
     * @param vacantOnly whether a lease that has a tenant refuses the change
     *                   ({@link SetTenantResult.Occupied}), even for a bypassing actor
     */
    @NotNull CompletableFuture<SetTenantResult> setTenant(
            @NotNull WorldGuardRegion region, @Nullable Party tenant,
            @NotNull ActorContext ctx, boolean vacantOnly);

    /**
     * @deprecated use {@link #setTenant(WorldGuardRegion, Party)}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    default @NotNull CompletableFuture<SetTenantResult> setTenant(
            @NotNull WorldGuardRegion region, @Nullable UUID tenantId) {
        return setTenant(region, tenantId == null ? null : Party.personal(tenantId));
    }

    // --- SetLandlord ---

    sealed interface SetLandlordResult {
        record Success(@NotNull Party previousLandlord,
                       @NotNull String regionId) implements SetLandlordResult {}
        record NoLeaseholdContract(@NotNull String regionId) implements SetLandlordResult {}
        /** The lease has a tenant and the change was asked for only while it has none. */
        record Occupied(@NotNull String regionId) implements SetLandlordResult {}
        record UpdateFailed(@NotNull String regionId) implements SetLandlordResult {}
        /** The actor may not hand the current landlord's role to another party. */
        record NotAllowedToReassign(@NotNull Party current) implements SetLandlordResult {}
        /** The actor does not manage the party the role would go to. */
        record NotAllowedToAssign(@NotNull Party requested) implements SetLandlordResult {}
        record Error(@NotNull String message) implements SetLandlordResult {}
    }

    /**
     * Sets the landlord as the console does: the assignment rules are bypassed.
     */
    default @NotNull CompletableFuture<SetLandlordResult> setLandlord(
            @NotNull WorldGuardRegion region, @NotNull Party landlord) {
        return setLandlord(region, landlord, ActorContext.console(), false);
    }

    /**
     * Sets the landlord on behalf of {@code ctx}, with no tenancy condition. See
     * {@link RealtyBackend#setLandlord} for the rules.
     */
    default @NotNull CompletableFuture<SetLandlordResult> setLandlord(
            @NotNull WorldGuardRegion region, @NotNull Party landlord, @NotNull ActorContext ctx) {
        return setLandlord(region, landlord, ctx, false);
    }

    /**
     * Sets the landlord on behalf of {@code ctx}. See {@link RealtyBackend#setLandlord} for the rules.
     *
     * @param ctx        who is acting; the rules are bypassed when it bypasses
     * @param vacantOnly whether a lease that has a tenant refuses the change
     *                   ({@link SetLandlordResult.Occupied}), even for a bypassing actor
     */
    @NotNull CompletableFuture<SetLandlordResult> setLandlord(
            @NotNull WorldGuardRegion region, @NotNull Party landlord,
            @NotNull ActorContext ctx, boolean vacantOnly);

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

    /**
     * Creates a freehold, held by {@code titleHolder} or by nobody when it is {@code null}. Only
     * a player can hold a title in this version: any other party fails the future with an
     * {@link IllegalArgumentException}.
     */
    @NotNull CompletableFuture<CreateFreeholdResult> createFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull Party authority,
            @Nullable Party titleHolder);

    /**
     * @deprecated use {@link #createFreehold(WorldGuardRegion, Double, Party, Party)}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    default @NotNull CompletableFuture<CreateFreeholdResult> createFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull Party authority,
            @Nullable UUID titleHolder) {
        return createFreehold(region, price, authority, titleHolder == null ? null : Party.personal(titleHolder));
    }

    /**
     * Registers an existing WorldGuard region as a freehold. See
     * {@link #createFreehold(WorldGuardRegion, Double, Party, Party)} for the title holder.
     */
    @NotNull CompletableFuture<CreateFreeholdResult> registerFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull Party authority,
            @Nullable Party titleHolder);

    /**
     * @deprecated use {@link #registerFreehold(WorldGuardRegion, Double, Party, Party)}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    default @NotNull CompletableFuture<CreateFreeholdResult> registerFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull Party authority,
            @Nullable UUID titleHolder) {
        return registerFreehold(region, price, authority, titleHolder == null ? null : Party.personal(titleHolder));
    }

    // --- Create/Register Leasehold ---

    sealed interface CreateLeaseholdResult {
        record Success(@NotNull String regionId) implements CreateLeaseholdResult {}
        record AlreadyRegistered(@NotNull String regionId) implements CreateLeaseholdResult {}
        record Error(@NotNull String message) implements CreateLeaseholdResult {}
    }

    @NotNull CompletableFuture<CreateLeaseholdResult> createLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull Party landlord);

    @NotNull CompletableFuture<CreateLeaseholdResult> registerLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull Party landlord);

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
            double price, long durationSeconds, int maxRenewals,
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
            @NotNull UUID inviterId, @NotNull ActorContext invitee, boolean bypassConflict);

    @NotNull CompletableFuture<RealtyBackend.AcceptAgentInviteResult> acceptAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext invitee, boolean bypassConflict);

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
            @NotNull ActorContext ctx, long biddingDurationSeconds,
            long paymentDurationSeconds, double minBid, double minBidStep);

    @NotNull CompletableFuture<RealtyBackend.CancelAuctionResult> cancelAuction(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<RealtyBackend.BidResult> performBid(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext bidder, double bidAmount, boolean bypassConflict);

    // --- Offer ---

    @NotNull CompletableFuture<RealtyBackend.OfferResult> placeOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext offerer, double price, boolean bypassConflict);

    @NotNull CompletableFuture<RealtyBackend.AcceptOfferResult> acceptOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx, @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.WithdrawOfferResult> withdrawOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.RejectOfferResult> rejectOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx, @NotNull UUID offererId);

    @NotNull CompletableFuture<RealtyBackend.RejectAllOffersResult> rejectAllOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx);

    @NotNull CompletableFuture<RealtyBackend.ToggleOffersResult> toggleOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx, boolean accepting);

    @NotNull CompletableFuture<List<OutboundOfferView>> listOutboundOffers(@NotNull UUID offererId);

    @NotNull CompletableFuture<List<InboundOfferView>> listInboundOffers(@NotNull UUID titleHolderId);

    // --- Property Config ---

    @NotNull CompletableFuture<RealtyBackend.SetAuthorityResult> setAuthority(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull Party authority);

    /** Sets the price as the console does: no check of who is acting and no tenancy condition. */
    default @NotNull CompletableFuture<RealtyBackend.SetPriceResult> setPrice(
            @NotNull String regionId, @NotNull UUID worldId, double price) {
        return setPrice(regionId, worldId, price, ActorContext.console(), false);
    }

    /**
     * Sets the price of a freehold or a leasehold.
     *
     * <p>The returned future completes on the main thread, so it must not be joined from there.
     *
     * @param ctx        who is acting; they must manage the region's holder unless they bypass
     *                   the check
     * @param vacantOnly whether a lease that has a tenant refuses the change, even for a
     *                   bypassing actor
     */
    @NotNull CompletableFuture<RealtyBackend.SetPriceResult> setPrice(
            @NotNull String regionId, @NotNull UUID worldId, double price,
            @NotNull ActorContext ctx, boolean vacantOnly);

    /** Clears the price as the console does: no check of who is acting. */
    default @NotNull CompletableFuture<RealtyBackend.UnsetPriceResult> unsetPrice(
            @NotNull String regionId, @NotNull UUID worldId) {
        return unsetPrice(regionId, worldId, ActorContext.console());
    }

    /**
     * Clears a freehold's price.
     *
     * <p>The returned future completes on the main thread, so it must not be joined from there.
     *
     * @param ctx who is acting; they must manage the freehold's holder unless they bypass the check
     */
    @NotNull CompletableFuture<RealtyBackend.UnsetPriceResult> unsetPrice(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull ActorContext ctx);

    /** Sets the duration as the console does: no check of who is acting and no tenancy condition. */
    default @NotNull CompletableFuture<RealtyBackend.SetDurationResult> setDuration(
            @NotNull String regionId, @NotNull UUID worldId, long durationSeconds) {
        return setDuration(regionId, worldId, durationSeconds, ActorContext.console(), false);
    }

    /**
     * Sets a leasehold's duration.
     *
     * <p>The returned future completes on the main thread, so it must not be joined from there.
     *
     * @param ctx        who is acting; they must manage the landlord unless they bypass the check
     * @param vacantOnly whether a lease that has a tenant refuses the change, even for a
     *                   bypassing actor
     */
    @NotNull CompletableFuture<RealtyBackend.SetDurationResult> setDuration(
            @NotNull String regionId, @NotNull UUID worldId, long durationSeconds,
            @NotNull ActorContext ctx, boolean vacantOnly);

    /** Sets the renewal limit as the console does: no check of who is acting and no tenancy condition. */
    default @NotNull CompletableFuture<RealtyBackend.SetMaxRenewalsResult> setMaxRenewals(
            @NotNull String regionId, @NotNull UUID worldId, int maxRenewals) {
        return setMaxRenewals(regionId, worldId, maxRenewals, ActorContext.console(), false);
    }

    /**
     * Sets a leasehold's renewal limit.
     *
     * <p>The returned future completes on the main thread, so it must not be joined from there.
     *
     * @param ctx        who is acting; they must manage the landlord unless they bypass the check
     * @param vacantOnly whether a lease that has a tenant refuses the change, even for a
     *                   bypassing actor
     */
    @NotNull CompletableFuture<RealtyBackend.SetMaxRenewalsResult> setMaxRenewals(
            @NotNull String regionId, @NotNull UUID worldId, int maxRenewals,
            @NotNull ActorContext ctx, boolean vacantOnly);

    @NotNull CompletableFuture<RealtyBackend.SetRentableResult> setRentable(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx, boolean accepting);

    // --- Group mapping ---

    @NotNull CompletableFuture<RealtyBackend.MapGroupResult> mapGroup(
            @NotNull String groupName, @NotNull Party.Account account);

    @NotNull CompletableFuture<RealtyBackend.UnmapGroupResult> unmapGroup(@NotNull String groupName);

    @NotNull CompletableFuture<List<GroupMapping>> listGroupMappings();

    // --- Leasehold Modifications ---

    @NotNull CompletableFuture<RealtyBackend.ProposeModificationResult> proposeModification(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull ActorContext ctx,
            @Nullable Double newPrice, @Nullable Long newDurationSeconds, @Nullable Integer newMaxExtensions);

    @NotNull CompletableFuture<RealtyBackend.ResolveModificationResult> acceptModification(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull ActorContext ctx);

    @NotNull CompletableFuture<RealtyBackend.ResolveModificationResult> rejectModification(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull ActorContext ctx);

    @NotNull CompletableFuture<RealtyBackend.ResolveModificationResult> withdrawModification(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull ActorContext ctx);

    @NotNull CompletableFuture<RealtyBackend.CancelTerminationResult> cancelTermination(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull ActorContext ctx);

    /** Tenant proposals awaiting any of the given landlords, such as every party a player manages. */
    @NotNull CompletableFuture<List<LeaseholdModificationView>> listModificationsAwaitingLandlord(
            @NotNull Set<Party> landlords);

    @NotNull CompletableFuture<List<LeaseholdModificationView>> listPendingModificationsByProposer(
            @NotNull UUID proposerId);

    // --- Query ---

    @NotNull CompletableFuture<RealtyBackend.RegionInfo> getRegionInfo(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@Nullable FreeholdContractEntity> getFreeholdContract(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@Nullable LeaseholdContractEntity> getLeaseholdContract(
            @NotNull String regionId, @NotNull UUID worldId);

    /** See {@link RealtyBackend#listRegions(Party, int, int)}. */
    @NotNull CompletableFuture<RealtyBackend.ListResult> listRegions(
            @NotNull Party target, int limit, int offset);

    default @NotNull CompletableFuture<RealtyBackend.ListResult> listRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return listRegions(Party.personal(targetId), limit, offset);
    }

    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listOwnedRegions(
            @NotNull Party target, int limit, int offset);

    default @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listOwnedRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return listOwnedRegions(Party.personal(targetId), limit, offset);
    }

    /** See {@link RealtyBackend#listAuthorityRegions(Party, int, int)}. */
    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listAuthorityRegions(
            @NotNull Party target, int limit, int offset);

    default @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listAuthorityRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return listAuthorityRegions(Party.personal(targetId), limit, offset);
    }

    /** See {@link RealtyBackend#listLandlordRegions(Party, int, int)}. */
    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listLandlordRegions(
            @NotNull Party target, int limit, int offset);

    default @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listLandlordRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return listLandlordRegions(Party.personal(targetId), limit, offset);
    }

    @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listRentedRegions(
            @NotNull Party target, int limit, int offset);

    default @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listRentedRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return listRentedRegions(Party.personal(targetId), limit, offset);
    }

    @NotNull CompletableFuture<RealtyBackend.HistoryResult> searchHistory(
            @NotNull String regionId, @NotNull UUID worldId,
            @Nullable String eventType, @Nullable LocalDateTime since,
            @Nullable UUID playerId, int limit, int offset);

    @NotNull CompletableFuture<RealtyBackend.RegionWithState> getRegionWithState(
            @NotNull String regionId, @NotNull UUID worldId);

    @NotNull CompletableFuture<@NotNull Map<String, String>> getRegionPlaceholders(
            @NotNull String regionId, @NotNull UUID worldId);

    // --- Region Tags ---

    @NotNull CompletableFuture<@NotNull List<String>> getTagIdsByRegion(@NotNull String worldGuardRegionId);

    @NotNull CompletableFuture<@NotNull List<String>> getRegionIdsByTag(@NotNull String tagId);

    @NotNull CompletableFuture<Integer> countRegionsByTag(@NotNull String tagId);
}
