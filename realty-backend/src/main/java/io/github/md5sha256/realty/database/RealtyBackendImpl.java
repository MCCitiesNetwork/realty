package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Money;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.LeaseholdModificationStatus;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
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
import io.github.md5sha256.realty.database.entity.OfferListingRow;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.PendingRefundEntity;
import io.github.md5sha256.realty.database.entity.RegionFeeEntity;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
import io.github.md5sha256.realty.database.entity.AuctionSort;
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
import io.github.md5sha256.realty.database.mapper.RegionFeeMapper;
import io.github.md5sha256.realty.fees.SaleFeeResolver;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RealtyBackendImpl implements RealtyBackend {

    private final Database database;
    private final Function<Party, CompletableFuture<String>> partyNameResolver;
    private final Function<LocalDateTime, String> dateFormatter;
    private final LongSupplier offerPaymentDurationSeconds;
    private final Supplier<LevyCatalog> levies;
    private final SaleFeeResolver feeResolver;
    private final BooleanSupplier holdingAvailable;
    private final Logger logger;
    /** The auctions already warned about for ending with no holding account, so each is warned once. */
    private final Set<Integer> warnedNoHolding = ConcurrentHashMap.newKeySet();

    /**
     * @param levies           the levies the server defines, read on each use so that a reload takes effect
     * @param holdingAvailable whether a holding account is configured; without one, no auction or
     *                         offer moves to payment, as its instalments would have nowhere to wait
     */
    public RealtyBackendImpl(@NotNull Database database,
                             @NotNull Function<Party, CompletableFuture<String>> partyNameResolver,
                             @NotNull Function<LocalDateTime, String> dateFormatter,
                             @NotNull LongSupplier offerPaymentDurationSeconds,
                             @NotNull Supplier<LevyCatalog> levies,
                             @NotNull BooleanSupplier holdingAvailable,
                             @NotNull Logger logger) {
        this.database = database;
        this.partyNameResolver = partyNameResolver;
        this.dateFormatter = dateFormatter;
        this.offerPaymentDurationSeconds = offerPaymentDurationSeconds;
        this.levies = levies;
        this.feeResolver = new SaleFeeResolver(levies);
        this.holdingAvailable = holdingAvailable;
        this.logger = logger;
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
            // Locked, as setFee locks it, so an agent cannot set a fee while being removed.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            int rows = wrapper.freeholdContractSanctionedAuctioneerMapper()
                    .deleteByRegionAndAuctioneer(worldGuardRegionId, worldId, auctioneerId);
            if (rows > 0) {
                wrapper.agentHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.AGENT_REMOVE.name(), auctioneerId, actorId);
                Integer realtyRegionId = realtyRegionIdOf(wrapper, worldGuardRegionId, worldId);
                if (realtyRegionId != null) {
                    wrapper.regionFeeMapper().deleteBySetByAndRole(realtyRegionId, auctioneerId, ROLE_AGENT);
                }
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
    public @NotNull List<ActiveAuction> listActiveAuctions() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();
            List<ActiveAuction> auctions = new ArrayList<>();
            for (ActiveAuctionRow row : auctionMapper.selectActivePage(null, AuctionSort.ENDING_SOON,
                    Integer.MAX_VALUE, 0)) {
                String regionId = row.worldGuardRegionId();
                FreeholdContractAuctionEntity auction = auctionMapper.selectActiveByRegion(regionId, row.worldId());
                if (auction == null) {
                    continue;
                }
                FreeholdContractBidMapper bidMapper = wrapper.freeholdContractBidMapper();
                FreeholdContractBidPaymentEntity payment = wrapper.freeholdContractBidPaymentMapper()
                        .selectByRegion(regionId, row.worldId());
                // While paying, report the bid being paid for: after a missed payment that is the
                // runner-up's, not the highest.
                auctions.add(payment == null
                        ? new ActiveAuction(regionId, row.worldId(), auction, ActiveAuction.Phase.BIDDING,
                                bidMapper.selectHighestBid(regionId, row.worldId()), toInstant(row.endDate()), null, 0,
                                null)
                        : new ActiveAuction(regionId, row.worldId(), auction, ActiveAuction.Phase.AWAITING_PAYMENT,
                                bidMapper.selectById(payment.bidId()), null, toInstant(payment.paymentDeadline()),
                                payment.currentPayment(), payment.oldRules() ? null
                                        : SaleFeeResolver.lockedQuote(wrapper, SALE_BID, payment.bidId(),
                                                payment.bidPrice(), payment.totalDue())));
            }
            return auctions;
        }
    }

    private static @NotNull Instant toInstant(@NotNull LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }

    @Override
    public @NotNull CancelAuctionResult cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // The freehold first, as payBid takes it, so an instalment cannot commit between the
            // payment being read below and its row being deleted with the auction.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            // Lock the active auction row first so a concurrent bid or settlement
            // (possibly in another process) cannot interleave with the cancel.
            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                    .selectActiveByRegionForUpdate(worldGuardRegionId, worldId);
            if (auction == null) {
                wrapper.session().commit();
                return new CancelAuctionResult(0, List.of());
            }
            List<UUID> bidderIds = wrapper.freeholdContractBidMapper().selectDistinctBidders(worldGuardRegionId, worldId);
            // An auction in its payment phase holds the winner's instalments. The payment row goes
            // with the auction, so what was paid is recorded as owed back first.
            FreeholdContractBidPaymentEntity payment = wrapper.freeholdContractBidPaymentMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (payment != null && payment.freeholdContractAuctionId() == auction.freeholdContractAuctionId()) {
                wrapper.saleFeeMapper().deleteUnpaidBySale(SALE_BID, payment.bidId());
                if (!payment.oldRules() && payment.currentPayment() > 0) {
                    wrapper.pendingRefundMapper().insert(payment.bidderId(), payment.currentPayment(),
                            worldGuardRegionId, worldId, REFUND_AUCTION_CANCELLED);
                }
                warnPendingDropped(worldGuardRegionId, payment.bidderId(), payment.pendingPayment(),
                        payment.oldRules(), "the auction was cancelled");
            }
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
        bidAmount = Money.cents(bidAmount);
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
            // The sliding bidding window, as settlement checks it: once it has passed, the auction
            // only waits for the scheduler to settle it, and a late bid must not reopen it.
            FreeholdContractBid highestBid = bidMapper.selectHighestBid(worldGuardRegionId, worldId);
            LocalDateTime windowStart = highestBid != null ? highestBid.bidTime() : auction.startDate();
            if (!LocalDateTime.now().isBefore(windowStart.plusSeconds(auction.biddingDurationSeconds()))) {
                return new BidResult.BiddingClosed();
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
     * Ends the transaction that the caller's reads opened, so that the next statement starts a
     * new one. Call it only while the session has changed no row.
     * <p>
     * A setter reads the contract, checks who may change it, and then runs an {@code UPDATE} whose
     * {@code WHERE} clause repeats that check against the stored row. The first read opens a
     * transaction and fixes its view of the database. If another transaction then commits a change
     * to the same contract (a tenant rents, the landlord changes), MariaDB 11.7 does not run the
     * {@code UPDATE} against the changed row: it fails the statement with "Record has changed
     * since last read". Once the transaction is ended, the {@code UPDATE} sees the committed row
     * and changes none, and the setter can answer that the caller no longer holds the region or
     * that it is now occupied. A read that follows such a refusal needs the same, because inside
     * the old transaction it would return the row as it was first read.
     * <p>
     * The rollback undoes nothing, because nothing has been written: it is only the way to end
     * the transaction. It is forced ({@code true}) because MyBatis sends no rollback for a session
     * that has changed no row.
     */
    static void endRead(@NotNull SqlSessionWrapper wrapper) {
        wrapper.session().rollback(true);
    }

    /**
     * Explains why a guarded leasehold write changed no row. It ends the read first
     * ({@link #endRead}), so that it sees a rent or landlord change committed since the caller
     * read the contract.
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
        endRead(wrapper);
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
     * @param expectedAuthorityPartyId the authority's party id that the write required, or
     *                               {@code null} when it had no authority condition
     */
    static @NotNull WriteRefusal diagnoseFreeholdRefusal(@NotNull SqlSessionWrapper wrapper,
                                                         @NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         boolean holderConditionApplied,
                                                         @Nullable UUID expectedTitleHolderId,
                                                         @Nullable Integer expectedAuthorityPartyId) {
        endRead(wrapper);
        FreeholdContractEntity current = wrapper.freeholdContractMapper()
                .selectByRegion(worldGuardRegionId, worldId);
        if (current != null && holderConditionApplied
                && !Objects.equals(expectedTitleHolderId, current.titleHolderId())) {
            return WriteRefusal.HOLDER_DIFFERS;
        }
        if (current != null && expectedAuthorityPartyId != null
                && expectedAuthorityPartyId != namedPartyId(wrapper, current.authority())) {
            return WriteRefusal.HOLDER_DIFFERS;
        }
        return WriteRefusal.OTHER;
    }

    /** Who acts for a freehold: its title holder, or its authority while it has none. */
    private static @NotNull Party freeholdHolder(@NotNull FreeholdContractEntity freehold) {
        UUID titleHolderId = freehold.titleHolderId();
        return titleHolderId != null ? Party.personal(titleHolderId) : freehold.authority();
    }

    /**
     * The authority condition for a guarded freehold write: while the freehold has no title holder
     * the authority is the one who acts for it, so it must still be the authority that was checked.
     * A title holder's authority to act does not depend on the authority, and a bypassing actor
     * is held to nothing.
     */
    private static @Nullable Integer requiredAuthorityPartyId(@NotNull ActorContext ctx,
                                                              @NotNull FreeholdContractEntity freehold,
                                                              int authorityPartyId) {
        return !ctx.bypass() && freehold.titleHolderId() == null ? authorityPartyId : null;
    }

    // --- Set Price ---

    @Override
    public @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             double price,
                                             @NotNull ActorContext ctx,
                                             boolean vacantOnly) {
        price = Money.cents(price);
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
                Integer requiredAuthorityPartyId = requiredAuthorityPartyId(ctx, freehold, authorityPartyId);
                endRead(wrapper);
                int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, price,
                        guardTitleHolder, freehold.titleHolderId(), requiredAuthorityPartyId);
                if (updated == 0) {
                    if (diagnoseFreeholdRefusal(wrapper, worldGuardRegionId, worldId,
                            guardTitleHolder, freehold.titleHolderId(),
                            requiredAuthorityPartyId) == WriteRefusal.HOLDER_DIFFERS) {
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
            endRead(wrapper);
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
            Integer requiredAuthorityPartyId = requiredAuthorityPartyId(ctx, freehold, authorityPartyId);
            endRead(wrapper);
            int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null,
                    guardTitleHolder, freehold.titleHolderId(), requiredAuthorityPartyId);
            if (updated == 0) {
                if (diagnoseFreeholdRefusal(wrapper, worldGuardRegionId, worldId,
                        guardTitleHolder, freehold.titleHolderId(),
                        requiredAuthorityPartyId) == WriteRefusal.HOLDER_DIFFERS) {
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
            endRead(wrapper);
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
            endRead(wrapper);
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
                                                    @NotNull ActorContext ctx,
                                                    boolean vacantOnly) {
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
            if (newLandlord.equals(previousLandlord)) {
                return new SetLandlordResult.NoChange();
            }
            int previousLandlordPartyId = namedPartyId(wrapper, previousLandlord);
            Integer requiredLandlordPartyId = ctx.bypass() ? null : previousLandlordPartyId;
            endRead(wrapper);
            int updated = leaseholdMapper.updateLandlordByRegion(worldGuardRegionId, worldId, landlordPartyId,
                    requiredLandlordPartyId, vacantOnly);
            if (updated == 0) {
                WriteRefusal refusal = diagnoseLeaseholdRefusal(wrapper, worldGuardRegionId, worldId,
                        requiredLandlordPartyId, vacantOnly);
                if (refusal == WriteRefusal.HOLDER_DIFFERS) {
                    LeaseholdContractEntity current = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
                    if (current == null) {
                        return new SetLandlordResult.UpdateFailed();
                    }
                    return new SetLandlordResult.NotAllowedToReassign(current.landlord());
                }
                if (refusal == WriteRefusal.OCCUPIED) {
                    return new SetLandlordResult.Occupied();
                }
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
                                                         @Nullable UUID titleHolderId,
                                                         @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetTitleHolderResult.NoFreeholdContract();
            }
            if (!ctx.mayManage(freeholdHolder(freehold))) {
                return new SetTitleHolderResult.NotAuthorized();
            }
            UUID previousTitleHolder = freehold.titleHolderId();
            if (Objects.equals(titleHolderId, previousTitleHolder)) {
                return new SetTitleHolderResult.NoChange();
            }
            if (saleInPayment(wrapper, worldGuardRegionId, worldId)) {
                return new SetTitleHolderResult.SaleInPayment();
            }
            int authorityPartyId = namedPartyId(wrapper, freehold.authority());
            boolean guardTitleHolder = !ctx.bypass();
            Integer requiredAuthorityPartyId = requiredAuthorityPartyId(ctx, freehold, authorityPartyId);
            endRead(wrapper);
            int updated = freeholdMapper.updateTitleHolderByRegion(worldGuardRegionId, worldId, titleHolderId,
                    guardTitleHolder, previousTitleHolder, requiredAuthorityPartyId);
            if (updated == 0) {
                if (diagnoseFreeholdRefusal(wrapper, worldGuardRegionId, worldId,
                        guardTitleHolder, previousTitleHolder,
                        requiredAuthorityPartyId) == WriteRefusal.HOLDER_DIFFERS) {
                    return new SetTitleHolderResult.NotAuthorized();
                }
                return new SetTitleHolderResult.UpdateFailed();
            }
            double historyPrice = freehold.price() != null ? freehold.price() : 0;
            if (titleHolderId != null) {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_TITLEHOLDER.name(),
                        titleHolderId, authorityPartyId, historyPrice);
            } else {
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.UNSET_TITLEHOLDER.name(),
                        previousTitleHolder, authorityPartyId, historyPrice);
            }
            onTitleChanged(wrapper, requireRealtyRegionId(wrapper, worldGuardRegionId, worldId),
                    previousTitleHolder, titleHolderId);
            wrapper.session().commit();
            return new SetTitleHolderResult.Success(previousTitleHolder);
        }
    }

    /**
     * Whether a buyer is paying for the region through a won auction or an accepted offer. Its
     * proceeds line is locked to the seller of the moment, so the title must not change hands.
     */
    private static boolean saleInPayment(@NotNull SqlSessionWrapper wrapper, @NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId) {
        return wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)
                || wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId);
    }

    // --- Transfer Title Holder ---

    @Override
    public @NotNull SetTitleHolderResult transferTitleHolder(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId,
                                                              @Nullable UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetTitleHolderResult.NoFreeholdContract();
            }
            UUID previousTitleHolder = freehold.titleHolderId();
            if (titleHolderId != null && titleHolderId.equals(previousTitleHolder)) {
                return new SetTitleHolderResult.NoChange();
            }
            if (saleInPayment(wrapper, worldGuardRegionId, worldId)) {
                return new SetTitleHolderResult.SaleInPayment();
            }
            int updated = freeholdMapper.updateTitleHolderByRegion(worldGuardRegionId, worldId, titleHolderId,
                    false, null, null);
            if (updated == 0) {
                return new SetTitleHolderResult.UpdateFailed();
            }
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null, null);
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
            onTitleChanged(wrapper, requireRealtyRegionId(wrapper, worldGuardRegionId, worldId),
                    previousTitleHolder, titleHolderId);
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
                leaseholdMapper.updateLandlordByRegion(childRegionId, worldId, landlordPartyId, null, false);
            }
            wrapper.session().commit();
        }
    }

    // --- Set Tenant ---


    @Override
    public @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @Nullable UUID tenantId,
                                                @NotNull ActorContext ctx,
                                                boolean vacantOnly) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            LeaseholdContractEntity lease = leaseholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetTenantResult.NoLeaseholdContract();
            }
            if (!ctx.mayManage(lease.landlord())) {
                return new SetTenantResult.NotAuthorized();
            }
            UUID previousTenant = lease.tenantId();
            if (Objects.equals(tenantId, previousTenant)) {
                return new SetTenantResult.NoChange();
            }
            int landlordPartyId = namedPartyId(wrapper, lease.landlord());
            Integer requiredLandlordPartyId = ctx.bypass() ? null : landlordPartyId;
            endRead(wrapper);
            int updated = leaseholdMapper.updateTenantByRegion(worldGuardRegionId, worldId, tenantId,
                    requiredLandlordPartyId, vacantOnly);
            if (updated == 0) {
                WriteRefusal refusal = diagnoseLeaseholdRefusal(wrapper, worldGuardRegionId, worldId,
                        requiredLandlordPartyId, vacantOnly);
                if (refusal == WriteRefusal.HOLDER_DIFFERS) {
                    return new SetTenantResult.NotAuthorized();
                }
                if (refusal == WriteRefusal.OCCUPIED) {
                    return new SetTenantResult.Occupied();
                }
                return new SetTenantResult.UpdateFailed();
            }
            if (tenantId != null) {
                wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.SET_TENANT.name(),
                        tenantId, landlordPartyId,
                        lease.price(), lease.durationSeconds(), null);
            } else {
                wrapper.leaseholdHistoryMapper().insert(worldGuardRegionId, worldId,
                        HistoryEventType.UNSET_TENANT.name(),
                        previousTenant, landlordPartyId,
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
            // Before the title passes, so the buyer's plot count does not include this region. No
            // agent runs a buy, so no agent fee applies. Nothing is saved until recordBuyFees.
            FeeQuote fees = feeResolver.resolve(wrapper, requireRegion(wrapper, worldGuardRegionId, worldId),
                    freehold, buyerId, freehold.price(), null);
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
            // The fees are left as they are: rollbackBuy cannot put deleted fees back, and a buy
            // that fails must leave the seller's fees standing. The fee clean-up for a buy runs
            // once its money has moved.
            int historyId = wrapper.freeholdHistoryMapper().insertReturningId(worldGuardRegionId, worldId,
                    HistoryEventType.BUY.name(), buyerId,
                    namedPartyId(wrapper, authority), freehold.price());
            wrapper.session().commit();
            return new BuyResult.Success(freehold.price(), authority, titleHolderId,
                    new BuyUndo(historyId, withdrawn, auctioneers), fees);
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

    @Override
    public void recordBuyFees(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @NotNull UUID buyerId,
                              @NotNull Party seller,
                              @NotNull FeeQuote quote) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            Integer realtyRegionId = realtyRegionIdOf(wrapper, worldGuardRegionId, worldId);
            feeResolver.recordPaidFees(wrapper, SALE_BUY, realtyRegionId, worldGuardRegionId, worldId,
                    buyerId, seller, quote);
            // The clean-up executeBuy leaves undone, because rollbackBuy cannot put deleted fees back.
            if (realtyRegionId != null) {
                deleteAgentFees(wrapper, realtyRegionId);
                onTitleChanged(wrapper, realtyRegionId, Party.playerUuidOf(seller).orElse(null), buyerId);
            }
            // Forced: the fee lines are INSERT ... RETURNING statements mapped as selects, and
            // MyBatis does not commit a session it believes only read.
            wrapper.session().commit(true);
        }
    }

    @Override
    public @NotNull FeeQuote previewFees(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID buyerId,
                                         double price) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return FeeQuote.none(price);
            }
            // The auction's end locks the fees with its auctioneer as the agent, so a preview
            // during one shows their fee too.
            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                    .selectActiveByRegion(worldGuardRegionId, worldId);
            return feeResolver.resolve(wrapper, requireRegion(wrapper, worldGuardRegionId, worldId),
                    freehold, buyerId, price, auction != null ? auction.auctioneerId() : null);
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
                leaseholdMapper.updateTenantByRegion(worldGuardRegionId, worldId, null, null, false);
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
            // The freehold first, as payBid and payOffer take it, so an instalment committing
            // concurrently is either in the payment read below or waits and then finds no region.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            // A sale under way falls through with the region. What its buyer paid is held for
            // them, so it is recorded as owed back; under the old rules it went to the seller.
            FreeholdContractBidPaymentEntity bidPayment = wrapper.freeholdContractBidPaymentMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (bidPayment != null) {
                wrapper.saleFeeMapper().deleteUnpaidBySale(SALE_BID, bidPayment.bidId());
                if (!bidPayment.oldRules() && bidPayment.currentPayment() > 0) {
                    wrapper.pendingRefundMapper().insert(bidPayment.bidderId(), bidPayment.currentPayment(),
                            worldGuardRegionId, worldId, REFUND_REGION_DELETED);
                }
                warnPendingDropped(worldGuardRegionId, bidPayment.bidderId(), bidPayment.pendingPayment(),
                        bidPayment.oldRules(), "the region was deleted");
            }
            FreeholdContractOfferPaymentEntity offerPayment = wrapper.freeholdContractOfferPaymentMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (offerPayment != null) {
                wrapper.saleFeeMapper().deleteUnpaidBySale(SALE_OFFER, offerPayment.offerId());
                if (!offerPayment.oldRules() && offerPayment.currentPayment() > 0) {
                    wrapper.pendingRefundMapper().insert(offerPayment.offererId(), offerPayment.currentPayment(),
                            worldGuardRegionId, worldId, REFUND_REGION_DELETED);
                }
                warnPendingDropped(worldGuardRegionId, offerPayment.offererId(), offerPayment.pendingPayment(),
                        offerPayment.oldRules(), "the region was deleted");
            }
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
        price = Money.cents(price);
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
    public @NotNull List<OfferListing> listOfferListings() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Map<String, List<InboundOfferView>> offersByRegion = new LinkedHashMap<>();
            for (InboundOfferView offer : wrapper.freeholdContractOfferMapper().selectAllOpen()) {
                offersByRegion.computeIfAbsent(regionKey(offer.worldGuardRegionId(), offer.worldId()),
                        ignored -> new ArrayList<>()).add(offer);
            }
            List<OfferListing> listings = new ArrayList<>();
            for (OfferListingRow row : wrapper.freeholdContractMapper().selectOfferListings()) {
                List<InboundOfferView> offers = List.copyOf(offersByRegion.getOrDefault(
                        regionKey(row.worldGuardRegionId(), row.worldId()), List.of()));
                FeeQuote fees = null;
                if (offers.stream().anyMatch(InboundOfferView::accepted)) {
                    FreeholdContractOfferPaymentEntity payment = wrapper.freeholdContractOfferPaymentMapper()
                            .selectByRegion(row.worldGuardRegionId(), row.worldId());
                    if (payment != null && !payment.oldRules()) {
                        fees = SaleFeeResolver.lockedQuote(wrapper, SALE_OFFER, payment.offerId(),
                                payment.offerPrice(), payment.totalDue());
                    }
                }
                listings.add(new OfferListing(row.worldGuardRegionId(), row.worldId(), row.authority(),
                        row.titleHolderId() == null ? null : Party.personal(row.titleHolderId()), row.price(),
                        offers, fees));
            }
            return listings;
        }
    }

    private static @NotNull String regionKey(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        return worldId + "/" + worldGuardRegionId;
    }

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
            if (freehold.acceptingOffers() == acceptingOffers) {
                return new ToggleOffersResult.NoChange();
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
            FreeholdContractOfferEntity offer = offerMapper.selectByOfferer(worldGuardRegionId, worldId, offererId);
            if (offer == null) {
                return new AcceptOfferResult.NoOffer();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AuctionExists();
            }
            if (paymentMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AlreadyAccepted();
            }
            if (!holdingAvailable.getAsBoolean()) {
                return new AcceptOfferResult.NoHoldingAccount();
            }
            // The fees lock now, with the agent fee of whoever accepted.
            RealtyRegionEntity region = requireRegion(wrapper, worldGuardRegionId, worldId);
            FeeQuote fees = feeResolver.resolve(wrapper, region, freehold, offererId, offer.offerPrice(), ctx.player());
            LocalDateTime paymentDeadline = LocalDateTime.now().plusSeconds(offerPaymentDurationSeconds.getAsLong());
            int inserted = paymentMapper.insertPayment(worldGuardRegionId, worldId, offererId, offer.offerPrice(),
                    paymentDeadline, fees.buyerTotal(), ctx.player());
            if (inserted == 0) {
                return new AcceptOfferResult.InsertFailed();
            }
            feeResolver.lockFees(wrapper, SALE_OFFER, offer.offerId(), region, offererId, freeholdHolder(freehold), fees);
            offerMapper.deleteOtherOffers(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new AcceptOfferResult.Success(offer.offerPrice(), fees.buyerTotal(), paymentDeadline);
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


    /** The {@code SaleFee.saleKind} values. */
    private static final String SALE_BUY = "buy";
    private static final String SALE_BID = "bid";
    private static final String SALE_OFFER = "offer";
    /** The {@code PendingRefund.reason} of instalments held for a sale whose region was deleted. */
    private static final String REFUND_REGION_DELETED = "region-deleted";
    /** The {@code PendingRefund.reason} of instalments held for an auction cancelled during payment. */
    private static final String REFUND_AUCTION_CANCELLED = "auction-cancelled";
    /** The {@code PendingRefund.reason} of instalments held for a payment that expired. */
    private static final String REFUND_BID_EXPIRED = "bid-expired";
    private static final String REFUND_OFFER_EXPIRED = "offer-expired";
    /** The {@code PendingRefund.reason} of an instalment whose money arrived after its sale ended. */
    private static final String REFUND_SALE_ENDED = "sale-ended";

    @Override
    public @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull UUID offererId,
                                             double amount) {
        amount = Money.cents(amount);
        if (amount <= 0) {
            return new PayOfferResult.InvalidAmount();
        }
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
            if (payment.pendingPayment() > 0) {
                // The money of an earlier instalment has not been confirmed as moved.
                return new PayOfferResult.PaymentInProgress();
            }
            if (payment.paymentDeadline().isBefore(LocalDateTime.now())) {
                return new PayOfferResult.PaymentExpired();
            }
            // In whole cents, so that a fraction of a cent can neither exceed nor complete it.
            double amountOwed = Money.minus(payment.totalDue(), payment.currentPayment());
            if (amount > amountOwed) {
                return new PayOfferResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = Money.plus(payment.currentPayment(), amount);
            // Counted as pending until the paper layer confirms that its money moved. Optimistic
            // lock: the row must be unchanged and have nothing else pending.
            int updated = paymentMapper.recordPending(payment.offerId(), payment.currentPayment(), amount);
            if (updated == 0) {
                return new PayOfferResult.NoPaymentRecord();
            }
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            // Record the payment only. Ownership transfer is DEFERRED to
            // finalizeOfferPurchase, which the paper layer calls ONLY after the
            // economy payment succeeds. Committing the transfer here (before the
            // money moved) handed the region over for free when payment failed.
            if (Money.atLeast(newTotal, payment.totalDue())) {
                FeeQuote fees = payment.oldRules()
                        ? FeeQuote.none(payment.offerPrice())
                        : SaleFeeResolver.lockedQuote(wrapper, SALE_OFFER, payment.offerId(),
                                payment.offerPrice(), payment.totalDue());
                wrapper.session().commit();
                return new PayOfferResult.FullyPaid(authority, titleHolderId, fees, payment.oldRules(), payment.offerId());
            }
            wrapper.session().commit();
            return new PayOfferResult.Success(newTotal, Money.minus(payment.totalDue(), newTotal), authority, titleHolderId,
                    payment.oldRules());
        }
    }

    @Override
    public boolean finalizeOfferPurchase(@NotNull String worldGuardRegionId,
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
                    || payment.pendingPayment() > 0
                    || !Money.atLeast(payment.currentPayment(), payment.totalDue())) {
                return false;
            }
            freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.offerPrice(), offererId);
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null, null);
            paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
            wrapper.saleFeeMapper().markCompleted(SALE_OFFER, payment.offerId());
            wrapper.freeholdContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            deleteAgentFees(wrapper, realtyRegionId);
            onTitleChanged(wrapper, realtyRegionId, freehold.titleHolderId(), offererId);
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.OFFER_BUY.name(),
                    offererId, namedPartyId(wrapper, freehold.authority()), payment.offerPrice());
            wrapper.session().commit();
            return true;
        }
    }

    @Override
    public void rollbackPayOffer(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID offererId,
                                  double amount) {
        amount = Money.cents(amount);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row (per-region serialization point) so the payment
            // read-and-decrement cannot interleave with accept/finalize on the same region.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            // Nothing was confirmed, so only the pending instalment is dropped. A payment gone
            // meanwhile refunded only confirmed money, so there is nothing more to undo.
            paymentMapper.clearPending(worldGuardRegionId, worldId, offererId, amount);
            wrapper.session().commit(true);
        }
    }

    @Override
    public boolean confirmPayOffer(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                   @NotNull UUID offererId, double amount, boolean oldRules) {
        amount = Money.cents(amount);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            boolean confirmed = paymentMapper.confirmPending(worldGuardRegionId, worldId, offererId, amount) == 1;
            if (!confirmed) {
                instalmentWithoutSale(wrapper, offererId, worldGuardRegionId, worldId, amount, oldRules);
            }
            wrapper.session().commit(true);
            return confirmed;
        }
    }

    /**
     * A payment deleted with an instalment still pending loses the only record of it. If its
     * transfer is still running, its confirm records what happens next; if it was stuck after a
     * crash, only this log is left.
     */
    private void warnPendingDropped(@NotNull String worldGuardRegionId, @NotNull UUID buyerId, double pending,
                                    boolean oldRules, @NotNull String why) {
        if (pending <= 0) {
            return;
        }
        logger.severe("A payment for " + worldGuardRegionId + " by " + buyerId + " was removed because " + why
                + " while an instalment of " + CurrencyFormatter.format(pending) + " was pending, so its money"
                + " may or may not have moved. If the transfer is still running, its outcome is handled when it"
                + " finishes; if the instalment was stuck, check the economy's ledger and settle it by hand"
                + (oldRules ? " between buyer and seller (old rules: it was paid to the seller)."
                        : " (refund the buyer from the holding account if it arrived)."));
    }

    /**
     * An instalment's money moved, but its payment is gone: cancelled or its region deleted while
     * the money moved. Those refunded only confirmed money, so this amount is recorded as owed
     * back. Under the old rules it went to the seller, never to the holding account, so nothing is
     * owed from there and only a log is left for an admin.
     */
    private void instalmentWithoutSale(@NotNull SqlSessionWrapper wrapper, @NotNull UUID buyerId,
                                       @NotNull String worldGuardRegionId, @NotNull UUID worldId, double amount,
                                       boolean oldRules) {
        if (oldRules) {
            logger.severe("An instalment of " + CurrencyFormatter.format(amount) + " by " + buyerId + " towards "
                    + worldGuardRegionId + " was paid to the seller under the old rules, but its sale ended while"
                    + " the money moved. Nothing is refunded from the holding account, which never received it;"
                    + " settle it between buyer and seller by hand.");
            return;
        }
        wrapper.pendingRefundMapper().insert(buyerId, amount, worldGuardRegionId, worldId, REFUND_SALE_ENDED);
        logger.warning("An instalment of " + CurrencyFormatter.format(amount) + " by " + buyerId + " towards "
                + worldGuardRegionId + " arrived after its sale had ended; it is recorded as a refund");
    }

    // --- Pay Bid ---


    @Override
    public @NotNull PayBidResult payBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID bidderId,
                                         double amount) {
        amount = Money.cents(amount);
        if (amount <= 0) {
            return new PayBidResult.InvalidAmount();
        }
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // The freehold first (the per-region serialization point), as payOffer does, so that a
            // cancel or a deletion reading what has been paid sees this instalment or waits for it.
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            FreeholdContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment == null || freehold == null || !payment.bidderId().equals(bidderId)) {
                return new PayBidResult.NoPaymentRecord();
            }
            if (payment.pendingPayment() > 0) {
                // The money of an earlier instalment has not been confirmed as moved.
                return new PayBidResult.PaymentInProgress();
            }
            if (payment.paymentDeadline().isBefore(LocalDateTime.now())) {
                return new PayBidResult.PaymentExpired();
            }
            // In whole cents, so that a fraction of a cent can neither exceed nor complete it.
            double amountOwed = Money.minus(payment.totalDue(), payment.currentPayment());
            if (amount > amountOwed) {
                return new PayBidResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = Money.plus(payment.currentPayment(), amount);
            // Counted as pending until the paper layer confirms that its money moved. Optimistic
            // lock: the row must be unchanged and have nothing else pending.
            int updated = paymentMapper.recordPending(payment.bidId(), payment.currentPayment(), amount);
            if (updated == 0) {
                return new PayBidResult.NoPaymentRecord();
            }
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            // Record the payment only. Ownership transfer is DEFERRED to
            // finalizeBidPurchase, called by the paper layer ONLY after the
            // economy payment succeeds — so a failed/insufficient payment can
            // never hand over the region (previously it did, for free).
            if (Money.atLeast(newTotal, payment.totalDue())) {
                FeeQuote fees = payment.oldRules()
                        ? FeeQuote.none(payment.bidPrice())
                        : SaleFeeResolver.lockedQuote(wrapper, SALE_BID, payment.bidId(),
                                payment.bidPrice(), payment.totalDue());
                wrapper.session().commit();
                return new PayBidResult.FullyPaid(authority, titleHolderId, fees, payment.oldRules(), payment.bidId());
            }
            wrapper.session().commit();
            return new PayBidResult.Success(newTotal, Money.minus(payment.totalDue(), newTotal), authority, titleHolderId,
                    payment.oldRules());
        }
    }

    @Override
    public boolean finalizeBidPurchase(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID bidderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Lock the freehold row first (per-region serialization point), as
            // finalizeOfferPurchase does, so the fees and title read below are current.
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            FreeholdContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            // Only finalize a real, fully-paid record for this bidder. Idempotent:
            // a second call (or one after rollback) finds nothing/under-paid and no-ops.
            if (freehold == null || payment == null || !payment.bidderId().equals(bidderId)
                    || payment.pendingPayment() > 0
                    || !Money.atLeast(payment.currentPayment(), payment.totalDue())) {
                return false;
            }
            freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.bidPrice(), bidderId);
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null, false, null, null);
            paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
            // Ended, so that settlement does not offer the sold plot to its winner again.
            wrapper.freeholdContractAuctionMapper().markEnded(payment.freeholdContractAuctionId());
            wrapper.saleFeeMapper().markCompleted(SALE_BID, payment.bidId());
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            deleteAgentFees(wrapper, realtyRegionId);
            onTitleChanged(wrapper, realtyRegionId, freehold.titleHolderId(), bidderId);
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.AUCTION_BUY.name(),
                    bidderId, namedPartyId(wrapper, freehold.authority()), payment.bidPrice());
            wrapper.session().commit();
            return true;
        }
    }

    @Override
    public void rollbackPayBid(@NotNull String worldGuardRegionId,
                                @NotNull UUID worldId,
                                @NotNull UUID bidderId,
                                double amount) {
        amount = Money.cents(amount);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // The freehold first, as payBid, cancelAuction and the expiry take it.
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            paymentMapper.clearPending(worldGuardRegionId, worldId, bidderId, amount);
            wrapper.session().commit(true);
        }
    }

    @Override
    public boolean confirmPayBid(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                 @NotNull UUID bidderId, double amount, boolean oldRules) {
        amount = Money.cents(amount);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.freeholdContractMapper().selectByRegionForUpdate(worldGuardRegionId, worldId);
            FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
            boolean confirmed = paymentMapper.confirmPending(worldGuardRegionId, worldId, bidderId, amount) == 1;
            if (!confirmed) {
                instalmentWithoutSale(wrapper, bidderId, worldGuardRegionId, worldId, amount, oldRules);
            }
            wrapper.session().commit(true);
            return confirmed;
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
                            null, auction.auctioneerId(), 0, 0, null));
                } else {
                    if (!holdingAvailable.getAsBoolean()) {
                        // The winner's instalments would have nowhere to wait. The auction is left
                        // as it is, to settle once a holding account is configured.
                        wrapper.session().rollback();
                        if (warnedNoHolding.add(auction.freeholdContractAuctionId())) {
                            logger.warning("The auction of " + region.worldGuardRegionId()
                                    + " has ended, but no holding account is configured, so its winner cannot"
                                    + " pay yet. It will settle once one is.");
                        }
                        continue;
                    }
                    FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                            .selectByRegion(region.worldGuardRegionId(), region.worldId());
                    if (freehold == null) {
                        auctionMapper.markEnded(auction.freeholdContractAuctionId());
                        wrapper.session().commit();
                        continue;
                    }
                    FeeQuote fees = feeResolver.resolve(wrapper, region, freehold, highestBid.bidderId(),
                            highestBid.bidAmount(), auction.auctioneerId());
                    LocalDateTime deadline = LocalDateTime.now().plusSeconds(auction.paymentDurationSeconds());
                    auctionMapper.setPaymentDeadline(auction.freeholdContractAuctionId(), deadline);
                    FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
                    paymentMapper.insertPayment(region.worldGuardRegionId(), region.worldId(),
                            highestBid.bidderId(), highestBid.bidAmount(), deadline, fees.buyerTotal());
                    FreeholdContractBidPaymentEntity payment =
                            paymentMapper.selectByRegion(region.worldGuardRegionId(), region.worldId());
                    if (payment == null) {
                        throw new IllegalStateException("no payment row for the auction of "
                                + region.worldGuardRegionId() + " just after inserting it");
                    }
                    feeResolver.lockFees(wrapper, SALE_BID, payment.bidId(), region, highestBid.bidderId(),
                            freeholdHolder(freehold), fees);
                    wrapper.session().commit();
                    warnedNoHolding.remove(auction.freeholdContractAuctionId());
                    results.add(new ExpiredBiddingAuction(region.worldGuardRegionId(), region.worldId(),
                            highestBid.bidderId(), auction.auctioneerId(), highestBid.bidAmount(),
                            fees.buyerTotal(), deadline));
                }
            } catch (RuntimeException ex) {
                // Such as two sales creating the same new party at once. Rolled back, so the
                // auction is tried again on the next run; the others go ahead now.
                logger.log(Level.WARNING, "Could not settle the auction #" + candidate.freeholdContractAuctionId()
                        + "; it will be tried again", ex);
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
        List<ExpiredBidPayment> results = new ArrayList<>();
        for (FreeholdContractBidPaymentEntity candidate : expired) {
            if (!candidate.oldRules() && !holdingAvailable.getAsBoolean()) {
                // Its refund comes from the holding account, and the next bidder's instalments
                // would have nowhere to wait. The expired payment is left as it is: nobody can pay
                // it, and each run looks at it again, so it hands over and refunds once a holding
                // account is configured. An old-rules payment touches no holding account, so it
                // ends now.
                if (warnedNoHolding.add(candidate.freeholdContractAuctionId())) {
                    logger.warning("A payment for an auction has expired, but no holding account is configured,"
                            + " so the next bidder cannot be offered it yet (bid #" + candidate.bidId()
                            + "). It will move on once one is.");
                }
                continue;
            }
            try (SqlSessionWrapper wrapper = database.openSession()) {
                ExpiredBidPayment result = expireBidPayment(wrapper, candidate);
                wrapper.session().commit();
                if (result != null) {
                    warnedNoHolding.remove(candidate.freeholdContractAuctionId());
                    results.add(result);
                }
            } catch (RuntimeException ex) {
                // Rolled back, refund and all, so the payment is tried again on the next run.
                logger.log(Level.WARNING, "Could not end the expired payment for bid #" + candidate.bidId()
                        + "; it will be tried again", ex);
            }
        }
        return results;
    }

    /**
     * Ends one expired payment, if it still is one: under the freehold lock it is read afresh, its
     * deadline checked by the database clock, and a payment paid in full, whose finalize is pending,
     * is left alone. What was paid under the new rules is recorded as owed back in the same
     * transaction, so it cannot be lost.
     *
     * @return the payment ended, or null when there was none to end
     */
    private @Nullable ExpiredBidPayment expireBidPayment(@NotNull SqlSessionWrapper wrapper,
                                                         @NotNull FreeholdContractBidPaymentEntity candidate) {
        RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(candidate.realtyRegionId());
        if (region == null) {
            // Deleted since the candidate snapshot; deleteRegion recorded the refund.
            return null;
        }
        wrapper.freeholdContractMapper().selectByRegionForUpdate(region.worldGuardRegionId(), region.worldId());
        FreeholdContractBidPaymentMapper paymentMapper = wrapper.freeholdContractBidPaymentMapper();
        FreeholdContractBidPaymentEntity payment = paymentMapper.selectExpiredById(candidate.bidId());
        // Not while an instalment is pending: its money may yet arrive, or be stuck for an admin.
        if (payment == null || payment.pendingPayment() > 0
                || Money.atLeast(payment.currentPayment(), payment.totalDue())) {
            return null;
        }
        paymentMapper.deleteByBidId(payment.bidId());
        wrapper.saleFeeMapper().deleteUnpaidBySale(SALE_BID, payment.bidId());
        Integer refundId = null;
        if (!payment.oldRules() && payment.currentPayment() > 0) {
            refundId = wrapper.pendingRefundMapper().insert(payment.bidderId(), payment.currentPayment(),
                    region.worldGuardRegionId(), region.worldId(), REFUND_BID_EXPIRED);
        }
        FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                .selectById(payment.freeholdContractAuctionId());
        if (auction != null) {
            LocalDateTime nextDeadline = LocalDateTime.now().plusSeconds(auction.paymentDurationSeconds());
            int inserted = paymentMapper.insertNextPayment(payment.freeholdContractAuctionId(), payment.bidPrice(),
                    nextDeadline);
            if (inserted > 0) {
                lockRunnerUp(wrapper, region, auction);
            } else {
                // No bidder is left to offer it to, so the auction is over; left open, settlement
                // would offer it to the winner who just missed their payment.
                wrapper.freeholdContractAuctionMapper().markEnded(auction.freeholdContractAuctionId());
            }
        }
        return new ExpiredBidPayment(payment.bidderId(), payment.currentPayment(), region.worldGuardRegionId(),
                region.worldId(), payment.oldRules(), refundId);
    }

    /**
     * Locks the fees of the runner-up just offered the auction, worked out afresh for them: their
     * plots, and so their levy, may differ from the first bidder's.
     */
    private void lockRunnerUp(@NotNull SqlSessionWrapper wrapper,
                              @NotNull RealtyRegionEntity region,
                              @NotNull FreeholdContractAuctionEntity auction) {
        FreeholdContractBidPaymentEntity next = wrapper.freeholdContractBidPaymentMapper()
                .selectByRegion(region.worldGuardRegionId(), region.worldId());
        FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                .selectByRegion(region.worldGuardRegionId(), region.worldId());
        if (next == null || freehold == null) {
            return;
        }
        FeeQuote fees = feeResolver.resolve(wrapper, region, freehold, next.bidderId(), next.bidPrice(),
                auction.auctioneerId());
        wrapper.freeholdContractBidPaymentMapper().updateTotalDue(next.bidId(), fees.buyerTotal());
        feeResolver.lockFees(wrapper, SALE_BID, next.bidId(), region, next.bidderId(), freeholdHolder(freehold), fees);
    }

    // --- Expired Offer Payments ---


    @Override
    public @NotNull List<ExpiredOfferPayment> clearExpiredOfferPayments() {
        List<FreeholdContractOfferPaymentEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.freeholdContractOfferPaymentMapper().selectAllExpired();
        }
        List<ExpiredOfferPayment> results = new ArrayList<>();
        for (FreeholdContractOfferPaymentEntity candidate : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                ExpiredOfferPayment result = expireOfferPayment(wrapper, candidate);
                wrapper.session().commit();
                if (result != null) {
                    results.add(result);
                }
            } catch (RuntimeException ex) {
                // Rolled back, refund and all, so the payment is tried again on the next run.
                logger.log(Level.WARNING, "Could not end the expired payment for offer #" + candidate.offerId()
                        + "; it will be tried again", ex);
            }
        }
        return results;
    }

    /** As {@link #expireBidPayment}, for an accepted offer, which has no next bidder. */
    private @Nullable ExpiredOfferPayment expireOfferPayment(@NotNull SqlSessionWrapper wrapper,
                                                             @NotNull FreeholdContractOfferPaymentEntity candidate) {
        RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(candidate.realtyRegionId());
        if (region == null) {
            return null;
        }
        wrapper.freeholdContractMapper().selectByRegionForUpdate(region.worldGuardRegionId(), region.worldId());
        FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
        FreeholdContractOfferPaymentEntity payment = paymentMapper.selectExpiredById(candidate.offerId());
        if (payment == null || payment.pendingPayment() > 0
                || Money.atLeast(payment.currentPayment(), payment.totalDue())) {
            return null;
        }
        paymentMapper.deleteByOfferId(payment.offerId());
        wrapper.saleFeeMapper().deleteUnpaidBySale(SALE_OFFER, payment.offerId());
        Integer refundId = null;
        if (!payment.oldRules() && payment.currentPayment() > 0) {
            refundId = wrapper.pendingRefundMapper().insert(payment.offererId(), payment.currentPayment(),
                    region.worldGuardRegionId(), region.worldId(), REFUND_OFFER_EXPIRED);
        }
        return new ExpiredOfferPayment(payment.offererId(), payment.currentPayment(), region.worldGuardRegionId(),
                region.worldId(), payment.oldRules(), refundId);
    }

    // --- Instalments stuck pending ---

    @Override
    public @NotNull List<StuckInstalment> stuckInstalments(int minutes) {
        List<StuckInstalment> stuck = new ArrayList<>();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            for (FreeholdContractBidPaymentEntity payment
                    : wrapper.freeholdContractBidPaymentMapper().selectStuckPending(minutes)) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                if (region != null) {
                    stuck.add(new StuckInstalment(PendingFinalize.Kind.BID, payment.bidId(),
                            region.worldGuardRegionId(), region.worldId(), payment.bidderId(),
                            payment.pendingPayment(), Objects.requireNonNull(payment.pendingSince())));
                }
            }
            for (FreeholdContractOfferPaymentEntity payment
                    : wrapper.freeholdContractOfferPaymentMapper().selectStuckPending(minutes)) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                if (region != null) {
                    stuck.add(new StuckInstalment(PendingFinalize.Kind.OFFER, payment.offerId(),
                            region.worldGuardRegionId(), region.worldId(), payment.offererId(),
                            payment.pendingPayment(), Objects.requireNonNull(payment.pendingSince())));
                }
            }
        }
        return stuck;
    }

    // --- Sales paid in full but not finalized ---

    @Override
    public @NotNull List<PendingFinalize> listFullyPaidUnfinalized() {
        List<PendingFinalize> pending = new ArrayList<>();
        try (SqlSessionWrapper wrapper = database.openSession()) {
            for (FreeholdContractBidPaymentEntity payment
                    : wrapper.freeholdContractBidPaymentMapper().selectFullyPaidUnfinalized()) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                if (region == null) {
                    continue;
                }
                FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                        .selectByRegion(region.worldGuardRegionId(), region.worldId());
                pending.add(new PendingFinalize(PendingFinalize.Kind.BID, region.worldGuardRegionId(),
                        region.worldId(), payment.bidderId(), freehold != null ? freehold.titleHolderId() : null,
                        payment.bidPrice(), payment.oldRules() ? FeeQuote.none(payment.bidPrice())
                                : SaleFeeResolver.lockedQuote(wrapper, SALE_BID, payment.bidId(),
                                        payment.bidPrice(), payment.totalDue()),
                        payment.oldRules()));
            }
            for (FreeholdContractOfferPaymentEntity payment
                    : wrapper.freeholdContractOfferPaymentMapper().selectFullyPaidUnfinalized()) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                if (region == null) {
                    continue;
                }
                FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                        .selectByRegion(region.worldGuardRegionId(), region.worldId());
                pending.add(new PendingFinalize(PendingFinalize.Kind.OFFER, region.worldGuardRegionId(),
                        region.worldId(), payment.offererId(), freehold != null ? freehold.titleHolderId() : null,
                        payment.offerPrice(), payment.oldRules() ? FeeQuote.none(payment.offerPrice())
                                : SaleFeeResolver.lockedQuote(wrapper, SALE_OFFER, payment.offerId(),
                                        payment.offerPrice(), payment.totalDue()),
                        payment.oldRules()));
            }
        }
        return pending;
    }

    // --- Sale payouts and refunds ---

    /** How long a failed payout or refund waits before it is tried again, by the database clock. */
    public static final int PAYOUT_RETRY_MINUTES = 60;
    /** How many times a payout or refund is tried before it is left for an admin. */
    public static final int MAX_PAYOUT_ATTEMPTS = 24;

    @Override
    public @NotNull List<DuePayout> duePayouts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return toPayouts(wrapper, wrapper.saleFeeMapper().selectDue(PAYOUT_RETRY_MINUTES, MAX_PAYOUT_ATTEMPTS));
        }
    }

    @Override
    public @NotNull List<DuePayout> stuckPayouts() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return toPayouts(wrapper, wrapper.saleFeeMapper().selectPaying());
        }
    }

    private @NotNull List<DuePayout> toPayouts(@NotNull SqlSessionWrapper wrapper, @NotNull List<SaleFeeEntity> rows) {
        List<DuePayout> payouts = new ArrayList<>(rows.size());
        Map<String, Double> salePrices = new HashMap<>();
        for (SaleFeeEntity row : rows) {
            Party recipient = wrapper.partyMapper().selectById(row.recipientPartyId());
            if (recipient == null) {
                logger.severe("Sale fee line " + row.saleFeeId() + " names party " + row.recipientPartyId()
                        + ", which does not exist; it cannot be paid out.");
                continue;
            }
            double salePrice = row.saleRef() == null ? row.amount()
                    : salePrices.computeIfAbsent(row.saleKind() + "#" + row.saleRef(),
                            key -> salePrice(wrapper, row.saleKind(), row.saleRef()));
            payouts.add(new DuePayout(row.saleFeeId(), row.lineKind(), row.source(), recipient, row.amount(),
                    row.regionName(), row.worldId(), row.buyerId(), row.attempts(), salePrice));
        }
        return payouts;
    }

    /**
     * The price a sale was locked at, from its lines: the seller's proceeds and the fees the seller
     * pays come out of the price, so together they are the price.
     */
    private static double salePrice(@NotNull SqlSessionWrapper wrapper, @NotNull String saleKind, int saleRef) {
        BigDecimal price = BigDecimal.ZERO;
        for (SaleFeeEntity line : wrapper.saleFeeMapper().selectBySale(saleKind, saleRef)) {
            if (SaleFeeResolver.LINE_PROCEEDS.equals(line.lineKind()) || line.side() == FeeSide.SELLER) {
                price = price.add(BigDecimal.valueOf(line.amount()));
            }
        }
        return price.doubleValue();
    }

    @Override
    public @NotNull OptionalInt claimPayout(int id) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            if (wrapper.saleFeeMapper().claim(id, PAYOUT_RETRY_MINUTES, MAX_PAYOUT_ATTEMPTS) != 1) {
                wrapper.session().commit();
                return OptionalInt.empty();
            }
            int attempts = wrapper.saleFeeMapper().selectAttempts(id);
            wrapper.session().commit();
            return OptionalInt.of(attempts);
        }
    }

    @Override
    public void markPayoutPaid(int id) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.saleFeeMapper().markPaid(id);
            wrapper.session().commit();
        }
    }

    @Override
    public void markPayoutFailed(int id, @NotNull String error) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.saleFeeMapper().markFailed(id, error);
            wrapper.session().commit();
        }
    }

    @Override
    public @NotNull List<DueRefund> dueRefunds() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return toRefunds(wrapper.pendingRefundMapper().selectDue(PAYOUT_RETRY_MINUTES, MAX_PAYOUT_ATTEMPTS));
        }
    }

    @Override
    public boolean holdsSaleMoney() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.saleFeeMapper().countHeldForSales() > 0;
        }
    }

    @Override
    public @NotNull List<DueRefund> stuckRefunds() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return toRefunds(wrapper.pendingRefundMapper().selectPaying());
        }
    }

    private static @NotNull List<DueRefund> toRefunds(@NotNull List<PendingRefundEntity> rows) {
        return rows.stream()
                .map(row -> new DueRefund(row.pendingRefundId(), row.buyerId(), row.amount(), row.regionName(),
                        row.worldId(), row.reason(), row.attempts()))
                .toList();
    }

    @Override
    public @NotNull OptionalInt claimRefund(int id) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            if (wrapper.pendingRefundMapper().claim(id, PAYOUT_RETRY_MINUTES, MAX_PAYOUT_ATTEMPTS) != 1) {
                wrapper.session().commit();
                return OptionalInt.empty();
            }
            int attempts = wrapper.pendingRefundMapper().selectAttempts(id);
            wrapper.session().commit();
            return OptionalInt.of(attempts);
        }
    }

    @Override
    public void markRefundPaid(int id) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.pendingRefundMapper().delete(id);
            wrapper.session().commit();
        }
    }

    @Override
    public void markRefundFailed(int id, @NotNull String error) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            wrapper.pendingRefundMapper().markFailed(id, error);
            wrapper.session().commit();
        }
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

    // --- Player Fees ---

    /** The {@code RegionFee.role} of a fee set by the authority, through a manager or in person. */
    private static final String ROLE_AUTHORITY = "authority";
    /** The {@code RegionFee.role} of a fee set by the titleholder. */
    private static final String ROLE_TITLEHOLDER = "titleholder";
    /** The {@code RegionFee.role} of a fee set by a sanctioned auctioneer. */
    private static final String ROLE_AGENT = "agent";

    @Override
    public @NotNull SetFeeResult setFee(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull ActorContext ctx,
                                        double percent,
                                        @NotNull FeeSide side,
                                        boolean asAuthority) {
        if (!(percent > 0) || Double.isInfinite(percent)) {
            throw new IllegalArgumentException("a fee must be a positive percentage, not " + percent);
        }
        UUID playerId = ctx.requirePlayer();
        Party personal = Party.personal(playerId);
        // Made before the transaction, as partyIdCreatingIfAbsent requires. A player who turns out
        // to hold no role is left with a party row that nothing names, which does no harm.
        Integer personalPartyId = asAuthority ? null : partyIdCreatingIfAbsent(personal);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Locked, so that the title cannot change hands between the role check and the write.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetFeeResult.NoFreehold();
            }
            Party authority = freehold.authority();
            UUID titleHolderId = freehold.titleHolderId();
            Party recipient;
            String role;
            int recipientPartyId;
            if (asAuthority) {
                if (!ctx.mayManage(authority)) {
                    return new SetFeeResult.NotAuthorityManager();
                }
                recipient = authority;
                role = ROLE_AUTHORITY;
                recipientPartyId = namedPartyId(wrapper, authority);
            } else {
                recipient = personal;
                if (playerId.equals(titleHolderId)) {
                    role = ROLE_TITLEHOLDER;
                } else if (wrapper.freeholdContractSanctionedAuctioneerMapper()
                        .existsByRegionAndAuctioneer(worldGuardRegionId, worldId, playerId)) {
                    role = ROLE_AGENT;
                } else if (personal.equals(authority)) {
                    role = ROLE_AUTHORITY;
                } else {
                    return new SetFeeResult.NotAllowed();
                }
                recipientPartyId = personalPartyId;
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            RegionFeeEntity existing = findFee(wrapper, realtyRegionId, recipientPartyId);
            if (existing != null && existing.side() == side && Double.compare(existing.percent(), percent) == 0
                    && existing.role().equals(role)) {
                return new SetFeeResult.NoChange();
            }
            // Buyers bid or offer against the fees they were shown, so none may grow under them.
            boolean raisesBuyerFee = side == FeeSide.BUYER
                    && (existing == null || existing.side() != FeeSide.BUYER || percent > existing.percent());
            if (raisesBuyerFee && saleOpen(wrapper, worldGuardRegionId, worldId)) {
                return new SetFeeResult.SaleOpen();
            }
            boolean setterIsSeller = titleHolderId != null
                    ? playerId.equals(titleHolderId)
                    : ctx.manages().contains(authority);
            boolean approved = side == FeeSide.BUYER
                    || recipient.equals(freeholdHolder(freehold))
                    || setterIsSeller;
            wrapper.regionFeeMapper().upsert(realtyRegionId, recipientPartyId, side, percent, role, playerId, approved);
            wrapper.session().commit();
            return new SetFeeResult.Success(recipient, !approved, existing == null ? null : existing.percent());
        }
    }

    @Override
    public @NotNull RemoveFeeResult removeFee(@NotNull String worldGuardRegionId,
                                              @NotNull UUID worldId,
                                              @NotNull ActorContext ctx,
                                              boolean asAuthority) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new RemoveFeeResult.NoFreehold();
            }
            Party recipient;
            if (asAuthority) {
                if (!ctx.mayManage(freehold.authority())) {
                    return new RemoveFeeResult.NotAuthorityManager();
                }
                recipient = freehold.authority();
            } else {
                recipient = Party.personal(ctx.requirePlayer());
            }
            Integer recipientPartyId = wrapper.partyMapper().findId(recipient);
            if (recipientPartyId == null) {
                return new RemoveFeeResult.NoFee();
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            if (wrapper.regionFeeMapper().delete(realtyRegionId, recipientPartyId) == 0) {
                return new RemoveFeeResult.NoFee();
            }
            wrapper.session().commit();
            return new RemoveFeeResult.Success();
        }
    }

    @Override
    public @NotNull ResolveFeeResult approveFee(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @NotNull ActorContext ctx,
                                                @NotNull Party recipient) {
        return resolvePendingFee(worldGuardRegionId, worldId, ctx, recipient, true);
    }

    @Override
    public @NotNull ResolveFeeResult rejectFee(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull ActorContext ctx,
                                               @NotNull Party recipient) {
        return resolvePendingFee(worldGuardRegionId, worldId, ctx, recipient, false);
    }

    /**
     * The seller approves the recipient's pending seller-paid fee, or rejects it, which deletes it.
     * The seller is whoever acts for the freehold: its titleholder, or a manager of its authority
     * while it has none.
     */
    private @NotNull ResolveFeeResult resolvePendingFee(@NotNull String worldGuardRegionId,
                                                        @NotNull UUID worldId,
                                                        @NotNull ActorContext ctx,
                                                        @NotNull Party recipient,
                                                        boolean approve) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new ResolveFeeResult.NoFreehold();
            }
            if (!ctx.mayManage(freeholdHolder(freehold))) {
                return new ResolveFeeResult.NotSeller();
            }
            Integer recipientPartyId = wrapper.partyMapper().findId(recipient);
            if (recipientPartyId == null) {
                return new ResolveFeeResult.NoPendingFee();
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            RegionFeeEntity fee = findFee(wrapper, realtyRegionId, recipientPartyId);
            if (fee == null || fee.side() != FeeSide.SELLER || fee.approved()) {
                return new ResolveFeeResult.NoPendingFee();
            }
            if (approve) {
                wrapper.regionFeeMapper().setApproved(realtyRegionId, recipientPartyId, true);
            } else {
                wrapper.regionFeeMapper().delete(realtyRegionId, recipientPartyId);
            }
            wrapper.session().commit();
            return new ResolveFeeResult.Success(fee.side(), fee.percent());
        }
    }

    @Override
    public @NotNull List<FeeListing> listFees(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return List.of();
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            List<FeeListing> listings = new ArrayList<>();
            LevyDefinition levy = SaleFeeResolver.resolveLevy(wrapper, this.levies.get(), realtyRegionId, freehold.authority());
            if (levy != null) {
                Set<String> tags = new HashSet<>();
                for (String tag : wrapper.regionTagMapper().selectTagIdsByRegionId(worldGuardRegionId)) {
                    tags.add(tag.toLowerCase(Locale.ROOT));
                }
                String rate = levyRate(levy, tags, freehold.authority());
                if (rate != null) {
                    listings.add(new FeeListing(levy.name(), levy.recipient(), levy.paidBy(), rate, true, null));
                }
            }
            for (RegionFeeEntity fee : wrapper.regionFeeMapper().selectByRegion(realtyRegionId)) {
                Party recipient = wrapper.partyMapper().selectById(fee.recipientPartyId());
                listings.add(new FeeListing(FeeLine.playerFeeSource(fee.role()), recipient, fee.side(),
                        formatPercent(fee.percent()) + "%", fee.approved(), fee.role()));
            }
            return listings;
        }
    }

    /**
     * The levy's rate on a region for display: the range of the first matching rule's tiers, such
     * as {@code 2–8% by plots held}, or one percentage when they are all the same. {@code null}
     * when the levy charges nothing there: it is off, the authority's kind may not use it, or no
     * rule matches.
     */
    private static @Nullable String levyRate(@NotNull LevyDefinition levy,
                                             @NotNull Set<String> lowerCasedTags,
                                             @NotNull Party authority) {
        if (!levy.enabled() || !SaleFeeResolver.levyFitsAuthority(levy, authority)) {
            return null;
        }
        for (LevyRule rule : levy.rules()) {
            if (!rule.matches(lowerCasedTags)) {
                continue;
            }
            if (rule.tiers().isEmpty()) {
                return null;
            }
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (LevyTier tier : rule.tiers()) {
                min = Math.min(min, tier.percent());
                max = Math.max(max, tier.percent());
            }
            if (Double.compare(min, max) == 0) {
                return formatPercent(min) + "%";
            }
            return formatPercent(min) + "–" + formatPercent(max) + "% by plots held";
        }
        return null;
    }

    /** A percentage with up to two decimals and no trailing zeros: {@code 3}, {@code 2.5}. */
    private static @NotNull String formatPercent(double percent) {
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static @Nullable RegionFeeEntity findFee(@NotNull SqlSessionWrapper wrapper,
                                                     int realtyRegionId,
                                                     int recipientPartyId) {
        for (RegionFeeEntity fee : wrapper.regionFeeMapper().selectByRegion(realtyRegionId)) {
            if (fee.recipientPartyId() == recipientPartyId) {
                return fee;
            }
        }
        return null;
    }

    /**
     * Whether the region's sale is open, so that what its buyers were shown must not grow: it has
     * an auction that has not ended, or an offer none of which has been accepted. An auction counts
     * through its whole payment phase, not only while it takes bids: the winner's fees are worked
     * out when the scheduler settles it, and a runner-up's afresh when the sale is handed to them.
     * Call it under the freehold lock, which auctions and offers are opened under.
     */
    private static boolean saleOpen(@NotNull SqlSessionWrapper wrapper,
                                    @NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId) {
        return wrapper.freeholdContractAuctionMapper().existsByRegion(worldGuardRegionId, worldId)
                || (wrapper.freeholdContractOfferMapper().existsByRegion(worldGuardRegionId, worldId)
                && !wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId));
    }

    /**
     * Clears the fees a change of titleholder leaves without their basis, in the caller's
     * transaction: the previous titleholder's fees go, and the seller-paid fees the new seller
     * did not set wait for the new seller's approval. When nobody holds the title afterwards, the
     * authority is the seller, so the fees it receives stay approved.
     */
    private static void onTitleChanged(@NotNull SqlSessionWrapper wrapper,
                                       int realtyRegionId,
                                       @Nullable UUID previous,
                                       @Nullable UUID next) {
        if (Objects.equals(previous, next)) {
            return;
        }
        RegionFeeMapper fees = wrapper.regionFeeMapper();
        if (previous != null) {
            fees.deleteBySetByAndRole(realtyRegionId, previous, ROLE_TITLEHOLDER);
        }
        if (next != null) {
            fees.unapproveSellerFeesNotSetBy(realtyRegionId, next);
            return;
        }
        // A fee set under the authority role is always paid to the authority.
        fees.unapproveSellerFeesNotOfRole(realtyRegionId, ROLE_AUTHORITY);
    }

    /** Deletes every agent's fee on the region, when all its agents are cleared. */
    private static void deleteAgentFees(@NotNull SqlSessionWrapper wrapper, int realtyRegionId) {
        wrapper.regionFeeMapper().deleteByRole(realtyRegionId, ROLE_AGENT);
    }

    private static @Nullable Integer realtyRegionIdOf(@NotNull SqlSessionWrapper wrapper,
                                                      @NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId) {
        RealtyRegionEntity region = wrapper.realtyRegionMapper().selectByWorldGuardRegion(worldGuardRegionId, worldId);
        return region == null ? null : region.realtyRegionId();
    }

    /** The region, for a region a contract was just read for, so it is registered. */
    private static @NotNull RealtyRegionEntity requireRegion(@NotNull SqlSessionWrapper wrapper,
                                                             @NotNull String worldGuardRegionId,
                                                             @NotNull UUID worldId) {
        RealtyRegionEntity region = wrapper.realtyRegionMapper().selectByWorldGuardRegion(worldGuardRegionId, worldId);
        if (region == null) {
            throw new IllegalStateException("no RealtyRegion row for " + worldGuardRegionId + ", although a contract names it");
        }
        return region;
    }

    /** The region's id, for a region a contract was just read for, so it is registered. */
    private static int requireRealtyRegionId(@NotNull SqlSessionWrapper wrapper,
                                             @NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId) {
        Integer realtyRegionId = realtyRegionIdOf(wrapper, worldGuardRegionId, worldId);
        if (realtyRegionId == null) {
            throw new IllegalStateException("no RealtyRegion row for " + worldGuardRegionId + ", although a contract names it");
        }
        return realtyRegionId;
    }

    // --- Levies ---

    @Override
    public @NotNull SetLevyResult setRegionLevy(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @NotNull ActorContext ctx,
                                                @NotNull String levyName) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Locked, as auctions and offers open under this lock, so none opens before the write.
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetLevyResult.NoFreehold();
            }
            if (!ctx.mayManage(freehold.authority())) {
                return new SetLevyResult.NotAuthorityManager();
            }
            LevyDefinition levy = this.levies.get().find(levyName).orElse(null);
            if (levy == null) {
                return new SetLevyResult.UnknownLevy();
            }
            if (!SaleFeeResolver.levyFitsAuthority(levy, freehold.authority())) {
                return new SetLevyResult.WrongAuthorityKind();
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            String previous = wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId);
            if (levy.name().equalsIgnoreCase(previous)) {
                return new SetLevyResult.NoChange();
            }
            if (saleOpen(wrapper, worldGuardRegionId, worldId)) {
                return new SetLevyResult.SaleOpen(1);
            }
            wrapper.levyChoiceMapper().upsertRegionLevy(realtyRegionId, levy.name(), ctx.player());
            wrapper.session().commit();
            return new SetLevyResult.Success(previous);
        }
    }

    @Override
    public @NotNull SetLevyResult clearRegionLevy(@NotNull String worldGuardRegionId,
                                                  @NotNull UUID worldId,
                                                  @NotNull ActorContext ctx) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegionForUpdate(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetLevyResult.NoFreehold();
            }
            if (!ctx.mayManage(freehold.authority())) {
                return new SetLevyResult.NotAuthorityManager();
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            String previous = wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId);
            if (previous == null) {
                return new SetLevyResult.NoChange();
            }
            if (saleOpen(wrapper, worldGuardRegionId, worldId)) {
                return new SetLevyResult.SaleOpen(1);
            }
            wrapper.levyChoiceMapper().deleteRegionLevy(realtyRegionId);
            wrapper.session().commit();
            return new SetLevyResult.Success(previous);
        }
    }

    @Override
    public @NotNull SetLevyResult setAuthorityLevy(@NotNull Party authority,
                                                   @NotNull ActorContext ctx,
                                                   @NotNull String levyName) {
        if (!ctx.mayManage(authority)) {
            return new SetLevyResult.NotAuthorityManager();
        }
        LevyDefinition levy = this.levies.get().find(levyName).orElse(null);
        if (levy == null) {
            return new SetLevyResult.UnknownLevy();
        }
        if (!SaleFeeResolver.levyFitsAuthority(levy, authority)) {
            return new SetLevyResult.WrongAuthorityKind();
        }
        int authorityPartyId = partyIdCreatingIfAbsent(authority);
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Locked first, so that the reads below see every auction and offer opened before.
            wrapper.freeholdContractMapper().lockByAuthority(authorityPartyId);
            String previous = wrapper.levyChoiceMapper().selectAuthorityLevy(authorityPartyId);
            if (levy.name().equalsIgnoreCase(previous)) {
                return new SetLevyResult.NoChange();
            }
            OpenSalePins pins = openSalePins(wrapper, this.levies.get(), authority, authorityPartyId, levy);
            if (pins.unpinnable() > 0) {
                return new SetLevyResult.SaleOpen(pins.unpinnable());
            }
            pins.apply(wrapper, ctx.player());
            wrapper.levyChoiceMapper().upsertAuthorityLevy(authorityPartyId, levy.name(), ctx.player());
            wrapper.session().commit();
            return new SetLevyResult.Success(previous, pins.levyByRegion().size());
        }
    }

    @Override
    public @NotNull SetLevyResult clearAuthorityLevy(@NotNull Party authority, @NotNull ActorContext ctx) {
        if (!ctx.mayManage(authority)) {
            return new SetLevyResult.NotAuthorityManager();
        }
        Integer authorityPartyId;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            authorityPartyId = wrapper.partyMapper().findId(authority);
        }
        if (authorityPartyId == null) {
            return new SetLevyResult.NoChange();
        }
        try (SqlSessionWrapper wrapper = database.openSession()) {
            // Locked first, so that the reads below see every auction and offer opened before.
            wrapper.freeholdContractMapper().lockByAuthority(authorityPartyId);
            String previous = wrapper.levyChoiceMapper().selectAuthorityLevy(authorityPartyId);
            if (previous == null) {
                return new SetLevyResult.NoChange();
            }
            LevyCatalog catalog = this.levies.get();
            OpenSalePins pins = openSalePins(wrapper, catalog, authority, authorityPartyId,
                    catalog.defaultLevy().orElse(null));
            if (pins.unpinnable() > 0) {
                return new SetLevyResult.SaleOpen(pins.unpinnable());
            }
            pins.apply(wrapper, ctx.player());
            wrapper.levyChoiceMapper().deleteAuthorityLevy(authorityPartyId);
            wrapper.session().commit();
            return new SetLevyResult.Success(previous, pins.levyByRegion().size());
        }
    }

    /**
     * What an authority's levy change must do first for its freeholds with bids or offers open,
     * whose levy must not change under their buyers.
     *
     * @param levyByRegion the levy each such freehold uses now, to be made its own choice
     * @param unpinnable   how many such freeholds use no levy now but would gain one; with none
     *                     to name, they cannot be pinned, so the change must be refused
     */
    private record OpenSalePins(@NotNull Map<Integer, String> levyByRegion, int unpinnable) {

        /** Makes each freehold's current levy its own choice, in the caller's transaction. */
        void apply(@NotNull SqlSessionWrapper wrapper, @Nullable UUID setBy) {
            levyByRegion.forEach((realtyRegionId, levyName) ->
                    wrapper.levyChoiceMapper().upsertRegionLevy(realtyRegionId, levyName, setBy));
        }
    }

    /**
     * Works out {@link OpenSalePins} for a change of the authority's levy to {@code next}, the
     * levy its freeholds without their own choice will use afterwards. A freehold whose own choice
     * names a defined levy keeps it whatever the authority chooses, so it is left alone, as is one
     * whose levy would not change. A levy the authority's kind may not use charges nothing, so it
     * counts as none. Call it with the authority's freeholds locked.
     */
    private static @NotNull OpenSalePins openSalePins(@NotNull SqlSessionWrapper wrapper,
                                                      @NotNull LevyCatalog catalog,
                                                      @NotNull Party authority,
                                                      int authorityPartyId,
                                                      @Nullable LevyDefinition next) {
        String nextName = next != null && SaleFeeResolver.levyFitsAuthority(next, authority) ? next.name() : null;
        Map<Integer, String> levyByRegion = new LinkedHashMap<>();
        int unpinnable = 0;
        for (int realtyRegionId : wrapper.freeholdContractMapper().selectOpenSaleRegionIdsByAuthority(authorityPartyId)) {
            String ownChoice = wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId);
            if (ownChoice != null && catalog.find(ownChoice).isPresent()) {
                continue;
            }
            LevyDefinition current = SaleFeeResolver.resolveLevy(wrapper, catalog, realtyRegionId, authority);
            String currentName = current != null && SaleFeeResolver.levyFitsAuthority(current, authority)
                    ? current.name()
                    : null;
            if (currentName == null ? nextName == null : currentName.equalsIgnoreCase(nextName)) {
                continue;
            }
            if (currentName == null) {
                unpinnable++;
            } else {
                levyByRegion.put(realtyRegionId, currentName);
            }
        }
        return new OpenSalePins(levyByRegion, unpinnable);
    }

    @Override
    public @NotNull List<LevyDefinition> listLevies() {
        return this.levies.get().all();
    }

    @Override
    public @NotNull List<String> listChosenLevyNames() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return List.copyOf(wrapper.levyChoiceMapper().selectAllChosenLevyNames());
        }
    }

    @Override
    public @Nullable String chosenLevy(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                    .selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return null;
            }
            int realtyRegionId = requireRealtyRegionId(wrapper, worldGuardRegionId, worldId);
            LevyDefinition levy = SaleFeeResolver.resolveLevy(wrapper, this.levies.get(), realtyRegionId, freehold.authority());
            return levy == null ? null : levy.name();
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
