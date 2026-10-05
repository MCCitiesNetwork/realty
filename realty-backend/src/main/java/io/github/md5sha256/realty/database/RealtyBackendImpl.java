package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.LeaseholdModificationStatus;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import io.github.md5sha256.realty.database.entity.AgentHistoryEntity;
import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.FreeholdContractAgentInviteEntity;
import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.database.entity.LeaseholdHistoryEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import io.github.md5sha256.realty.database.entity.TerminatedLeaseholdView;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import io.github.md5sha256.realty.database.mapper.LeaseholdContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.PartyMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

public class RealtyBackendImpl implements RealtyBackend {

    private final Database database;
    private final Function<Party, CompletableFuture<String>> partyNameResolver;
    private final Function<LocalDateTime, String> dateFormatter;
    private final LongSupplier offerPaymentDurationSeconds;

    public RealtyBackendImpl(@NotNull Database database,
                             @NotNull Function<Party, CompletableFuture<String>> partyNameResolver,
                             @NotNull Function<LocalDateTime, String> dateFormatter,
                             @NotNull LongSupplier offerPaymentDurationSeconds) {
        this.database = database;
        this.partyNameResolver = partyNameResolver;
        this.dateFormatter = dateFormatter;
        this.offerPaymentDurationSeconds = offerPaymentDurationSeconds;
    }

    // --- Parties ---

    /**
     * The id of the party's row, creating its base row and kind row if it has none. Runs in a short
     * transaction of its own, before the caller's, so that two commands naming the same new party
     * at the same moment both succeed: the loser's kind row is refused by its unique key, the two
     * rows it made are rolled back, and it reads the winner's. A party created here stays if the
     * caller's transaction then rolls back; a party that nothing names does no harm.
     */
    private int partyIdCreatingIfAbsent(@NotNull Party party) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            try {
                int partyId = wrapper.partyMapper().findOrInsert(party);
                session.commit();
                return partyId;
            } catch (PersistenceException raced) {
                session.rollback();
                Integer partyId = wrapper.partyMapper().findId(party);
                if (partyId == null) {
                    throw raced;
                }
                return partyId;
            }
        }
    }

    /**
     * The id of a party that a contract already names, so its row exists. Used for
     * history rows, which only ever name the contract's own landlord or authority.
     */
    private static int namedPartyId(@NotNull SqlSessionWrapper wrapper, @NotNull Party party) {
        Integer partyId = wrapper.partyMapper().findId(party);
        if (partyId == null) {
            throw new IllegalStateException("no Party row for " + party + ", although a contract names it");
        }
        return partyId;
    }

    // --- Sanctioned Auctioneers ---

    @Override
    public int removeSanctionedAuctioneer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID auctioneerId,
                                           @NotNull UUID actorId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            int rows = wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .deleteByRegionAndAuctioneer(worldGuardRegionId, worldId, auctioneerId);
            if (rows > 0) {
                wrapper.agentHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.AGENT_REMOVE.name(), auctioneerId, actorId);
            }
            session.commit();
            return rows;
        }
    }

    // --- Agent Invites ---


    @Override
    public @NotNull InviteAgentResult inviteAgent(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull UUID inviterId,
                                                   @NotNull ActorContext invitee,
                                                   boolean bypassConflict) {
        UUID inviteeId = invitee.requirePlayer();
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new InviteAgentResult.NoFreeholdContract();
            }
            if (!inviterId.equals(freehold.titleHolderId())) {
                return new InviteAgentResult.NotTitleHolder();
            }
            if (inviteeId.equals(freehold.titleHolderId())) {
                return new InviteAgentResult.IsTitleHolder();
            }
            if (conflictsWithAuthority(freehold.authority(), invitee, bypassConflict)) {
                return new InviteAgentResult.IsAuthority();
            }
            if (wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .existsByRegionAndAuctioneer(worldGuardRegionId, worldId, inviteeId)) {
                return new InviteAgentResult.AlreadyAgent();
            }
            if (wrapper.freeholdContractAgentInviteMapper()
                    .existsByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId)) {
                return new InviteAgentResult.AlreadyInvited();
            }
            wrapper.freeholdContractAgentInviteMapper()
                    .insert(worldGuardRegionId, worldId, inviterId, inviteeId);
            session.commit();
            return new InviteAgentResult.Success();
        }
    }


    @Override
    public @NotNull AcceptAgentInviteResult acceptAgentInvite(@NotNull String worldGuardRegionId,
                                                               @NotNull UUID worldId,
                                                               @NotNull ActorContext invitee,
                                                               boolean bypassConflict) {
        UUID inviteeId = invitee.requirePlayer();
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            FreeholdContractAgentInviteEntity invite = wrapper.freeholdContractAgentInviteMapper()
                    .selectByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            if (invite == null) {
                return new AcceptAgentInviteResult.NotFound();
            }
            if (wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .existsByRegionAndAuctioneer(worldGuardRegionId, worldId, inviteeId)) {
                wrapper.freeholdContractAgentInviteMapper()
                        .deleteByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
                session.commit();
                return new AcceptAgentInviteResult.AlreadyAgent();
            }
            // The invite may have been made while the invitee was offline, when their groups
            // could not be known, so the conflict of interest is checked again here.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new AcceptAgentInviteResult.NotFound();
            }
            if (conflictsWithAuthority(freehold.authority(), invitee, bypassConflict)) {
                return new AcceptAgentInviteResult.IsAuthority();
            }
            wrapper.freeholdContractAgentInviteMapper()
                    .deleteByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .insert(worldGuardRegionId, worldId, inviteeId);
            wrapper.agentHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.AGENT_ADD.name(), inviteeId, invite.inviterId());
            session.commit();
            return new AcceptAgentInviteResult.Success(invite.inviterId());
        }
    }


    @Override
    public @NotNull WithdrawAgentInviteResult withdrawAgentInvite(@NotNull String worldGuardRegionId,
                                                                    @NotNull UUID worldId,
                                                                    @NotNull UUID inviteeId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            int rows = wrapper.freeholdContractAgentInviteMapper()
                    .deleteByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            session.commit();
            return rows > 0
                    ? new WithdrawAgentInviteResult.Success()
                    : new WithdrawAgentInviteResult.NotFound();
        }
    }


    @Override
    public @NotNull RejectAgentInviteResult rejectAgentInvite(@NotNull String worldGuardRegionId,
                                                                @NotNull UUID worldId,
                                                                @NotNull UUID inviteeId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            FreeholdContractAgentInviteEntity invite = wrapper.freeholdContractAgentInviteMapper()
                    .selectByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            if (invite == null) {
                return new RejectAgentInviteResult.NotFound();
            }
            wrapper.freeholdContractAgentInviteMapper()
                    .deleteByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            session.commit();
            return new RejectAgentInviteResult.Success(invite.inviterId());
        }
    }

    // --- Auction ---


    @Override
    public @NotNull CreateAuctionResult createAuction(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @NotNull ActorContext ctx,
                              long biddingDurationSeconds,
                              long paymentDurationSeconds,
                              double minBid,
                              double minBidStep) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            // Lock the freehold row (the per-region serialization point) so the
            // offers-exist check cannot race a concurrent placeOffer, which performs the
            // mirror check under the same lock. Without it both sides pass their snapshot
            // read and commit, leaving the region with an offer and an auction at once.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new CreateAuctionResult.NoFreeholdContract();
            }
            if (wrapper.freeholdContractOfferMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new CreateAuctionResult.OffersExist();
            }
            if (!actsForFreehold(wrapper, freehold, worldGuardRegionId, worldId, ctx)) {
                return new CreateAuctionResult.NotSanctioned();
            }
            wrapper.freeholdContractAuctionMapper().createAuction(
                    worldGuardRegionId, worldId, ctx.requirePlayer(), LocalDateTime.now(),
                    biddingDurationSeconds, paymentDurationSeconds, minBid, minBidStep);
            session.commit();
            return new CreateAuctionResult.Success();
        }
    }


    @Override
    public @NotNull CancelAuctionResult cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the active auction row first so a concurrent bid or settlement
            // (possibly in another process) cannot interleave with the cancel.
            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                    .selectActiveByRegionForUpdate(worldGuardRegionId, worldId);
            if (auction == null) {
                wrapper.session().commit();
                return new CancelAuctionResult(0, List.of());
            }
            List<UUID> bidderIds = wrapper.freeholdContractBidMapper().selectDistinctBidders(worldGuardRegionId, worldId);
            int deleted = wrapper.freeholdContractAuctionMapper().deleteActiveAuctionByRegion(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return new CancelAuctionResult(deleted, bidderIds);
        }
    }

    // --- Bid ---


    @Override
    public @NotNull BidResult performBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull ActorContext bidder,
                                         double bidAmount,
                                         boolean bypassConflict) {
        UUID bidderId = bidder.requirePlayer();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();
            FreeholdContractBidMapper bidMapper = wrapper.freeholdContractBidMapper();

            // Lock the auction row for this transaction so bid placement cannot
            // interleave with settlement or cancellation (possibly in another
            // process, e.g. the web/REST API) on the same auction.
            FreeholdContractAuctionEntity auction = auctionMapper.selectActiveByRegionForUpdate(worldGuardRegionId, worldId);
            if (auction == null) {
                return new BidResult.NoAuction();
            }
            // Settlement records a bid payment for the winner once bidding closes.
            // If one exists, bidding is over (the auction lingers in its payment
            // phase with ended = FALSE) and no further bids may be accepted.
            if (wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new BidResult.NoAuction();
            }
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null && (conflictsWithAuthority(freehold.authority(), bidder, bypassConflict)
                    || bidderId.equals(freehold.titleHolderId())
                    || bidderId.equals(auction.auctioneerId()))) {
                return new BidResult.IsOwner();
            }
            if (bidAmount < auction.minBid()) {
                return new BidResult.BidTooLowMinimum(auction.minBid());
            }
            FreeholdContractBid highestBid = bidMapper.selectHighestBid(worldGuardRegionId, worldId);
            if (highestBid != null) {
                if (highestBid.bidderId().equals(bidderId)) {
                    return new BidResult.AlreadyHighestBidder();
                }
                if (bidAmount < highestBid.bidAmount() + auction.minStep()) {
                    return new BidResult.BidTooLowCurrent(highestBid.bidAmount());
                }
            }
            UUID previousBidderId = highestBid != null ? highestBid.bidderId() : null;
            int inserted = bidMapper.performContractBid(new FreeholdContractBid(
                    auction.freeholdContractAuctionId(), bidderId, bidAmount, LocalDateTime.now()));
            if (inserted == 0) {
                // Re-fetch highest bid in case it was inserted concurrently
                FreeholdContractBid current = bidMapper.selectHighestBid(worldGuardRegionId, worldId);
                if (current != null) {
                    if (current.bidderId().equals(bidderId)) {
                        return new BidResult.AlreadyHighestBidder();
                    }
                    if (bidAmount < current.bidAmount() + auction.minStep()) {
                        return new BidResult.BidTooLowCurrent(current.bidAmount());
                    }
                }
                return new BidResult.BidTooLowMinimum(auction.minBid());
            }
            wrapper.session().commit();
            return new BidResult.Success(previousBidderId);
        }
    }

    /** Why a guarded term write changed no row. */
    enum WriteRefusal {
        HOLDER_DIFFERS,
        OCCUPIED,
        OTHER
    }

    /**
     * Explains why a guarded leasehold write changed no row. The transaction is ended first: the
     * caller read the contract before writing, so under REPEATABLE READ a plain re-read would
     * return that older snapshot and miss a rent or landlord change committed in between.
     * Nothing is pending when this is called, so the rollback discards nothing.
     *
     * @param expectedLandlordPartyId the landlord's party id that the write required, or
     *                                {@code null} when the write had no landlord condition
     * @param vacantOnly              whether the write required that there be no tenant
     */
    static @NotNull WriteRefusal diagnoseLeaseholdRefusal(@NotNull SqlSessionWrapper wrapper,
                                                          @NotNull String worldGuardRegionId,
                                                          @NotNull UUID worldId,
                                                          @Nullable Integer expectedLandlordPartyId,
                                                          boolean vacantOnly) {
        wrapper.session().rollback(true);
        LeaseholdContractEntity current = wrapper.leaseholdContractMapper()
                .selectByRegion(worldGuardRegionId, worldId);
        if (current == null) {
            return WriteRefusal.OTHER;
        }
        if (expectedLandlordPartyId != null
                && expectedLandlordPartyId != namedPartyId(wrapper, current.landlord())) {
            return WriteRefusal.HOLDER_DIFFERS;
        }
        if (vacantOnly && current.tenantId() != null) {
            return WriteRefusal.OCCUPIED;
        }
        return WriteRefusal.OTHER;
    }

    /**
     * Freehold counterpart of {@link #diagnoseLeaseholdRefusal}; there is no tenancy to check.
     *
     * @param holderConditionApplied whether the write required a title holder at all
     * @param expectedTitleHolderId  the title holder the write required; {@code null} means none
     */
    static @NotNull WriteRefusal diagnoseFreeholdRefusal(@NotNull SqlSessionWrapper wrapper,
                                                         @NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         boolean holderConditionApplied,
                                                         @Nullable UUID expectedTitleHolderId) {
        wrapper.session().rollback(true);
        FreeholdContractEntity current = wrapper.freeholdContractMapper()
                .selectByRegion(worldGuardRegionId, worldId);
        if (current != null && holderConditionApplied
                && !Objects.equals(expectedTitleHolderId, current.titleHolderId())) {
            return WriteRefusal.HOLDER_DIFFERS;
        }
        return WriteRefusal.OTHER;
    }

    /** Who acts for a freehold: its title holder, or its authority while it has none. */
    private static @NotNull Party freeholdHolder(@NotNull FreeholdContractEntity freehold) {
        UUID titleHolderId = freehold.titleHolderId();
        return titleHolderId != null ? Party.personal(titleHolderId) : freehold.authority();
    }

    // --- Set Price ---

    @Override
    public @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             double price,
                                             @NotNull ActorContext ctx,
                                             boolean vacantOnly) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null) {
                if (!ctx.mayManage(freeholdHolder(freehold))) {
                    return new SetPriceResult.NotAuthorized();
                }
                if (wrapper.freeholdContractAuctionMapper().existsByRegion(worldGuardRegionId, worldId)) {
                    return new SetPriceResult.AuctionExists();
                }
                if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                    return new SetPriceResult.OfferPaymentInProgress();
                }
                if (wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                    return new SetPriceResult.BidPaymentInProgress();
                }
                int authorityPartyId = namedPartyId(wrapper, freehold.authority());
                boolean guardTitleHolder = !ctx.bypass();
                wrapper.session().rollback(true); // only reads so far; drop the snapshot so the guard sees committed rows
                int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, price,
                        guardTitleHolder, freehold.titleHolderId());
                if (updated == 0) {
                    if (diagnoseFreeholdRefusal(wrapper, worldGuardRegionId, worldId,
                            guardTitleHolder, freehold.titleHolderId()) == WriteRefusal.HOLDER_DIFFERS) {
                        return new SetPriceResult.NotAuthorized();
                    }
                    return new SetPriceResult.UpdateFailed();
                }
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_PRICE.name(),
                        freehold.titleHolderId(), authorityPartyId, price);
                wrapper.session().commit();
                return new SetPriceResult.Success();
            }
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetPriceResult.NoContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new SetPriceResult.NotAuthorized();
            }
            int landlordPartyId = namedPartyId(wrapper, lease.landlord());
            Integer requiredLandlordPartyId = ctx.bypass() ? null : landlordPartyId;
            wrapper.session().rollback(true); // only reads so far; drop the snapshot so the guard sees committed rows
            int updated = leaseholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, price,
                    requiredLandlordPartyId, vacantOnly);
            if (updated == 0) {
                WriteRefusal refusal = diagnoseLeaseholdRefusal(wrapper, worldGuardRegionId, worldId,
                        requiredLandlordPartyId, vacantOnly);
                if (refusal == WriteRefusal.HOLDER_DIFFERS) {
                    return new SetPriceResult.NotAuthorized();
                }
                if (refusal == WriteRefusal.OCCUPIED) {
                    return new SetPriceResult.Occupied();
                }
                return new SetPriceResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.SET_PRICE.name(),
                    lease.tenantId(), landlordPartyId,
                    price, lease.durationSeconds(), null);
            wrapper.session().commit();
            return new SetPriceResult.Success();
        }
    }

    // --- Unset Price ---

    @Override
    public @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                                  @NotNull UUID worldId,
                                                  @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new UnsetPriceResult.NoFreeholdContract();
            }
            if (!ctx.mayManage(freeholdHolder(freehold))) {
                return new UnsetPriceResult.NotAuthorized();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.OfferPaymentInProgress();
            }
            if (wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.BidPaymentInProgress();
            }
            int authorityPartyId = namedPartyId(wrapper, freehold.authority());
            boolean guardTitleHolder = !ctx.bypass();
            wrapper.session().rollback(true); // only reads so far; drop the snapshot so the guard sees committed rows
            int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null,
                    guardTitleHolder, freehold.titleHolderId());
            if (updated == 0) {
                if (diagnoseFreeholdRefusal(wrapper, worldGuardRegionId, worldId,
                        guardTitleHolder, freehold.titleHolderId()) == WriteRefusal.HOLDER_DIFFERS) {
                    return new UnsetPriceResult.NotAuthorized();
                }
                return new UnsetPriceResult.UpdateFailed();
            }
            double previousPrice = freehold.price() != null ? freehold.price() : 0;
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.UNSET_PRICE.name(),
                    freehold.titleHolderId(), authorityPartyId, previousPrice);
            wrapper.session().commit();
            return new UnsetPriceResult.Success();
        }
    }

    // --- Set Duration ---

    @Override
    public @NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId,
                                                    @NotNull UUID worldId,
                                                    long durationSeconds,
                                                    @NotNull ActorContext ctx,
                                                    boolean vacantOnly) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetDurationResult.NoLeaseholdContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new SetDurationResult.NotAuthorized();
            }
            int landlordPartyId = namedPartyId(wrapper, lease.landlord());
            Integer requiredLandlordPartyId = ctx.bypass() ? null : landlordPartyId;
            wrapper.session().rollback(true); // only reads so far; drop the snapshot so the guard sees committed rows
            int updated = leaseholdMapper.updateDurationByRegion(worldGuardRegionId, worldId, durationSeconds,
                    requiredLandlordPartyId, vacantOnly);
            if (updated == 0) {
                WriteRefusal refusal = diagnoseLeaseholdRefusal(wrapper, worldGuardRegionId, worldId,
                        requiredLandlordPartyId, vacantOnly);
                if (refusal == WriteRefusal.HOLDER_DIFFERS) {
                    return new SetDurationResult.NotAuthorized();
                }
                if (refusal == WriteRefusal.OCCUPIED) {
                    return new SetDurationResult.Occupied();
                }
                return new SetDurationResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.SET_DURATION.name(),
                    lease.tenantId(), landlordPartyId,
                    lease.price(), durationSeconds, null);
            wrapper.session().commit();
            return new SetDurationResult.Success();
        }
    }

    // --- Set Max Renewals ---

    @Override
    public @NotNull SetMaxRenewalsResult setMaxRenewals(@NotNull String worldGuardRegionId,
                                                          @NotNull UUID worldId,
                                                          int maxRenewals,
                                                          @NotNull ActorContext ctx,
                                                          boolean vacantOnly) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetMaxRenewalsResult.NoLeaseholdContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new SetMaxRenewalsResult.NotAuthorized();
            }
            if (maxRenewals >= 0 && lease.tenantId() != null
                    && lease.currentMaxExtensions() != null
                    && maxRenewals < lease.currentMaxExtensions()) {
                return new SetMaxRenewalsResult.BelowCurrentExtensions(lease.currentMaxExtensions());
            }
            int landlordPartyId = namedPartyId(wrapper, lease.landlord());
            Integer requiredLandlordPartyId = ctx.bypass() ? null : landlordPartyId;
            wrapper.session().rollback(true); // only reads so far; drop the snapshot so the guard sees committed rows
            int updated = leaseholdMapper.updateMaxRenewalsByRegion(worldGuardRegionId, worldId, maxRenewals,
                    requiredLandlordPartyId, vacantOnly);
            if (updated == 0) {
                WriteRefusal refusal = diagnoseLeaseholdRefusal(wrapper, worldGuardRegionId, worldId,
                        requiredLandlordPartyId, vacantOnly);
                if (refusal == WriteRefusal.HOLDER_DIFFERS) {
                    return new SetMaxRenewalsResult.NotAuthorized();
                }
                if (refusal == WriteRefusal.OCCUPIED) {
                    return new SetMaxRenewalsResult.Occupied();
                }
                return new SetMaxRenewalsResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.SET_MAX_EXTENSIONS.name(),
                    lease.tenantId(), landlordPartyId,
                    lease.price(), lease.durationSeconds(),
                    maxRenewals < 0 ? null : maxRenewals);
            wrapper.session().commit();
            return new SetMaxRenewalsResult.Success();
        }
    }

    // --- Set Landlord ---


    @Override
    public @NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId,
                                                    @NotNull UUID worldId,
                                                    @NotNull Party newLandlord,
                                                    @NotNull ActorContext ctx) {
        int landlordPartyId = partyIdCreatingIfAbsent(newLandlord);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetLandlordResult.NoLeaseholdContract();
            }
            Party previousLandlord = lease.landlord();
            if (!ctx.mayReassign(previousLandlord)) {
                return new SetLandlordResult.NotAllowedToReassign(previousLandlord);
            }
            if (!ctx.mayManage(newLandlord)) {
                return new SetLandlordResult.NotAllowedToAssign(newLandlord);
            }
            int updated = leaseholdMapper.updateLandlordByRegion(worldGuardRegionId, worldId, landlordPartyId);
            if (updated == 0) {
                return new SetLandlordResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.SET_LANDLORD.name(),
                    lease.tenantId(), landlordPartyId,
                    lease.price(), lease.durationSeconds(), null);
            wrapper.session().commit();
            return new SetLandlordResult.Success(previousLandlord);
        }
    }

    // --- Set Authority ---

    @Override
    public @NotNull SetAuthorityResult setAuthority(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @NotNull Party authority) {
        int authorityPartyId = partyIdCreatingIfAbsent(authority);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetAuthorityResult.NoFreeholdContract();
            }
            Party previousAuthority = freehold.authority();
            int updated = freeholdMapper.updateAuthorityByRegion(worldGuardRegionId, worldId, authorityPartyId);
            if (updated == 0) {
                return new SetAuthorityResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetAuthorityResult.Success(previousAuthority);
        }
    }

    // --- Set Title Holder ---

    @Override
    public @NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         @Nullable UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetTitleHolderResult.NoFreeholdContract();
            }
            UUID previousTitleHolder = freehold.titleHolderId();
            int updated = freeholdMapper.updateTitleHolderByRegion(worldGuardRegionId, worldId, titleHolderId);
            if (updated == 0) {
                return new SetTitleHolderResult.UpdateFailed();
            }
            double historyPrice = freehold.price() != null ? freehold.price() : 0;
            if (titleHolderId != null) {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_TITLEHOLDER.name(),
                        titleHolderId, namedPartyId(wrapper, freehold.authority()), historyPrice);
            } else {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.UNSET_TITLEHOLDER.name(),
                        previousTitleHolder, namedPartyId(wrapper, freehold.authority()), historyPrice);
            }
            wrapper.session().commit();
            return new SetTitleHolderResult.Success(previousTitleHolder);
        }
    }

    // --- Transfer Title Holder ---

    @Override
    public @NotNull SetTitleHolderResult transferTitleHolder(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId,
                                                              @Nullable UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetTitleHolderResult.NoFreeholdContract();
            }
            UUID previousTitleHolder = freehold.titleHolderId();
            int updated = freeholdMapper.updateTitleHolderByRegion(worldGuardRegionId, worldId, titleHolderId);
            if (updated == 0) {
                return new SetTitleHolderResult.UpdateFailed();
            }
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null);
            double historyPrice = freehold.price() != null ? freehold.price() : 0;
            if (titleHolderId != null) {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_TITLEHOLDER.name(),
                        titleHolderId, namedPartyId(wrapper, freehold.authority()), historyPrice);
            } else {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.UNSET_TITLEHOLDER.name(),
                        previousTitleHolder, namedPartyId(wrapper, freehold.authority()), historyPrice);
            }
            wrapper.session().commit();
            return new SetTitleHolderResult.Success(previousTitleHolder);
        }
    }

    // --- Update Subregion Landlords ---

    @Override
    public void updateSubregionLandlords(@NotNull List<String> childRegionIds,
                                          @NotNull UUID worldId,
                                          @NotNull Party newLandlord) {
        if (childRegionIds.isEmpty()) {
            return;
        }
        int landlordPartyId = partyIdCreatingIfAbsent(newLandlord);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            for (String childRegionId : childRegionIds) {
                leaseholdMapper.updateLandlordByRegion(childRegionId, worldId, landlordPartyId);
            }
            wrapper.session().commit();
        }
    }

    // --- Set Tenant ---


    @Override
    public @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @Nullable UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetTenantResult.NoLeaseholdContract();
            }
            UUID previousTenant = lease.tenantId();
            int updated = leaseholdMapper.updateTenantByRegion(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new SetTenantResult.UpdateFailed();
            }
            if (tenantId != null) {
                wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_TENANT.name(),
                        tenantId, namedPartyId(wrapper, lease.landlord()),
                        lease.price(), lease.durationSeconds(), null);
            } else {
                wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.UNSET_TENANT.name(),
                        previousTenant, namedPartyId(wrapper, lease.landlord()),
                        lease.price(), lease.durationSeconds(), null);
            }
            wrapper.session().commit();
            return new SetTenantResult.Success(previousTenant, lease.landlord());
        }
    }

    // --- Buy (fixed-price) ---


    @Override
    public @NotNull BuyResult executeBuy(@NotNull String worldGuardRegionId,
                                          @NotNull UUID worldId,
                                          @NotNull ActorContext buyer,
                                          boolean bypassConflict) {
        UUID buyerId = buyer.requirePlayer();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            // Lock the freehold row first (per-region serialization point), as every
            // offer and bid operation does. Read without it, an offer could be accepted
            // between the check below and the reservation.
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new BuyResult.NoFreeholdContract();
            }
            if (freehold.price() == null) {
                return new BuyResult.NotForFreehold();
            }
            if (conflictsWithAuthority(freehold.authority(), buyer, bypassConflict)) {
                return new BuyResult.IsAuthority();
            }
            if (buyerId.equals(freehold.titleHolderId())) {
                return new BuyResult.IsTitleHolder();
            }
            // Somebody is part-way through paying for this region. It has an asking price
            // only because accepting an offer does not clear one. Sold now, their
            // payment record would go with the offer it belongs to.
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)
                    || wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new BuyResult.NotForFreehold();
            }
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            // Atomic: WHERE price IS NOT NULL prevents concurrent buys
            int updated = freeholdMapper.atomicBuy(worldGuardRegionId, worldId, buyerId);
            if (updated == 0) {
                return new BuyResult.UpdateFailed();
            }
            // Everything the sale takes away is noted before it goes. The buyer has not
            // paid yet, and if they cannot, rollbackBuy puts all of it back. It used to put
            // back the title and the price only, so a buyer who could not pay left a sale
            // in the history and cleared the region of every offer on it.
            List<FreeholdContractOfferEntity> offers =
                    wrapper.freeholdContractOfferMapper().selectByRegion(worldGuardRegionId, worldId);
            List<WithdrawnOffer> withdrawn = offers == null ? List.of() : offers.stream()
                    .map(offer -> new WithdrawnOffer(offer.offererId(), offer.offerPrice(), offer.offerTime()))
                    .toList();
            List<UUID> auctioneers = List.copyOf(wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .selectByRegion(worldGuardRegionId, worldId));
            wrapper.freeholdContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            int historyId = wrapper.freeholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                    HistoryEventType.BUY.name(), buyerId,
                    namedPartyId(wrapper, authority), freehold.price());
            wrapper.session().commit();
            return new BuyResult.Success(freehold.price(), authority, titleHolderId,
                    new BuyUndo(historyId, withdrawn, auctioneers));
        }
    }

    @Override
    public void rollbackBuy(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @NotNull UUID buyerId,
                             @NotNull BuyResult.Success reserved) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            // Only a reservation that still stands. See the interface. The title and the
            // price go back first and cannot be held up by what follows: an offer or an
            // auctioneer that is already there again is left as it is.
            if (freehold != null && buyerId.equals(freehold.titleHolderId())) {
                freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId,
                        reserved.price(), reserved.titleHolderId());
                for (WithdrawnOffer offer : reserved.undo().offers()) {
                    wrapper.freeholdContractOfferMapper().restoreOffer(worldGuardRegionId, worldId,
                            offer.offererId(), offer.offerPrice(), offer.offerTime());
                }
                for (UUID auctioneer : reserved.undo().auctioneers()) {
                    wrapper.freeholdContractSanctionedAuctioneerMapper()
                            .restore(worldGuardRegionId, worldId, auctioneer);
                }
            }
            // By its id, so it is this record and no other, and whether or not the region
            // is still there. One plot's history showed sixteen sales, and the plot had
            // changed hands once.
            wrapper.freeholdHistoryMapper().deleteById(reserved.undo().historyId());
            wrapper.session().commit();
        }
    }

    // --- Create Freehold ---

    @Override
    public boolean createFreehold(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @Nullable Double price,
                              @NotNull Party authority,
                              @Nullable UUID titleHolder) {
        int authorityPartyId = partyIdCreatingIfAbsent(authority);
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            RealtyRegionEntity existing = regionMapper.selectByWorldGuardRegion(worldGuardRegionId, worldId);
            if (existing != null) {
                if (!wrapper.contractMapper().selectByRealtyRegionId(existing.realtyRegionId()).isEmpty()) {
                    return false;
                }
                regionMapper.deleteByRealtyRegionId(existing.realtyRegionId());
            }
            int regionId = regionMapper.registerWorldGuardRegion(worldGuardRegionId, worldId);
            int freeholdContractId = wrapper.freeholdContractMapper().insertFreehold(regionId, price,
                    authorityPartyId, titleHolder);
            wrapper.contractMapper().insert(new ContractEntity(freeholdContractId, "freehold", regionId));
            session.commit();
            return true;
        }
    }

    // --- Create Rental ---

    @Override
    public boolean createLeasehold(@NotNull String worldGuardRegionId,
                                @NotNull UUID worldId,
                                double price,
                                long durationSeconds,
                                int maxRenewals,
                                @NotNull Party landlord) {
        int landlordPartyId = partyIdCreatingIfAbsent(landlord);
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            RealtyRegionEntity existing = regionMapper.selectByWorldGuardRegion(worldGuardRegionId, worldId);
            if (existing != null) {
                if (!wrapper.contractMapper().selectByRealtyRegionId(existing.realtyRegionId()).isEmpty()) {
                    return false;
                }
                regionMapper.deleteByRealtyRegionId(existing.realtyRegionId());
            }
            int regionId = regionMapper.registerWorldGuardRegion(worldGuardRegionId, worldId);
            int leaseholdContractId = wrapper.leaseholdContractMapper().insertLeasehold(regionId, price, durationSeconds, maxRenewals, landlordPartyId, null);
            wrapper.contractMapper().insert(new ContractEntity(leaseholdContractId, "leasehold", regionId));
            session.commit();
            return true;
        }
    }

    // --- Rent ---


    @Override
    public @NotNull RentResult rentRegion(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new RentResult.NoLeaseholdContract();
            }
            if (lease.tenantId() != null) {
                return new RentResult.AlreadyOccupied();
            }
            if (!lease.acceptingTenants()) {
                return new RentResult.NotAcceptingTenants();
            }
            int updated = leaseholdMapper.rentRegion(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new RentResult.UpdateFailed();
            }
            int historyId = wrapper.leaseholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                    HistoryEventType.RENT.name(), tenantId, namedPartyId(wrapper, lease.landlord()),
                    lease.price(), lease.durationSeconds(), null);
            wrapper.session().commit();
            return new RentResult.Success(lease.price(), lease.durationSeconds(), lease.landlord(), historyId);
        }
    }

    // --- Set Rentable ---

    @Override
    public @NotNull SetRentableResult setRentable(@NotNull String worldGuardRegionId,
                                                  @NotNull UUID worldId,
                                                  @NotNull ActorContext ctx,
                                                  boolean accepting) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetRentableResult.NoLeaseholdContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new SetRentableResult.NotAuthorized();
            }
            if (lease.acceptingTenants() == accepting) {
                return new SetRentableResult.NoChange(accepting);
            }
            int updated = leaseholdMapper.updateAcceptingTenantsByRegion(worldGuardRegionId, worldId, accepting);
            if (updated == 0) {
                return new SetRentableResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetRentableResult.Success(accepting);
        }
    }

    @Override
    public void rollbackRent(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @NotNull UUID tenantId,
                             @NotNull RentResult.Success reserved) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            // Only this tenant's reservation. If the region has been let to somebody else
            // since, clearing the tenant would evict them.
            if (lease != null && tenantId.equals(lease.tenantId())) {
                leaseholdMapper.updateTenantByRegion(worldGuardRegionId, worldId, null);
            }
            // The record went in with the tenant, so it comes out here. Left behind, it
            // said the region had been let.
            wrapper.leaseholdHistoryMapper().deleteById(reserved.historyId());
            wrapper.session().commit();
        }
    }

    // --- Unrent ---


    @Override
    public @NotNull UnrentResult unrentRegion(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new UnrentResult.NoLeaseholdContract();
            }
            // A scheduled termination — an eviction in particular — must run its notice period out.
            // Without this guard the tenant could unrent (which clears terminationEffectiveDate along
            // with the tenant) and immediately re-rent, unilaterally cancelling their own eviction.
            if (lease.terminationEffectiveDate() != null) {
                return new UnrentResult.Terminating();
            }
            long totalSeconds = lease.durationSeconds();
            // Pro-rata refund of the UNUSED portion of the current period. Renewals push
            // endDate out by another durationSeconds each (see renewLeasehold) but are not
            // separately escrowed, so the remaining time must be clamped to a single period.
            // Without this clamp a tenant can renew N times and unrent for price*N, draining
            // the landlord's account — never refund more than one period's price.
            long rawRemainingSeconds = lease.endDate() == null ? 0
                    : Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), lease.endDate()).getSeconds());
            long remainingSeconds = Math.min(rawRemainingSeconds, totalSeconds);
            double refund = totalSeconds > 0 ? lease.price() * remainingSeconds / totalSeconds : 0;
            int updated = leaseholdMapper.unrentRegion(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new UnrentResult.UpdateFailed();
            }
            int historyId = wrapper.leaseholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                    HistoryEventType.UNRENT.name(), tenantId, namedPartyId(wrapper, lease.landlord()),
                    lease.price(), lease.durationSeconds(), null);
            wrapper.session().commit();
            // The tenancy as it stood, so that it can be put back as it stood if the
            // refund cannot be paid.
            return new UnrentResult.Success(refund, tenantId, lease.landlord(),
                    new Tenancy(lease.startDate(), lease.endDate(), lease.currentMaxExtensions()), historyId);
        }
    }

    @Override
    public void rollbackUnrent(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @NotNull UUID tenantId,
                               @NotNull UnrentResult.Success ended) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Put back, not let again. Letting the region again started the tenancy from
            // now: a tenant who had paid for three more periods was left with one, and
            // one on their last day was given a whole period for nothing. It was also
            // refused on a region no longer accepting tenants, and the tenant was left
            // with no tenancy and no refund.
            Tenancy previous = ended.previous();
            int restored = wrapper.leaseholdContractMapper().restoreTenancy(worldGuardRegionId, worldId,
                    tenantId, previous.startDate(), previous.endDate(), previous.extensionsUsed());
            // Only if it was put back. If the region has a new tenant already, this
            // tenancy did end, and the record of that is true.
            if (restored > 0) {
                wrapper.leaseholdHistoryMapper().deleteById(ended.historyId());
            }
            wrapper.session().commit();
        }
    }

    // --- Renew Leasehold ---


    @Override
    public @NotNull RenewLeaseholdResult renewLeasehold(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @NotNull UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new RenewLeaseholdResult.NoLeaseholdContract();
            }
            if (lease.terminationEffectiveDate() != null) {
                return new RenewLeaseholdResult.Terminating();
            }
            // Apply any landlord modification that is active for the next cycle, then renew under the
            // new terms. The whole block is committed atomically below, so an early return (e.g. the
            // extension cap now blocks renewal) rolls the application back with the session.
            LeaseholdModificationEntity activeMod = wrapper.leaseholdModificationMapper()
                    .selectActiveByContract(lease.leaseholdContractId());
            boolean modificationApplied = activeMod != null
                    && LeaseholdModificationStatus.ACTIVE.equals(activeMod.status());
            // The terms as they stand, before any change is applied to them. If the
            // renewal is not paid for, rollbackRenewLeasehold puts these back.
            LeaseholdContractEntity before = lease;
            if (modificationApplied) {
                leaseholdMapper.applyModificationTerms(worldGuardRegionId, worldId,
                        activeMod.newPrice(), activeMod.newDurationSeconds(), activeMod.newMaxExtensions());
                wrapper.leaseholdModificationMapper().updateStatus(activeMod.modificationId(),
                        LeaseholdModificationStatus.APPLIED);
                // Re-read so the cap check, history and returned price reflect the new terms.
                lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            }
            // A null count alongside a non-null cap is a broken row (see V15); read it as zero
            // extensions used rather than failing the renewal on an unboxing NPE.
            int extensionsUsed = lease.currentMaxExtensions() == null ? 0 : lease.currentMaxExtensions();
            if (lease.maxExtensions() != null && extensionsUsed >= lease.maxExtensions()) {
                return new RenewLeaseholdResult.NoExtensionsRemaining();
            }
            int updated = leaseholdMapper.renewLeasehold(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new RenewLeaseholdResult.UpdateFailed();
            }
            Integer extensionsRemaining = null;
            if (lease.maxExtensions() != null) {
                extensionsRemaining = lease.maxExtensions() - (extensionsUsed + 1);
            }
            int landlordPartyId = namedPartyId(wrapper, lease.landlord());
            AppliedTerms appliedTerms = null;
            if (modificationApplied) {
                int appliedId = wrapper.leaseholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                        HistoryEventType.MODIFY_APPLY.name(),
                        tenantId, landlordPartyId, lease.price(), lease.durationSeconds(), extensionsRemaining);
                appliedTerms = new AppliedTerms(activeMod.modificationId(), appliedId,
                        before.price(), before.durationSeconds(),
                        before.maxExtensions(), before.currentMaxExtensions());
            }
            int historyId = wrapper.leaseholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                    HistoryEventType.RENEW.name(),
                    tenantId, landlordPartyId, lease.price(), lease.durationSeconds(), extensionsRemaining);
            wrapper.session().commit();
            return new RenewLeaseholdResult.Success(lease.price(), lease.landlord(),
                    new RenewUndo(historyId, appliedTerms));
        }
    }

    @Override
    public void rollbackRenewLeasehold(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull UUID tenantId,
                                        @NotNull RenewLeaseholdResult.Success reserved) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            // Takes off the period the renewal added, which was a period on the new terms
            // if terms were changed, so this comes before the terms are put back.
            int undone = leaseholdMapper.rollbackRenewLeasehold(worldGuardRegionId, worldId, tenantId);
            AppliedTerms applied = reserved.undo().appliedTerms();
            if (undone > 0 && applied != null) {
                // The change was applied so that the renewal could be charged on the new
                // terms. There was no renewal. Left applied, a tenant who renewed without
                // the money had the landlord's new price on a period paid for at the old
                // one, and was refunded at the new price when they left.
                leaseholdMapper.restoreTerms(worldGuardRegionId, worldId,
                        applied.previousPrice(), applied.previousDurationSeconds(),
                        applied.previousMaxExtensions(), applied.previousExtensionsUsed());
                LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
                LeaseholdModificationEntity since = lease == null ? null
                        : wrapper.leaseholdModificationMapper().selectActiveByContract(lease.leaseholdContractId());
                if (since == null) {
                    // Pending again, as it was.
                    wrapper.leaseholdModificationMapper().reactivate(applied.modificationId());
                } else {
                    // Another has been proposed since, and there is only ever one pending.
                    // Had this one still been pending when that was proposed, it would
                    // have been carried forward into it and superseded, so that is done
                    // now. Left marked as applied, a rent rise would be on record as
                    // having taken effect and would never be charged.
                    wrapper.leaseholdModificationMapper()
                            .carryForward(applied.modificationId(), since.modificationId());
                    wrapper.leaseholdModificationMapper().updateStatus(applied.modificationId(),
                            LeaseholdModificationStatus.SUPERSEDED);
                }
                wrapper.leaseholdHistoryMapper().deleteById(applied.historyId());
            }
            wrapper.leaseholdHistoryMapper().deleteById(reserved.undo().historyId());
            wrapper.session().commit();
        }
    }

    // --- Leasehold Modifications ---


    @Override
    public @NotNull ProposeModificationResult proposeModification(@NotNull String worldGuardRegionId,
                                                                  @NotNull UUID worldId,
                                                                  @NotNull ActorContext ctx,
                                                                  @Nullable Double newPrice,
                                                                  @Nullable Long newDurationSeconds,
                                                                  @Nullable Integer newMaxExtensions) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new ProposeModificationResult.NoLeaseholdContract();
            }
            if (lease.tenantId() == null) {
                return new ProposeModificationResult.NotOccupied();
            }
            if (lease.terminationEffectiveDate() != null) {
                return new ProposeModificationResult.Terminating();
            }
            // Derive the proposer's role from the actor: the tenant first, then a manager of the
            // landlord. An admin (bypass) acts as the landlord.
            String proposerRole;
            UUID proposerId;
            if (lease.tenantId().equals(ctx.player())) {
                proposerRole = LeaseholdRoles.TENANT;
                proposerId = lease.tenantId();
            } else if (ctx.mayManage(lease.landlord())) {
                proposerRole = LeaseholdRoles.LANDLORD;
                // The landlord's own UUID while the landlord is a player, as before; the acting
                // player's otherwise, since the column holds a player.
                proposerId = Party.playerUuidOf(lease.landlord()).orElseGet(() -> ctx.requirePlayer());
            } else {
                return new ProposeModificationResult.NotAuthorized();
            }
            LeaseholdModificationEntity existing = wrapper.leaseholdModificationMapper()
                    .selectActiveByContract(lease.leaseholdContractId());
            Double mergedPrice = newPrice;
            Long mergedDuration = newDurationSeconds;
            Integer mergedMax = newMaxExtensions;
            if (existing != null) {
                // Same-role proposals merge (carry forward unspecified fields); a different role supersedes.
                if (existing.proposerRole().equals(proposerRole)) {
                    if (mergedPrice == null) mergedPrice = existing.newPrice();
                    if (mergedDuration == null) mergedDuration = existing.newDurationSeconds();
                    if (mergedMax == null) mergedMax = existing.newMaxExtensions();
                }
                wrapper.leaseholdModificationMapper().updateStatus(existing.modificationId(),
                        LeaseholdModificationStatus.SUPERSEDED);
            }
            String status = LeaseholdRoles.LANDLORD.equals(proposerRole)
                    ? LeaseholdModificationStatus.ACTIVE : LeaseholdModificationStatus.AWAITING_LANDLORD;
            int modificationId = wrapper.leaseholdModificationMapper().insert(lease.leaseholdContractId(),
                    proposerRole, proposerId, mergedPrice, mergedDuration, mergedMax, status);
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.MODIFY_PROPOSE.name(), lease.tenantId(),
                    namedPartyId(wrapper, lease.landlord()),
                    mergedPrice, mergedDuration, mergedMax);
            wrapper.session().commit();
            return new ProposeModificationResult.Success(modificationId, proposerRole,
                    LeaseholdModificationStatus.ACTIVE.equals(status), lease.landlord(), lease.tenantId());
        }
    }

    @Override
    public @NotNull ResolveModificationResult acceptModification(@NotNull String worldGuardRegionId,
                                                                 @NotNull UUID worldId,
                                                                 @NotNull ActorContext ctx) {
        return resolveTenantProposal(worldGuardRegionId, worldId, ctx, true);
    }

    @Override
    public @NotNull ResolveModificationResult rejectModification(@NotNull String worldGuardRegionId,
                                                                 @NotNull UUID worldId,
                                                                 @NotNull ActorContext ctx) {
        return resolveTenantProposal(worldGuardRegionId, worldId, ctx, false);
    }

    private @NotNull ResolveModificationResult resolveTenantProposal(@NotNull String worldGuardRegionId,
                                                                     @NotNull UUID worldId,
                                                                     @NotNull ActorContext ctx,
                                                                     boolean accept) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new ResolveModificationResult.NoLeaseholdContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new ResolveModificationResult.NotAuthorized();
            }
            LeaseholdModificationEntity mod = wrapper.leaseholdModificationMapper()
                    .selectActiveByContract(lease.leaseholdContractId());
            if (mod == null) {
                return new ResolveModificationResult.NoPendingProposal();
            }
            if (!LeaseholdModificationStatus.AWAITING_LANDLORD.equals(mod.status())) {
                return new ResolveModificationResult.NotTenantProposal();
            }
            wrapper.leaseholdModificationMapper().updateStatus(mod.modificationId(),
                    accept ? LeaseholdModificationStatus.ACTIVE : LeaseholdModificationStatus.REJECTED);
            UUID tenantId = lease.tenantId() != null ? lease.tenantId() : mod.proposerId();
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    (accept ? HistoryEventType.MODIFY_ACCEPT : HistoryEventType.MODIFY_REJECT).name(),
                    tenantId, namedPartyId(wrapper, lease.landlord()),
                    mod.newPrice(), mod.newDurationSeconds(), mod.newMaxExtensions());
            wrapper.session().commit();
            return new ResolveModificationResult.Success(mod.modificationId(), tenantId,
                    lease.landlord(), mod.proposerRole());
        }
    }

    @Override
    public @NotNull ResolveModificationResult withdrawModification(@NotNull String worldGuardRegionId,
                                                                   @NotNull UUID worldId,
                                                                   @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new ResolveModificationResult.NoLeaseholdContract();
            }
            LeaseholdModificationEntity mod = wrapper.leaseholdModificationMapper()
                    .selectActiveByContract(lease.leaseholdContractId());
            if (mod == null) {
                return new ResolveModificationResult.NoPendingProposal();
            }
            // Any manager of the landlord may withdraw the landlord's proposal; only its proposer
            // may withdraw a tenant's.
            boolean mayWithdraw = LeaseholdRoles.LANDLORD.equals(mod.proposerRole())
                    ? ctx.mayManage(lease.landlord())
                    : ctx.bypass() || mod.proposerId().equals(ctx.player());
            if (!mayWithdraw) {
                return new ResolveModificationResult.NotAuthorized();
            }
            wrapper.leaseholdModificationMapper().updateStatus(mod.modificationId(),
                    LeaseholdModificationStatus.WITHDRAWN);
            // The history row names the lease's tenant, or no one; never the proposer, who may be
            // the landlord.
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.MODIFY_WITHDRAW.name(), lease.tenantId(),
                    namedPartyId(wrapper, lease.landlord()),
                    mod.newPrice(), mod.newDurationSeconds(), mod.newMaxExtensions());
            wrapper.session().commit();
            return new ResolveModificationResult.Success(mod.modificationId(), lease.tenantId(),
                    lease.landlord(), mod.proposerRole());
        }
    }

    @Override
    public @NotNull List<LeaseholdModificationView> listModificationsAwaitingLandlord(@NotNull Set<Party> landlords) {
        if (landlords.isEmpty()) {
            return List.of();
        }
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // A party with no row is the landlord of nothing, so it is left out rather than inserted.
            List<Integer> landlordPartyIds = new ArrayList<>();
            for (Party landlord : landlords) {
                Integer landlordPartyId = wrapper.partyMapper().findId(landlord);
                if (landlordPartyId != null) {
                    landlordPartyIds.add(landlordPartyId);
                }
            }
            if (landlordPartyIds.isEmpty()) {
                return List.of();
            }
            return wrapper.leaseholdModificationMapper().selectAwaitingByLandlords(landlordPartyIds);
        }
    }

    @Override
    public @NotNull List<LeaseholdModificationView> listPendingModificationsByProposer(@NotNull UUID proposerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdModificationMapper().selectPendingByProposer(proposerId);
        }
    }

    // --- Terminate Leasehold ---


    @Override
    public @NotNull TerminateLeaseholdResult terminateLease(@NotNull String worldGuardRegionId,
                                                            @NotNull UUID worldId,
                                                            @NotNull LocalDateTime newEndDate,
                                                            @NotNull LocalDateTime effectiveDate,
                                                            @NotNull String terminatedByRole) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new TerminateLeaseholdResult.NoLeaseholdContract();
            }
            if (lease.tenantId() == null) {
                return new TerminateLeaseholdResult.NotOccupied();
            }
            if (lease.terminationEffectiveDate() != null) {
                return new TerminateLeaseholdResult.AlreadyTerminating();
            }
            int updated = leaseholdMapper.scheduleTermination(worldGuardRegionId, worldId,
                    newEndDate, effectiveDate, terminatedByRole);
            if (updated == 0) {
                return new TerminateLeaseholdResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.TERMINATE.name(), lease.tenantId(),
                    namedPartyId(wrapper, lease.landlord()),
                    lease.price(), lease.durationSeconds(), null);
            wrapper.session().commit();
            return new TerminateLeaseholdResult.Success(lease.tenantId(), lease.landlord());
        }
    }

    @Override
    public @NotNull CancelTerminationResult cancelTermination(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId,
                                                              @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new CancelTerminationResult.NoLeaseholdContract();
            }
            if (lease.terminationEffectiveDate() == null) {
                return new CancelTerminationResult.NotTerminating();
            }
            String role = lease.terminatedByRole() != null ? lease.terminatedByRole() : LeaseholdRoles.LANDLORD;
            // Only the side that initiated the termination (or an admin) may cancel it; for the
            // landlord, that is any of its managers.
            boolean mayCancel = LeaseholdRoles.TENANT.equals(role)
                    ? ctx.bypass() || (lease.tenantId() != null && lease.tenantId().equals(ctx.player()))
                    : ctx.mayManage(lease.landlord());
            if (!mayCancel) {
                return new CancelTerminationResult.NotAuthorized();
            }
            int updated = leaseholdMapper.clearTermination(worldGuardRegionId, worldId);
            if (updated == 0) {
                return new CancelTerminationResult.UpdateFailed();
            }
            wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                    HistoryEventType.TERMINATION_CANCEL.name(), lease.tenantId(),
                    namedPartyId(wrapper, lease.landlord()),
                    null, null, null);
            wrapper.session().commit();
            // A terminating lease has a tenant unless its row was changed by hand.
            return new CancelTerminationResult.Success(role, lease.landlord(), lease.tenantId());
        }
    }

    // --- Delete ---

    @Override
    public int deleteRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            int deleted = wrapper.realtyRegionMapper().deleteByWorldGuardRegion(worldGuardRegionId, worldId);
            session.commit();
            return deleted;
        }
    }

    // --- Info ---


    @Override
    public @Nullable FreeholdContractEntity getFreeholdContract(@NotNull String worldGuardRegionId,
                                                                  @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
        }
    }

    @Override
    public @Nullable LeaseholdContractEntity getLeaseholdContract(@NotNull String worldGuardRegionId,
                                                                    @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
        }
    }

    @Override
    public @NotNull List<Party> listNonPlayerParties() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.partyMapper().selectNonPersonal();
        }
    }

    @Override
    public @Nullable Party.Group findGroupParty(@NotNull String groupName) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.partyMapper().findGroupParty(groupName);
        }
    }

    @Override
    public @Nullable Party.Account findAccountParty(int accountId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.partyMapper().findAccountParty(accountId);
        }
    }

    // --- Group mapping ---

    @Override
    public @NotNull MapGroupResult mapGroup(@NotNull String groupName, @NotNull Party.Account account) {
        Party.Group mapped = Party.group(groupName, account.accountId(), account.kind());
        try (SqlSessionWrapper wrapper = database.openSession()) {
            PartyMapper partyMapper = wrapper.partyMapper();
            Integer partyId = partyMapper.lockGroupId(mapped.groupName());
            if (partyId == null) {
                // Should another thread insert the same group first, the unique groupName makes
                // this insert fail rather than add a second row.
                partyMapper.insertGroup(mapped.groupName(), account.accountId(), account.kind());
                wrapper.session().commit();
                return new MapGroupResult.Created(mapped);
            }
            if (!(partyMapper.selectById(partyId) instanceof Party.Group previous)) {
                throw new IllegalStateException("party #" + partyId + " is not a group");
            }
            if (previous.equals(mapped)) {
                return new MapGroupResult.NoChange(previous);
            }
            // Updated in place, so the party id every contract and history entry points at stays.
            partyMapper.updateGroupAccount(partyId, account.accountId(), account.kind());
            wrapper.session().commit();
            return new MapGroupResult.Changed(previous, mapped);
        }
    }

    @Override
    public @NotNull UnmapGroupResult unmapGroup(@NotNull String groupName) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            PartyMapper partyMapper = wrapper.partyMapper();
            // The lock keeps a contract or history entry from naming the group between the
            // counts and the delete.
            Integer partyId = partyMapper.lockGroupId(groupName.toLowerCase(Locale.ROOT));
            if (partyId == null) {
                return new UnmapGroupResult.NotMapped();
            }
            if (!(partyMapper.selectById(partyId) instanceof Party.Group group)) {
                throw new IllegalStateException("party #" + partyId + " is not a group");
            }
            int contractCount = partyMapper.countContractsNaming(partyId);
            int historyCount = partyMapper.countHistoryNaming(partyId);
            if (contractCount > 0 || historyCount > 0) {
                return new UnmapGroupResult.StillInUse(contractCount, historyCount);
            }
            partyMapper.deleteGroup(partyId);
            wrapper.session().commit();
            return new UnmapGroupResult.Success(group);
        }
    }

    @Override
    public @NotNull List<GroupMapping> listGroupMappings() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.partyMapper().selectGroupMappings();
        }
    }

    @Override
    public @NotNull RegionInfo getRegionInfo(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper().selectActiveByRegion(worldGuardRegionId, worldId);
            Double lastSoldPrice = freehold != null
                    ? wrapper.freeholdHistoryMapper().selectLastFreeholdPrice(worldGuardRegionId, worldId)
                    : null;
            FreeholdContractBid highestBid = auction != null
                    ? wrapper.freeholdContractBidMapper().selectHighestBid(worldGuardRegionId, worldId)
                    : null;
            return new RegionInfo(freehold, lease, auction, lastSoldPrice, highestBid);
        }
    }

    @Override
    public @Nullable RegionState getRegionState(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null) {
                return freehold.titleHolderId() != null ? RegionState.SOLD : RegionState.FOR_SALE;
            }
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (lease != null) {
                return lease.tenantId() != null ? RegionState.LEASED : RegionState.FOR_LEASE;
            }
            return null;
        }
    }

    @Override
    public boolean isRegistered(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper()
                    .selectByWorldGuardRegion(worldGuardRegionId, worldId) != null;
        }
    }

    /**
     * Builds a placeholder map for the given region, containing all info-level
     * properties: region, title_holder, authority, price, last_sold_price,
     * landlord, tenant, duration, start_date, end_date, extensions, has_auction.
     */
    @Override
    public @NotNull Map<String, String> getRegionPlaceholders(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return getRegionPlaceholders(wrapper, worldGuardRegionId, worldId);
        }
    }

    @NotNull Map<String, String> getRegionPlaceholders(@NotNull SqlSessionWrapper wrapper,
                                                       @NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("region", worldGuardRegionId);

        FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
        if (freehold != null) {
            String titleHolder = freehold.titleHolderId() != null
                    ? partyNameResolver.apply(Party.personal(freehold.titleHolderId())).join() : "";
            placeholders.put("title_holder", titleHolder);
            placeholders.put("titleholder", titleHolder);
            placeholders.put("authority", partyNameResolver.apply(freehold.authority()).join());
            placeholders.put("price", freehold.price() != null ? CurrencyFormatter.format(freehold.price()) : "");
            Double lastSoldPrice = wrapper.freeholdHistoryMapper().selectLastFreeholdPrice(worldGuardRegionId, worldId);
            placeholders.put("last_sold_price", lastSoldPrice != null ? CurrencyFormatter.format(lastSoldPrice) : "");
        }

        LeaseholdContractEntity lease = wrapper.leaseholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
        if (lease != null) {
            placeholders.put("landlord", partyNameResolver.apply(lease.landlord()).join());
            placeholders.put("tenant", lease.tenant().map(tenant -> partyNameResolver.apply(tenant).join()).orElse(""));
            placeholders.put("price", CurrencyFormatter.format(lease.price()));
            placeholders.put("duration", DurationFormatter.format(Duration.ofSeconds(lease.durationSeconds())));
            placeholders.put("start_date", lease.startDate() != null ? dateFormatter.apply(lease.startDate()) : "N/A");
            placeholders.put("end_date", lease.endDate() != null ? dateFormatter.apply(lease.endDate()) : "N/A");
            placeholders.put("time_left", DurationFormatter.formatTimeLeft(lease.endDate()));
            if (lease.maxExtensions() != null) {
                placeholders.put("extensions",
                        (lease.currentMaxExtensions() == null ? 0 : lease.currentMaxExtensions())
                                + "/" + lease.maxExtensions());
            } else {
                placeholders.put("extensions", "unlimited");
            }
        }

        FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                .selectActiveByRegion(worldGuardRegionId, worldId);
        placeholders.put("has_auction", auction != null ? "true" : "false");

        return placeholders;
    }



    @Override
    public @NotNull List<RegionWithState> getAllRegionsWithState() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            List<RealtyRegionEntity> regions = wrapper.realtyRegionMapper().selectAll();
            List<RegionWithState> result = new ArrayList<>();
            for (RealtyRegionEntity region : regions) {
                Map<String, String> placeholders = getRegionPlaceholders(
                        wrapper, region.worldGuardRegionId(), region.worldId());
                FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                        .selectByRegion(region.worldGuardRegionId(), region.worldId());
                if (freehold != null) {
                    result.add(new RegionWithState(region,
                            freehold.titleHolderId() != null ? RegionState.SOLD : RegionState.FOR_SALE,
                            placeholders));
                    continue;
                }
                LeaseholdContractEntity lease = wrapper.leaseholdContractMapper()
                        .selectByRegion(region.worldGuardRegionId(), region.worldId());
                if (lease != null) {
                    result.add(new RegionWithState(region,
                            lease.tenantId() != null ? RegionState.LEASED : RegionState.FOR_LEASE,
                            placeholders));
                }
            }
            return result;
        }
    }

    /**
     * Gets the state and placeholders for a single region.
     *
     * @param worldGuardRegionId the WG region name
     * @param worldId            the world UUID
     * @return the region with its state and placeholders, or null if not found or has no contract
     */
    @Override
    public @Nullable RegionWithState getRegionWithState(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionEntity region = wrapper.realtyRegionMapper()
                    .selectByWorldGuardRegion(worldGuardRegionId, worldId);
            if (region == null) {
                return null;
            }
            Map<String, String> placeholders = getRegionPlaceholders(wrapper, worldGuardRegionId, worldId);
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null) {
                return new RegionWithState(region,
                        freehold.titleHolderId() != null ? RegionState.SOLD : RegionState.FOR_SALE,
                        placeholders);
            }
            LeaseholdContractEntity lease = wrapper.leaseholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (lease != null) {
                return new RegionWithState(region,
                        lease.tenantId() != null ? RegionState.LEASED : RegionState.FOR_LEASE,
                        placeholders);
            }
            return null;
        }
    }

    // --- Add/Remove permission check ---

    @Override
    public boolean checkRegionAuthority(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            UUID playerId = ctx.player();
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            if (playerId != null
                    && freeholdMapper.existsByRegionAndTitleHolder(worldGuardRegionId, worldId, playerId)) {
                return true;
            }
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            if (playerId != null
                    && leaseholdMapper.existsByRegionAndTenant(worldGuardRegionId, worldId, playerId)) {
                return true;
            }
            // Membership alone: the admin bypass grants no authority over a region.
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            return lease != null && ctx.manages().contains(lease.landlord());
        }
    }

    // --- List ---


    @Override
    public @NotNull ListResult listRegions(@NotNull Party target, int limit, int offset) {
        // Only a player holds a title or rents in this version, so for any other party those two
        // categories are empty and only the regions it is the authority or the landlord of are listed.
        UUID playerId = target instanceof Party.Personal personal ? personal.playerUuid() : null;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            // A party with no row names no contract; looking it up must not create the row.
            Integer partyId = wrapper.partyMapper().findId(target);
            int ownedCount = playerId == null ? 0 : regionMapper.countRegionsByTitleHolder(playerId);
            int authorityCount = partyId == null ? 0 : regionMapper.countRegionsByAuthority(partyId);
            int landlordCount = partyId == null ? 0 : regionMapper.countRegionsByLandlord(partyId);
            int rentedCount = playerId == null ? 0 : regionMapper.countRegionsByTenant(playerId);

            // One offset runs over the four categories in order: each takes what is left of the
            // page, starting where the offset falls inside it.
            int remaining = limit;
            int catOffset = offset;

            List<RealtyRegionEntity> owned = remaining > 0 && playerId != null
                    ? regionMapper.selectRegionsByTitleHolder(playerId, remaining, catOffset)
                    : List.of();
            remaining -= owned.size();
            catOffset = Math.max(0, catOffset - ownedCount);

            List<RealtyRegionEntity> authority = remaining > 0 && partyId != null
                    ? regionMapper.selectRegionsByAuthority(partyId, remaining, catOffset)
                    : List.of();
            remaining -= authority.size();
            catOffset = Math.max(0, catOffset - authorityCount);

            List<RealtyRegionEntity> landlord = remaining > 0 && partyId != null
                    ? regionMapper.selectRegionsByLandlord(partyId, remaining, catOffset)
                    : List.of();
            remaining -= landlord.size();
            catOffset = Math.max(0, catOffset - landlordCount);

            List<RealtyRegionEntity> rented = remaining > 0 && playerId != null
                    ? regionMapper.selectRegionsByTenant(playerId, remaining, catOffset)
                    : List.of();

            return new ListResult(ownedCount, authorityCount, landlordCount, rentedCount,
                    owned, authority, landlord, rented);
        }
    }

    @Override
    public @NotNull SingleCategoryResult listOwnedRegions(@NotNull Party target, int limit, int offset) {
        if (!(target instanceof Party.Personal personal)) {
            // Only a player holds a title in stage 1.
            return new SingleCategoryResult(0, List.of());
        }
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByTitleHolder(personal.playerUuid());
            List<RealtyRegionEntity> regions = mapper.selectRegionsByTitleHolder(personal.playerUuid(), limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    @Override
    public @NotNull SingleCategoryResult listAuthorityRegions(@NotNull Party target, int limit, int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer partyId = wrapper.partyMapper().findId(target);
            if (partyId == null) {
                return new SingleCategoryResult(0, List.of());
            }
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByAuthority(partyId);
            List<RealtyRegionEntity> regions = mapper.selectRegionsByAuthority(partyId, limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    @Override
    public @NotNull SingleCategoryResult listLandlordRegions(@NotNull Party target, int limit, int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer partyId = wrapper.partyMapper().findId(target);
            if (partyId == null) {
                return new SingleCategoryResult(0, List.of());
            }
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByLandlord(partyId);
            List<RealtyRegionEntity> regions = mapper.selectRegionsByLandlord(partyId, limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    @Override
    public @NotNull SingleCategoryResult listRentedRegions(@NotNull Party target, int limit, int offset) {
        if (!(target instanceof Party.Personal personal)) {
            // Only a player rents in stage 1.
            return new SingleCategoryResult(0, List.of());
        }
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByTenant(personal.playerUuid());
            List<RealtyRegionEntity> regions = mapper.selectRegionsByTenant(personal.playerUuid(), limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    // --- List Outbound Offers ---

    @Override
    public @NotNull List<OutboundOfferView> listOutboundOffers(@NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferMapper().selectAllByOfferer(offererId);
        }
    }

    // --- List Inbound Offers ---

    @Override
    public @NotNull List<InboundOfferView> listInboundOffers(@NotNull UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferMapper().selectAllByTitleHolder(titleHolderId);
        }
    }

    // --- Withdraw Offer ---


    @Override
    public @NotNull WithdrawOfferResult withdrawOffer(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row (per-region serialization point) so the offer-exists and
            // accepted-payment checks cannot race a concurrent accept before the delete.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null
                    || !wrapper.freeholdContractOfferMapper().existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new WithdrawOfferResult.NoOffer();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new WithdrawOfferResult.OfferAccepted();
            }
            wrapper.freeholdContractOfferMapper().deleteOfferByOfferer(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new WithdrawOfferResult.Success(freehold.titleHolderId());
        }
    }

    // --- Reject Offer ---


    @Override
    public @NotNull RejectOfferResult rejectOffer(@NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId,
                                                      @NotNull ActorContext ctx,
                                                      @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row (per-region serialization point) so the offer's
            // existence and accepted-payment checks cannot race a concurrent accept/pay.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new RejectOfferResult.NoOffer();
            }
            if (!actsForFreehold(wrapper, freehold, worldGuardRegionId, worldId, ctx)) {
                return new RejectOfferResult.NotSanctioned();
            }
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            if (!offerMapper.existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new RejectOfferResult.NoOffer();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new RejectOfferResult.OfferAccepted();
            }
            offerMapper.deleteOfferByOfferer(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new RejectOfferResult.Success(offererId);
        }
    }

    // --- Reject All Offers ---


    @Override
    public @NotNull RejectAllOffersResult rejectAllOffers(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId,
                                                              @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            // Lock the freehold row (per-region serialization point) so the accepted-payment
            // check cannot race a concurrent accept/pay before the bulk delete.
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new RejectAllOffersResult.NoFreeholdContract();
            }
            if (!actsForFreehold(wrapper, freehold, worldGuardRegionId, worldId, ctx)) {
                return new RejectAllOffersResult.NotSanctioned();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new RejectAllOffersResult.OfferAccepted();
            }
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            List<FreeholdContractOfferEntity> offers = offerMapper.selectByRegion(worldGuardRegionId, worldId);
            List<UUID> offererIds = offers != null
                    ? offers.stream().map(FreeholdContractOfferEntity::offererId).toList()
                    : List.of();
            offerMapper.deleteOffers(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return new RejectAllOffersResult.Success(offererIds);
        }
    }

    // --- Place Offer ---


    @Override
    public @NotNull OfferResult placeOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull ActorContext offerer,
                                           double price,
                                           boolean bypassConflict) {
        UUID offererId = offerer.requirePlayer();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();

            // Lock the freehold row for this transaction: the per-region serialization
            // point for the offer lifecycle, so concurrent offer operations (possibly in
            // another process) cannot interleave their checks and mutations.
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new OfferResult.NoFreeholdContract();
            }
            if (!freehold.acceptingOffers()) {
                return new OfferResult.NotAcceptingOffers();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new OfferResult.AuctionExists();
            }
            if (conflictsWithAuthority(freehold.authority(), offerer, bypassConflict)
                    || offererId.equals(freehold.titleHolderId())) {
                return new OfferResult.IsOwner();
            }
            if (offerMapper.existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new OfferResult.AlreadyHasOffer();
            }
            int inserted = offerMapper.insertOffer(worldGuardRegionId, worldId, offererId, price);
            wrapper.session().commit();
            if (inserted == 0) {
                return new OfferResult.InsertFailed();
            }
            return new OfferResult.Success(freehold.titleHolderId());
        }
    }

    // --- Toggle Offers ---


    @Override
    public @NotNull ToggleOffersResult toggleOffers(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @NotNull ActorContext ctx,
                                                     boolean acceptingOffers) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row (the per-region serialization point) so this
            // read-authorize-write cannot interleave with placeOffer, which reads
            // acceptingOffers under the same lock. The UPDATE alone would block behind a
            // holder, but that does not protect this method's own check-then-act.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new ToggleOffersResult.NoFreeholdContract();
            }
            if (!ctx.bypass() && !actsForFreehold(wrapper, freehold, worldGuardRegionId, worldId, ctx)) {
                return new ToggleOffersResult.NotSanctioned();
            }
            int updated = wrapper.freeholdContractMapper().updateAcceptingOffersByRegion(
                    worldGuardRegionId, worldId, acceptingOffers);
            if (updated == 0) {
                return new ToggleOffersResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new ToggleOffersResult.Success(acceptingOffers);
        }
    }

    // --- Accept Offer ---


    @Override
    public @NotNull AcceptOfferResult acceptOffer(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull ActorContext ctx,
                                                   @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();

            // Lock the freehold row first (per-region serialization point) so a concurrent
            // place/pay/withdraw/reject on this region cannot interleave with acceptance.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new AcceptOfferResult.NoOffer();
            }
            if (!actsForFreehold(wrapper, freehold, worldGuardRegionId, worldId, ctx)) {
                return new AcceptOfferResult.NotSanctioned();
            }
            if (offerMapper.selectByOfferer(worldGuardRegionId, worldId, offererId) == null) {
                return new AcceptOfferResult.NoOffer();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AuctionExists();
            }
            if (paymentMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AlreadyAccepted();
            }
            LocalDateTime paymentDeadline = LocalDateTime.now().plusSeconds(offerPaymentDurationSeconds.getAsLong());
            int inserted = paymentMapper.insertPayment(worldGuardRegionId, worldId, offererId, 0, paymentDeadline);
            if (inserted == 0) {
                return new AcceptOfferResult.InsertFailed();
            }
            offerMapper.deleteOtherOffers(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new AcceptOfferResult.Success();
        }
    }

    /**
     * Whether the actor may run the freehold's offers and auctions: a manager of its authority, its
     * titleholder, or a sanctioned auctioneer. Membership alone; the admin bypass plays no part.
     */
    private static boolean actsForFreehold(@NotNull SqlSessionWrapper wrapper,
                                           @NotNull FreeholdContractEntity freehold,
                                           @NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull ActorContext ctx) {
        if (ctx.manages().contains(freehold.authority())) {
            return true;
        }
        UUID playerId = ctx.player();
        return playerId != null
                && (playerId.equals(freehold.titleHolderId())
                    || wrapper.freeholdContractSanctionedAuctioneerMapper()
                            .existsByRegionAndAuctioneer(worldGuardRegionId, worldId, playerId));
    }

    /**
     * Whether the actor may not buy, bid on, make an offer on or be agent for land whose authority is
     * {@code authority}. A player who is the authority is always refused; a manager of the authority
     * is refused unless {@code bypassConflict}. The actor's admin bypass plays no part.
     */
    private static boolean conflictsWithAuthority(@NotNull Party authority,
                                                  @NotNull ActorContext actor,
                                                  boolean bypassConflict) {
        if (Party.personal(actor.requirePlayer()).equals(authority)) {
            return true;
        }
        return !bypassConflict && actor.manages().contains(authority);
    }

    // --- Pay Offer ---


    /** Currency drift tolerance (half a cent) for "fully paid" comparisons on DOUBLE columns. */
    private static final double PAYMENT_EPSILON = 0.005;

    @Override
    public @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull UUID offererId,
                                             double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row first (per-region serialization point) so the payment
            // read-and-increment cannot interleave with accept/finalize on the same region.
            // The Vault transfer happens in the paper layer, outside this lock.
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractOfferPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment == null || !payment.offererId().equals(offererId)) {
                return new PayOfferResult.NoPaymentRecord();
            }
            double amountOwed = payment.offerPrice() - payment.currentPayment();
            if (amount > amountOwed + PAYMENT_EPSILON) {
                return new PayOfferResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = payment.currentPayment() + amount;
            // Optimistic lock: WHERE currentPayment = expected prevents concurrent updates
            int updated = paymentMapper.atomicUpdatePayment(worldGuardRegionId, worldId, offererId,
                    payment.currentPayment(), newTotal);
            if (updated == 0) {
                return new PayOfferResult.NoPaymentRecord();
            }
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            // Record the payment only. Ownership transfer is DEFERRED to
            // finalizeOfferPurchase, which the paper layer calls ONLY after the
            // economy payment succeeds. Committing the transfer here (before the
            // money moved) handed the region over for free when payment failed.
            wrapper.session().commit();
            if (newTotal >= payment.offerPrice() - PAYMENT_EPSILON) {
                return new PayOfferResult.FullyPaid(authority, titleHolderId);
            }
            return new PayOfferResult.Success(newTotal, payment.offerPrice() - newTotal, authority, titleHolderId);
        }
    }

    @Override
    public void finalizeOfferPurchase(@NotNull String worldGuardRegionId,
                                      @NotNull UUID worldId,
                                      @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row first (per-region serialization point) so finalisation
            // cannot interleave with a concurrent pay/reject/withdraw on the same region.
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractOfferPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            // Only finalize a real, fully-paid record for this offerer. Idempotent:
            // a second call (or one after rollback) finds nothing/under-paid and no-ops.
            if (freehold == null || payment == null || !payment.offererId().equals(offererId)
                    || payment.currentPayment() < payment.offerPrice() - PAYMENT_EPSILON) {
                return;
            }
            freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.offerPrice(), offererId);
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null);
            paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.OFFER_BUY.name(),
                    offererId, namedPartyId(wrapper, freehold.authority()), payment.offerPrice());
            wrapper.session().commit();
        }
    }

    @Override
    public void rollbackPayOffer(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID offererId,
                                  double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row (per-region serialization point) so the payment
            // read-and-decrement cannot interleave with accept/finalize on the same region.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractOfferPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment != null && payment.offererId().equals(offererId)) {
                double rolledBack = Math.max(0, payment.currentPayment() - amount);
                paymentMapper.updatePayment(worldGuardRegionId, worldId, offererId, rolledBack);
                wrapper.session().commit();
            }
        }
    }

    // --- Pay Bid ---


    @Override
    public @NotNull PayBidResult payBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID bidderId,
                                         double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            FreeholdContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment == null || !payment.bidderId().equals(bidderId)) {
                return new PayBidResult.NoPaymentRecord();
            }
            if (payment.paymentDeadline().isBefore(LocalDateTime.now())) {
                return new PayBidResult.PaymentExpired();
            }
            double amountOwed = payment.bidPrice() - payment.currentPayment();
            if (amount > amountOwed + PAYMENT_EPSILON) {
                return new PayBidResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = payment.currentPayment() + amount;
            // Optimistic lock: WHERE currentPayment = expected prevents concurrent updates
            int updated = paymentMapper.atomicUpdatePayment(worldGuardRegionId, worldId, bidderId,
                    payment.currentPayment(), newTotal);
            if (updated == 0) {
                return new PayBidResult.NoPaymentRecord();
            }
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            // Record the payment only. Ownership transfer is DEFERRED to
            // finalizeBidPurchase, called by the paper layer ONLY after the
            // economy payment succeeds — so a failed/insufficient payment can
            // never hand over the region (previously it did, for free).
            wrapper.session().commit();
            if (newTotal >= payment.bidPrice() - PAYMENT_EPSILON) {
                return new PayBidResult.FullyPaid(authority, titleHolderId);
            }
            return new PayBidResult.Success(newTotal, payment.bidPrice() - newTotal, authority, titleHolderId);
        }
    }

    @Override
    public void finalizeBidPurchase(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID bidderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            FreeholdContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            // Only finalize a real, fully-paid record for this bidder. Idempotent:
            // a second call (or one after rollback) finds nothing/under-paid and no-ops.
            if (payment == null || !payment.bidderId().equals(bidderId)
                    || payment.currentPayment() < payment.bidPrice() - PAYMENT_EPSILON) {
                return;
            }
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.bidPrice(), bidderId);
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null);
            paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.AUCTION_BUY.name(),
                    bidderId, namedPartyId(wrapper, freehold.authority()), payment.bidPrice());
            wrapper.session().commit();
        }
    }

    @Override
    public void rollbackPayBid(@NotNull String worldGuardRegionId,
                                @NotNull UUID worldId,
                                @NotNull UUID bidderId,
                                double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            FreeholdContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment != null && payment.bidderId().equals(bidderId)) {
                double rolledBack = Math.max(0, payment.currentPayment() - amount);
                paymentMapper.updatePayment(worldGuardRegionId, worldId, bidderId, rolledBack);
                wrapper.session().commit();
            }
        }
    }

    // --- Expired Bidding Auctions ---


    @Override
    public @NotNull List<ExpiredBiddingAuction> clearExpiredBiddingAuctions() {
        List<FreeholdContractAuctionEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.freeholdContractAuctionMapper().selectExpiredBiddingAuctions();
        }
        if (expired == null || expired.isEmpty()) {
            return List.of();
        }
        List<ExpiredBiddingAuction> results = new ArrayList<>();
        for (FreeholdContractAuctionEntity candidate : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();
                // Lock the auction row so settlement cannot interleave with a
                // concurrent bid or cancellation (possibly in another process).
                FreeholdContractAuctionEntity auction =
                        auctionMapper.selectByIdForUpdate(candidate.freeholdContractAuctionId());
                if (auction == null || auction.ended()) {
                    // Cancelled or already settled since the candidate snapshot.
                    wrapper.session().commit();
                    continue;
                }
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(auction.realtyRegionId());
                if (region == null) {
                    auctionMapper.markEnded(auction.freeholdContractAuctionId());
                    wrapper.session().commit();
                    continue;
                }
                // Idempotency: if a winner's payment already exists this auction is
                // already in its payment phase. Do not settle (or charge) twice.
                if (wrapper.freeholdContractBidPaymentMapper()
                        .existsByRegion(region.worldGuardRegionId(), region.worldId())) {
                    wrapper.session().commit();
                    continue;
                }
                FreeholdContractBid highestBid = wrapper.freeholdContractBidMapper()
                        .selectHighestBid(region.worldGuardRegionId(), region.worldId());
                // Re-validate the sliding bidding window under the lock: a bid may
                // have committed after the candidate snapshot, extending it. Bids
                // are strictly ascending, so the highest bid is also the latest.
                LocalDateTime windowStart = highestBid != null ? highestBid.bidTime() : auction.startDate();
                if (LocalDateTime.now().isBefore(windowStart.plusSeconds(auction.biddingDurationSeconds()))) {
                    wrapper.session().commit();
                    continue;
                }
                if (highestBid == null) {
                    auctionMapper.markEnded(auction.freeholdContractAuctionId());
                    wrapper.session().commit();
                    results.add(new ExpiredBiddingAuction(region.worldGuardRegionId(), region.worldId(),
                            null, auction.auctioneerId()));
                } else {
                    LocalDateTime deadline = LocalDateTime.now().plusSeconds(auction.paymentDurationSeconds());
                    auctionMapper.setPaymentDeadline(auction.freeholdContractAuctionId(), deadline);
                    wrapper.freeholdContractBidPaymentMapper().insertPayment(
                            region.worldGuardRegionId(), region.worldId(),
                            highestBid.bidderId(), highestBid.bidAmount(), deadline);
                    wrapper.session().commit();
                    results.add(new ExpiredBiddingAuction(region.worldGuardRegionId(), region.worldId(),
                            highestBid.bidderId(), auction.auctioneerId()));
                }
            }
        }
        return results;
    }

    // --- Expired Bid Payments ---


    @Override
    public @NotNull List<ExpiredBidPayment> clearExpiredBidPayments() {
        List<FreeholdContractBidPaymentEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.freeholdContractBidPaymentMapper().selectAllExpired();
        }
        List<ExpiredBidPayment> refunds = new ArrayList<>();
        for (FreeholdContractBidPaymentEntity payment : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
                FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                paymentMapper.deleteByBidId(payment.bidId());
                String regionName = region != null ? region.worldGuardRegionId() : "unknown";
                UUID worldId = region != null ? region.worldId() : null;
                refunds.add(new ExpiredBidPayment(payment.bidderId(), payment.currentPayment(), regionName, worldId));
                FreeholdContractAuctionEntity auction = auctionMapper.selectById(payment.freeholdContractAuctionId());
                if (auction != null) {
                    LocalDateTime nextDeadline = LocalDateTime.now().plusSeconds(auction.paymentDurationSeconds());
                    paymentMapper.insertNextPayment(
                            payment.freeholdContractAuctionId(),
                            payment.bidderId(),
                            nextDeadline);
                }
                wrapper.session().commit();
            }
        }
        return refunds;
    }

    // --- Expired Offer Payments ---


    @Override
    public @NotNull List<ExpiredOfferPayment> clearExpiredOfferPayments() {
        List<FreeholdContractOfferPaymentEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.freeholdContractOfferPaymentMapper().selectAllExpired();
        }
        List<ExpiredOfferPayment> refunds = new ArrayList<>();
        for (FreeholdContractOfferPaymentEntity payment : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                wrapper.freeholdContractOfferPaymentMapper().deleteByOfferId(payment.offerId());
                wrapper.session().commit();
                String regionName = region != null ? region.worldGuardRegionId() : "unknown";
                UUID worldId = region != null ? region.worldId() : null;
                refunds.add(new ExpiredOfferPayment(payment.offererId(), payment.currentPayment(), regionName, worldId));
            }
        }
        return refunds;
    }

    // --- Expired Leaseholds ---


    @Override
    public @NotNull List<ExpiredLeasehold> clearExpiredLeaseholds() {
        List<ExpiredLeaseholdView> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.leaseholdContractMapper().selectExpiredLeaseholds();
        }
        List<ExpiredLeasehold> results = new ArrayList<>();
        for (ExpiredLeaseholdView lease : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                wrapper.leaseholdContractMapper().clearTenant(lease.leaseholdContractId());
                wrapper.leaseholdHistoryMapper().insert(lease.worldGuardRegionId(), lease.worldId(),
                        HistoryEventType.LEASEHOLD_EXPIRY.name(), lease.tenantId(),
                        namedPartyId(wrapper, lease.landlord()),
                        null, null, null);
                wrapper.session().commit();
                results.add(new ExpiredLeasehold(lease.tenantId(), lease.landlord(),
                        lease.worldGuardRegionId(), lease.worldId()));
            }
        }
        return results;
    }

    // --- Terminated Leaseholds ---


    @Override
    public @NotNull List<TerminatedLeasehold> clearTerminatedLeaseholds() {
        List<TerminatedLeaseholdView> terminated;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            terminated = wrapper.leaseholdContractMapper().selectTerminatedLeaseholds();
        }
        List<TerminatedLeasehold> results = new ArrayList<>();
        for (TerminatedLeaseholdView lease : terminated) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                // Refund the prepaid-but-unused span (endDate − effectiveDate), clamped to one period —
                // the same clamp the manual unrent uses so renew-then-terminate cannot over-refund.
                // A tenanted lease with no endDate has no prepaid span to refund.
                long unusedSeconds = lease.endDate() == null ? 0 : Math.max(0, Duration.between(
                        lease.terminationEffectiveDate(), lease.endDate()).getSeconds());
                long clampedSeconds = Math.min(unusedSeconds, lease.durationSeconds());
                double refund = lease.durationSeconds() > 0
                        ? lease.price() * clampedSeconds / lease.durationSeconds() : 0;
                wrapper.leaseholdContractMapper().clearTenant(lease.leaseholdContractId());
                wrapper.leaseholdHistoryMapper().insert(lease.worldGuardRegionId(), lease.worldId(),
                        HistoryEventType.LEASEHOLD_EXPIRY.name(), lease.tenantId(),
                        namedPartyId(wrapper, lease.landlord()),
                        null, null, null);
                wrapper.session().commit();
                results.add(new TerminatedLeasehold(lease.tenantId(), lease.landlord(),
                        lease.worldGuardRegionId(), lease.worldId(), refund, lease.terminatedByRole()));
            }
        }
        return results;
    }

    // --- Aggregate Statistics ---

    @Override
    public int countAllRegions() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().countAll();
        }
    }

    @Override
    public int countAllFreeholdContracts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractMapper().countAll();
        }
    }

    @Override
    public int countAllLeaseholdContracts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdContractMapper().countAll();
        }
    }

    @Override
    public int countOccupiedFreeholdContracts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractMapper().countOccupied();
        }
    }

    @Override
    public int countOccupiedLeaseholdContracts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdContractMapper().countOccupied();
        }
    }

    @Override
    public int countActiveOffers() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferMapper().countAll();
        }
    }

    @Override
    public int countActiveAuctions() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractAuctionMapper().countActive();
        }
    }

    @Override
    public int countRegionsByAuthority(@NotNull Party authority) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer authorityPartyId = wrapper.partyMapper().findId(authority);
            if (authorityPartyId == null) {
                return 0;
            }
            return wrapper.realtyRegionMapper().countRegionsByAuthority(authorityPartyId);
        }
    }

    @Override
    public @NotNull List<String> listRegionNamesByTitleHolder(@NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().selectRegionNamesByTitleHolder(playerId);
        }
    }

    @Override
    public @NotNull List<String> listRegionNamesByTenant(@NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().selectRegionNamesByTenant(playerId);
        }
    }

    @Override
    public @NotNull List<String> listRegionNamesByLandlord(@NotNull Party landlord) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer landlordPartyId = wrapper.partyMapper().findId(landlord);
            if (landlordPartyId == null) {
                return List.of();
            }
            return wrapper.realtyRegionMapper().selectRegionNamesByLandlord(landlordPartyId);
        }
    }

    @Override
    public int countRegionsByTitleHolder(@NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().countRegionsByTitleHolder(playerId);
        }
    }

    @Override
    public int countRegionsByLandlord(@NotNull Party landlord) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer landlordPartyId = wrapper.partyMapper().findId(landlord);
            if (landlordPartyId == null) {
                return 0;
            }
            return wrapper.realtyRegionMapper().countRegionsByLandlord(landlordPartyId);
        }
    }

    @Override
    public int countRegionsByTenant(@NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.realtyRegionMapper().countRegionsByTenant(playerId);
        }
    }

    @Override
    public int countOccupiedLeaseholdsByLandlord(@NotNull Party landlord) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Integer landlordPartyId = wrapper.partyMapper().findId(landlord);
            if (landlordPartyId == null) {
                return 0;
            }
            return wrapper.leaseholdContractMapper().countOccupiedByLandlord(landlordPartyId);
        }
    }

    @Override
    public long averageLeaseholdDurationSeconds() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdContractMapper().averageLeaseholdDurationSeconds();
        }
    }

    @Override
    public double averageFreeholdPrice() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractMapper().averagePrice();
        }
    }

    @Override
    public double averageLeaseholdPrice() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.leaseholdContractMapper().averagePrice();
        }
    }

    // --- History Search ---


    @Override
    public @NotNull HistoryResult searchHistory(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @Nullable String eventType,
                                                @Nullable LocalDateTime since,
                                                @Nullable UUID playerId,
                                                int limit,
                                                int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            int freeholdCount = wrapper.freeholdHistoryMapper()
                    .countHistory(worldGuardRegionId, worldId, eventType, since, playerId);
            int leaseholdCount = wrapper.leaseholdHistoryMapper()
                    .countHistory(worldGuardRegionId, worldId, eventType, since, playerId);
            int agentCount = wrapper.agentHistoryMapper()
                    .countHistory(worldGuardRegionId, worldId, eventType, since, playerId);
            int totalCount = freeholdCount + leaseholdCount + agentCount;

            List<FreeholdHistoryEntity> freeholdResults = wrapper.freeholdHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);
            List<LeaseholdHistoryEntity> leaseholdResults = wrapper.leaseholdHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);
            List<AgentHistoryEntity> agentResults = wrapper.agentHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);

            List<HistoryEntry> combined = new ArrayList<>(freeholdResults.size() + leaseholdResults.size() + agentResults.size());
            for (FreeholdHistoryEntity e : freeholdResults) {
                combined.add(new HistoryEntry.Freehold(e.eventType(), e.eventTime(),
                        e.buyerId(), e.authority(), e.price()));
            }
            for (LeaseholdHistoryEntity e : leaseholdResults) {
                combined.add(new HistoryEntry.Leasehold(e.eventType(), e.eventTime(),
                        e.tenantId(), e.landlord(), e.price(), e.durationSeconds(), e.extensionsRemaining()));
            }
            for (AgentHistoryEntity e : agentResults) {
                combined.add(new HistoryEntry.Agent(e.eventType(), e.eventTime(),
                        e.agentId(), e.actorId()));
            }
            combined.sort((a, b) -> b.eventTime().compareTo(a.eventTime()));
            return new HistoryResult(combined, totalCount);
        }
    }

    @Override
    public @NotNull List<TagCountEntity> countRegionsPerTag() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.regionTagMapper().selectTagCounts();
        }
    }

    @Override
    public @NotNull StatisticsEntity statistics() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.statisticsMapper().select();
        }
    }

    @Override
    public @NotNull List<String> getAllTagIds() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.regionTagMapper().selectDistinctTagIds();
        }
    }

    @Override
    public @NotNull List<String> getTagIdsByRegion(@NotNull String worldGuardRegionId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.regionTagMapper().selectTagIdsByRegionId(worldGuardRegionId);
        }
    }

    @Override
    public @NotNull List<String> getRegionIdsByTag(@NotNull String tagId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.regionTagMapper().selectRegionIdsByTagId(tagId);
        }
    }

    @Override
    public int countRegionsByTag(@NotNull String tagId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.regionTagMapper().countByTagId(tagId);
        }
    }

    // --- Schematics ---

    @Override
    public boolean storeSchematic(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  byte @NotNull [] data) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            // MariaDB reports two affected rows when ON DUPLICATE KEY UPDATE updates
            // rather than inserts, so a re-capture counts as success too.
            int rows = wrapper.realtySchematicMapper()
                    .upsert(worldGuardRegionId, worldId, data, LocalDateTime.now());
            session.commit();
            return rows > 0;
        }
    }

    @Override
    public byte @Nullable [] getSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtySchematicEntity entity = wrapper.realtySchematicMapper()
                    .selectByWorldGuardRegion(worldGuardRegionId, worldId);
            return entity == null ? null : entity.data();
        }
    }

}
