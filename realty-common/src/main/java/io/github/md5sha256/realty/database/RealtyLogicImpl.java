package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.entity.AgentHistoryEntity;
import io.github.md5sha256.realty.database.entity.ExpiredLeaseView;
import io.github.md5sha256.realty.database.entity.FreeholdContractAgentInviteEntity;
import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.database.entity.LeaseHistoryEntity;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractSanctionedAuctioneerMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RealtyLogicImpl {

    private final Database database;
    // TODO: load from configuration
    private long offerPaymentDurationSeconds = 86400;

    public RealtyLogicImpl(@NotNull Database database) {
        this.database = database;
    }

    public void setOfferPaymentDurationSeconds(long offerPaymentDurationSeconds) {
        this.offerPaymentDurationSeconds = offerPaymentDurationSeconds;
    }

    // --- Sanctioned Auctioneers ---

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

    public sealed interface InviteAgentResult {
        record Success() implements InviteAgentResult {}
        record NoFreeholdContract() implements InviteAgentResult {}
        record NotTitleHolder() implements InviteAgentResult {}
        record AlreadyInvited() implements InviteAgentResult {}
    }

    public @NotNull InviteAgentResult inviteAgent(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull UUID inviterId,
                                                   @NotNull UUID inviteeId) {
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

    public sealed interface AcceptAgentInviteResult {
        record Success(@NotNull UUID inviterId) implements AcceptAgentInviteResult {}
        record NotFound() implements AcceptAgentInviteResult {}
    }

    public @NotNull AcceptAgentInviteResult acceptAgentInvite(@NotNull String worldGuardRegionId,
                                                               @NotNull UUID worldId,
                                                               @NotNull UUID inviteeId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            FreeholdContractAgentInviteEntity invite = wrapper.freeholdContractAgentInviteMapper()
                    .selectByRegionAndInvitee(worldGuardRegionId, worldId, inviteeId);
            if (invite == null) {
                return new AcceptAgentInviteResult.NotFound();
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

    public sealed interface WithdrawAgentInviteResult {
        record Success() implements WithdrawAgentInviteResult {}
        record NotFound() implements WithdrawAgentInviteResult {}
    }

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

    public sealed interface RejectAgentInviteResult {
        record Success(@NotNull UUID inviterId) implements RejectAgentInviteResult {}
        record NotFound() implements RejectAgentInviteResult {}
    }

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

    public sealed interface CreateAuctionResult {
        record Success() implements CreateAuctionResult {}
        record NotSanctioned() implements CreateAuctionResult {}
        record NoFreeholdContract() implements CreateAuctionResult {}
    }

    public @NotNull CreateAuctionResult createAuction(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @NotNull UUID auctioneerId,
                              long biddingDurationSeconds,
                              long paymentDurationSeconds,
                              double minBid,
                              double minBidStep) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new CreateAuctionResult.NoFreeholdContract();
            }
            if (!auctioneerId.equals(freehold.authorityId())
                    && !auctioneerId.equals(freehold.titleHolderId())
                    && !wrapper.freeholdContractSanctionedAuctioneerMapper()
                            .existsByRegionAndAuctioneer(worldGuardRegionId, worldId, auctioneerId)) {
                return new CreateAuctionResult.NotSanctioned();
            }
            wrapper.freeholdContractAuctionMapper().createAuction(
                    worldGuardRegionId, worldId, auctioneerId, LocalDateTime.now(),
                    biddingDurationSeconds, paymentDurationSeconds, minBid, minBidStep);
            session.commit();
            return new CreateAuctionResult.Success();
        }
    }

    public record CancelAuctionResult(int deleted, @NotNull List<UUID> bidderIds) {}

    public @NotNull CancelAuctionResult cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            List<UUID> bidderIds = wrapper.freeholdContractBidMapper().selectDistinctBidders(worldGuardRegionId, worldId);
            int deleted = wrapper.freeholdContractAuctionMapper().deleteActiveAuctionByRegion(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return new CancelAuctionResult(deleted, bidderIds);
        }
    }

    // --- Bid ---

    public sealed interface BidResult {
        record Success(@Nullable UUID previousBidderId) implements BidResult {}
        record NoAuction() implements BidResult {}
        record IsOwner() implements BidResult {}
        record BidTooLowMinimum(double minBid) implements BidResult {}
        record BidTooLowCurrent(double currentHighest) implements BidResult {}
        record AlreadyHighestBidder() implements BidResult {}
    }

    public @NotNull BidResult performBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID bidderId,
                                         double bidAmount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();
            FreeholdContractBidMapper bidMapper = wrapper.freeholdContractBidMapper();

            FreeholdContractAuctionEntity auction = auctionMapper.selectActiveByRegion(worldGuardRegionId, worldId);
            if (auction == null) {
                return new BidResult.NoAuction();
            }
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null && (bidderId.equals(freehold.authorityId())
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

    // --- Set Price ---

    public sealed interface SetPriceResult {
        record Success() implements SetPriceResult {}
        record NoFreeholdContract() implements SetPriceResult {}
        record AuctionExists() implements SetPriceResult {}
        record OfferPaymentInProgress() implements SetPriceResult {}
        record BidPaymentInProgress() implements SetPriceResult {}
        record UpdateFailed() implements SetPriceResult {}
    }

    public @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             double price) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetPriceResult.NoFreeholdContract();
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
            int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, price);
            if (updated == 0) {
                return new SetPriceResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetPriceResult.Success();
        }
    }

    // --- Unset Price ---

    public sealed interface UnsetPriceResult {
        record Success() implements UnsetPriceResult {}
        record NoFreeholdContract() implements UnsetPriceResult {}
        record OfferPaymentInProgress() implements UnsetPriceResult {}
        record BidPaymentInProgress() implements UnsetPriceResult {}
        record UpdateFailed() implements UnsetPriceResult {}
    }

    public @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                                  @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new UnsetPriceResult.NoFreeholdContract();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.OfferPaymentInProgress();
            }
            if (wrapper.freeholdContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.BidPaymentInProgress();
            }
            int updated = freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
            if (updated == 0) {
                return new UnsetPriceResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new UnsetPriceResult.Success();
        }
    }

    // --- Set Duration ---

    public sealed interface SetDurationResult {
        record Success() implements SetDurationResult {}
        record NoLeaseContract() implements SetDurationResult {}
        record UpdateFailed() implements SetDurationResult {}
    }

    public @NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId,
                                                    @NotNull UUID worldId,
                                                    long durationSeconds) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetDurationResult.NoLeaseContract();
            }
            int updated = leaseMapper.updateDurationByRegion(worldGuardRegionId, worldId, durationSeconds);
            if (updated == 0) {
                return new SetDurationResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetDurationResult.Success();
        }
    }

    // --- Set Landlord ---

    public sealed interface SetLandlordResult {
        record Success() implements SetLandlordResult {}
        record NoLeaseContract() implements SetLandlordResult {}
        record UpdateFailed() implements SetLandlordResult {}
    }

    public @NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId,
                                                    @NotNull UUID worldId,
                                                    @NotNull UUID landlordId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetLandlordResult.NoLeaseContract();
            }
            int updated = leaseMapper.updateLandlordByRegion(worldGuardRegionId, worldId, landlordId);
            if (updated == 0) {
                return new SetLandlordResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetLandlordResult.Success();
        }
    }

    // --- Set Title Holder ---

    public sealed interface SetTitleHolderResult {
        record Success() implements SetTitleHolderResult {}
        record NoFreeholdContract() implements SetTitleHolderResult {}
        record UpdateFailed() implements SetTitleHolderResult {}
    }

    public @NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId,
                                                         @NotNull UUID worldId,
                                                         @Nullable UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new SetTitleHolderResult.NoFreeholdContract();
            }
            int updated = freeholdMapper.updateTitleHolderByRegion(worldGuardRegionId, worldId, titleHolderId);
            if (updated == 0) {
                return new SetTitleHolderResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetTitleHolderResult.Success();
        }
    }

    // --- Set Tenant ---

    public sealed interface SetTenantResult {
        record Success() implements SetTenantResult {}
        record NoLeaseContract() implements SetTenantResult {}
        record UpdateFailed() implements SetTenantResult {}
    }

    public @NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId,
                                                @NotNull UUID worldId,
                                                @Nullable UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new SetTenantResult.NoLeaseContract();
            }
            int updated = leaseMapper.updateTenantByRegion(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new SetTenantResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new SetTenantResult.Success();
        }
    }

    // --- Buy (fixed-price) ---

    public sealed interface BuyValidation {
        record Eligible(double price, @NotNull UUID authorityId) implements BuyValidation {}
        record NoFreeholdContract() implements BuyValidation {}
        record NotForFreehold() implements BuyValidation {}
        record IsAuthority() implements BuyValidation {}
        record IsTitleHolder() implements BuyValidation {}
    }

    public @NotNull BuyValidation validateBuy(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull UUID buyerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new BuyValidation.NoFreeholdContract();
            }
            if (freehold.price() == null) {
                return new BuyValidation.NotForFreehold();
            }
            if (freehold.authorityId().equals(buyerId)) {
                return new BuyValidation.IsAuthority();
            }
            if (buyerId.equals(freehold.titleHolderId())) {
                return new BuyValidation.IsTitleHolder();
            }
            return new BuyValidation.Eligible(freehold.price(), freehold.authorityId());
        }
    }

    public sealed interface BuyResult {
        record Success(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements BuyResult {}
        record NoFreeholdContract() implements BuyResult {}
        record NotForFreehold() implements BuyResult {}
    }

    public @NotNull BuyResult executeBuy(@NotNull String worldGuardRegionId,
                                          @NotNull UUID worldId,
                                          @NotNull UUID buyerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new BuyResult.NoFreeholdContract();
            }
            if (freehold.price() == null) {
                return new BuyResult.NotForFreehold();
            }
            UUID authorityId = freehold.authorityId();
            UUID titleHolderId = freehold.titleHolderId();
            freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, freehold.price(), buyerId);
            freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
            wrapper.freeholdContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
            wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.BUY.name(),
                    buyerId, authorityId, freehold.price());
            wrapper.session().commit();
            return new BuyResult.Success(authorityId, titleHolderId);
        }
    }

    // --- Create Freehold ---

    public boolean createFreehold(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @Nullable Double price,
                              @NotNull UUID authority,
                              @Nullable UUID titleHolder) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            if (regionMapper.selectByWorldGuardRegion(worldGuardRegionId, worldId) != null) {
                return false;
            }
            int regionId = regionMapper.registerWorldGuardRegion(worldGuardRegionId, worldId);
            int freeholdContractId = wrapper.freeholdContractMapper().insertFreehold(regionId, price, authority, titleHolder);
            wrapper.contractMapper().insert(new ContractEntity(freeholdContractId, "freehold", regionId));
            session.commit();
            return true;
        }
    }

    // --- Create Rental ---

    public boolean createRental(@NotNull String worldGuardRegionId,
                                @NotNull UUID worldId,
                                double price,
                                long durationSeconds,
                                int maxRenewals,
                                @NotNull UUID landlordId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            if (regionMapper.selectByWorldGuardRegion(worldGuardRegionId, worldId) != null) {
                return false;
            }
            int regionId = regionMapper.registerWorldGuardRegion(worldGuardRegionId, worldId);
            int leaseContractId = wrapper.leaseContractMapper().insertLease(regionId, price, durationSeconds, maxRenewals, landlordId, null);
            wrapper.contractMapper().insert(new ContractEntity(leaseContractId, "contract", regionId));
            session.commit();
            return true;
        }
    }

    // --- Rent ---

    public sealed interface RentResult {
        record Success(double price, @NotNull UUID landlordId) implements RentResult {}
        record NoLeaseContract() implements RentResult {}
        record IsLandlord() implements RentResult {}
        record AlreadyOccupied() implements RentResult {}
        record UpdateFailed() implements RentResult {}
    }

    public @NotNull RentResult rentRegion(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new RentResult.NoLeaseContract();
            }
            if (lease.landlordId().equals(tenantId)) {
                return new RentResult.IsLandlord();
            }
            if (lease.tenantId() != null) {
                return new RentResult.AlreadyOccupied();
            }
            int updated = leaseMapper.rentRegion(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new RentResult.UpdateFailed();
            }
            wrapper.leaseHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.RENT.name(),
                    tenantId, lease.landlordId(), lease.price(), lease.durationSeconds(), null);
            wrapper.session().commit();
            return new RentResult.Success(lease.price(), lease.landlordId());
        }
    }

    // --- Renew Lease ---

    public sealed interface RenewLeaseResult {
        record Success(double price, @NotNull UUID landlordId) implements RenewLeaseResult {}
        record NoLeaseContract() implements RenewLeaseResult {}
        record NotTenant() implements RenewLeaseResult {}
        record NoExtensionsRemaining() implements RenewLeaseResult {}
        record UpdateFailed() implements RenewLeaseResult {}
    }

    public @NotNull RenewLeaseResult renewLease(@NotNull String worldGuardRegionId,
                                                 @NotNull UUID worldId,
                                                 @NotNull UUID tenantId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            if (lease == null) {
                return new RenewLeaseResult.NoLeaseContract();
            }
            if (!tenantId.equals(lease.tenantId())) {
                return new RenewLeaseResult.NotTenant();
            }
            if (lease.maxExtensions() != null && lease.currentMaxExtensions() >= lease.maxExtensions()) {
                return new RenewLeaseResult.NoExtensionsRemaining();
            }
            int updated = leaseMapper.renewLease(worldGuardRegionId, worldId, tenantId);
            if (updated == 0) {
                return new RenewLeaseResult.UpdateFailed();
            }
            Integer extensionsRemaining = null;
            if (lease.maxExtensions() != null) {
                extensionsRemaining = lease.maxExtensions() - (lease.currentMaxExtensions() + 1);
            }
            wrapper.leaseHistoryMapper().insert(worldGuardRegionId, worldId, "RENEW",
                    tenantId, lease.landlordId(), lease.price(), lease.durationSeconds(), extensionsRemaining);
            wrapper.session().commit();
            return new RenewLeaseResult.Success(lease.price(), lease.landlordId());
        }
    }

    // --- Delete ---

    public int deleteRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            int deleted = wrapper.realtyRegionMapper().deleteByWorldGuardRegion(worldGuardRegionId, worldId);
            session.commit();
            return deleted;
        }
    }

    // --- Info ---

    public record RegionInfo(
            @Nullable FreeholdContractEntity freehold,
            @Nullable LeaseContractEntity lease,
            @Nullable FreeholdContractAuctionEntity auction,
            @Nullable Double lastSoldPrice
    ) {}

    public @NotNull RegionInfo getRegionInfo(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            LeaseContractEntity lease = wrapper.leaseContractMapper().selectByRegion(worldGuardRegionId, worldId);
            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper().selectActiveByRegion(worldGuardRegionId, worldId);
            Double lastSoldPrice = freehold != null
                    ? wrapper.freeholdHistoryMapper().selectLastFreeholdPrice(worldGuardRegionId, worldId)
                    : null;
            return new RegionInfo(freehold, lease, auction, lastSoldPrice);
        }
    }

    public @Nullable RegionState getRegionState(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null) {
                return freehold.titleHolderId() != null ? RegionState.SOLD : RegionState.FOR_SALE;
            }
            LeaseContractEntity lease = wrapper.leaseContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (lease != null) {
                return lease.tenantId() != null ? RegionState.LEASED : RegionState.FOR_LEASE;
            }
            return null;
        }
    }

    /**
     * Builds a placeholder map for the given region, containing all info-level
     * properties: region, title_holder, authority, price, last_sold_price,
     * landlord, tenant, duration, start_date, end_date, extensions, has_auction.
     */
    public @NotNull Map<String, String> getRegionPlaceholders(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("region", worldGuardRegionId);

            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (freehold != null) {
                placeholders.put("title_holder", freehold.titleHolderId() != null ? freehold.titleHolderId().toString() : "");
                placeholders.put("authority", freehold.authorityId().toString());
                placeholders.put("price", freehold.price() != null ? String.valueOf(freehold.price()) : "");
                Double lastSoldPrice = wrapper.freeholdHistoryMapper().selectLastFreeholdPrice(worldGuardRegionId, worldId);
                placeholders.put("last_sold_price", lastSoldPrice != null ? String.valueOf(lastSoldPrice) : "");
            }

            LeaseContractEntity lease = wrapper.leaseContractMapper().selectByRegion(worldGuardRegionId, worldId);
            if (lease != null) {
                placeholders.put("landlord", lease.landlordId().toString());
                placeholders.put("tenant", lease.tenantId() != null ? lease.tenantId().toString() : "");
                placeholders.put("price", String.valueOf(lease.price()));
                placeholders.put("duration", DurationFormatter.format(Duration.ofSeconds(lease.durationSeconds())));
                placeholders.put("start_date", lease.startDate().toString());
                LocalDateTime endDate = lease.startDate().plusSeconds(lease.durationSeconds());
                placeholders.put("end_date", endDate.toString());
                if (lease.maxExtensions() != null) {
                    placeholders.put("extensions", lease.currentMaxExtensions() + "/" + lease.maxExtensions());
                } else {
                    placeholders.put("extensions", "unlimited");
                }
            }

            FreeholdContractAuctionEntity auction = wrapper.freeholdContractAuctionMapper()
                    .selectActiveByRegion(worldGuardRegionId, worldId);
            placeholders.put("has_auction", auction != null ? "true" : "false");

            return placeholders;
        }
    }


    public record RegionWithState(
            @NotNull RealtyRegionEntity region,
            @NotNull RegionState state,
            @NotNull Map<String, String> placeholders
    ) {}

    public @NotNull List<RegionWithState> getAllRegionsWithState() {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            List<RealtyRegionEntity> regions = wrapper.realtyRegionMapper().selectAll();
            List<RegionWithState> result = new ArrayList<>();
            for (RealtyRegionEntity region : regions) {
                Map<String, String> placeholders = getRegionPlaceholders(
                        region.worldGuardRegionId(), region.worldId());
                FreeholdContractEntity freehold = wrapper.freeholdContractMapper()
                        .selectByRegion(region.worldGuardRegionId(), region.worldId());
                if (freehold != null) {
                    result.add(new RegionWithState(region,
                            freehold.titleHolderId() != null ? RegionState.SOLD : RegionState.FOR_SALE,
                            placeholders));
                    continue;
                }
                LeaseContractEntity lease = wrapper.leaseContractMapper()
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

    // --- Add/Remove permission check ---

    public boolean checkRegionAuthority(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            if (freeholdMapper.existsByRegionAndAuthority(worldGuardRegionId, worldId, playerId)) {
                return true;
            }
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            if (leaseMapper.existsByRegionAndTenant(worldGuardRegionId, worldId, playerId)) {
                return true;
            }
            LeaseContractEntity lease = leaseMapper.selectByRegion(worldGuardRegionId, worldId);
            return lease != null && lease.landlordId().equals(playerId);
        }
    }

    // --- List ---

    public record ListResult(
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

    public @NotNull ListResult listRegions(@NotNull UUID targetId, int limit, int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            int ownedCount = regionMapper.countRegionsByTitleHolder(targetId);
            int landlordCount = regionMapper.countRegionsByAuthority(targetId);
            int rentedCount = regionMapper.countRegionsByTenant(targetId);

            int remaining = limit;
            int catOffset = offset;

            List<RealtyRegionEntity> owned = regionMapper.selectRegionsByTitleHolder(targetId, remaining, catOffset);
            remaining -= owned.size();
            catOffset = Math.max(0, catOffset - ownedCount);

            List<RealtyRegionEntity> landlordRegions = remaining > 0
                    ? regionMapper.selectRegionsByAuthority(targetId, remaining, catOffset)
                    : List.of();
            remaining -= landlordRegions.size();
            catOffset = Math.max(0, catOffset - landlordCount);

            List<RealtyRegionEntity> rented = remaining > 0
                    ? regionMapper.selectRegionsByTenant(targetId, remaining, catOffset)
                    : List.of();

            return new ListResult(ownedCount, landlordCount, rentedCount, owned, landlordRegions, rented);
        }
    }

    public record SingleCategoryResult(
            int totalCount,
            @NotNull List<RealtyRegionEntity> regions
    ) { }

    public @NotNull SingleCategoryResult listOwnedRegions(@NotNull UUID targetId, int limit, int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByTitleHolder(targetId);
            List<RealtyRegionEntity> regions = mapper.selectRegionsByTitleHolder(targetId, limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    public @NotNull SingleCategoryResult listRentedRegions(@NotNull UUID targetId, int limit, int offset) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            RealtyRegionMapper mapper = wrapper.realtyRegionMapper();
            int count = mapper.countRegionsByTenant(targetId);
            List<RealtyRegionEntity> regions = mapper.selectRegionsByTenant(targetId, limit, offset);
            return new SingleCategoryResult(count, regions);
        }
    }

    // --- List Outbound Offers ---

    public @NotNull List<OutboundOfferView> listOutboundOffers(@NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferMapper().selectAllByOfferer(offererId);
        }
    }

    // --- List Inbound Offers ---

    public @NotNull List<InboundOfferView> listInboundOffers(@NotNull UUID titleHolderId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.freeholdContractOfferMapper().selectAllByTitleHolder(titleHolderId);
        }
    }

    // --- Withdraw Offer ---

    public sealed interface WithdrawOfferResult {
        record Success(@Nullable UUID titleHolderId) implements WithdrawOfferResult {}
        record NoOffer() implements WithdrawOfferResult {}
        record OfferAccepted() implements WithdrawOfferResult {}
    }

    public @NotNull WithdrawOfferResult withdrawOffer(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            if (!wrapper.freeholdContractOfferMapper().existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new WithdrawOfferResult.NoOffer();
            }
            if (wrapper.freeholdContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new WithdrawOfferResult.OfferAccepted();
            }
            FreeholdContractEntity freehold = wrapper.freeholdContractMapper().selectByRegion(worldGuardRegionId, worldId);
            wrapper.freeholdContractOfferMapper().deleteOfferByOfferer(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new WithdrawOfferResult.Success(freehold.titleHolderId());
        }
    }

    // --- Reject Offer ---

    public sealed interface RejectOfferResult {
        record Success(@NotNull UUID offererId) implements RejectOfferResult {}
        record NoOffer() implements RejectOfferResult {}
        record OfferAccepted() implements RejectOfferResult {}
    }

    public @NotNull RejectOfferResult rejectOffer(@NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId,
                                                      @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
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

    public sealed interface RejectAllOffersResult {
        record Success(@NotNull List<UUID> offererIds) implements RejectAllOffersResult {}
        record NoFreeholdContract() implements RejectAllOffersResult {}
        record OfferAccepted() implements RejectAllOffersResult {}
    }

    public @NotNull RejectAllOffersResult rejectAllOffers(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            if (freeholdMapper.selectByRegion(worldGuardRegionId, worldId) == null) {
                return new RejectAllOffersResult.NoFreeholdContract();
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

    public sealed interface OfferResult {
        record Success(@Nullable UUID titleHolderId) implements OfferResult {}
        record NoFreeholdContract() implements OfferResult {}
        record IsOwner() implements OfferResult {}
        record AlreadyHasOffer() implements OfferResult {}
        record AuctionExists() implements OfferResult {}
        record InsertFailed() implements OfferResult {}
    }

    public @NotNull OfferResult placeOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID offererId,
                                           double price) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();

            FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
            if (freehold == null) {
                return new OfferResult.NoFreeholdContract();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new OfferResult.AuctionExists();
            }
            if (offererId.equals(freehold.authorityId()) || offererId.equals(freehold.titleHolderId())) {
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

    // --- Accept Offer ---

    public sealed interface AcceptOfferResult {
        record Success() implements AcceptOfferResult {}
        record NoOffer() implements AcceptOfferResult {}
        record AuctionExists() implements AcceptOfferResult {}
        record AlreadyAccepted() implements AcceptOfferResult {}
        record InsertFailed() implements AcceptOfferResult {}
    }

    public @NotNull AcceptOfferResult acceptOffer(@NotNull String worldGuardRegionId,
                                                   @NotNull UUID worldId,
                                                   @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractOfferMapper offerMapper = wrapper.freeholdContractOfferMapper();
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractAuctionMapper auctionMapper = wrapper.freeholdContractAuctionMapper();

            if (offerMapper.selectByOfferer(worldGuardRegionId, worldId, offererId) == null) {
                return new AcceptOfferResult.NoOffer();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AuctionExists();
            }
            if (paymentMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new AcceptOfferResult.AlreadyAccepted();
            }
            LocalDateTime paymentDeadline = LocalDateTime.now().plusSeconds(offerPaymentDurationSeconds);
            int inserted = paymentMapper.insertPayment(worldGuardRegionId, worldId, offererId, 0, paymentDeadline);
            if (inserted == 0) {
                return new AcceptOfferResult.InsertFailed();
            }
            offerMapper.deleteOtherOffers(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new AcceptOfferResult.Success();
        }
    }

    // --- Pay Offer ---

    public sealed interface PayOfferResult {
        record Success(double newTotal, double remaining) implements PayOfferResult {}
        record FullyPaid(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayOfferResult {}
        record NoPaymentRecord() implements PayOfferResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayOfferResult {}
    }

    public @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull UUID offererId,
                                             double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            FreeholdContractOfferPaymentMapper paymentMapper = wrapper.freeholdContractOfferPaymentMapper();
            FreeholdContractOfferPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment == null || !payment.offererId().equals(offererId)) {
                return new PayOfferResult.NoPaymentRecord();
            }
            double amountOwed = payment.offerPrice() - payment.currentPayment();
            if (amount > amountOwed) {
                return new PayOfferResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = payment.currentPayment() + amount;
            if (newTotal == payment.offerPrice()) {
                // Fully paid — transfer ownership, reset price (not for freehold)
                FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
                FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
                UUID authorityId = freehold.authorityId();
                UUID titleHolderId = freehold.titleHolderId();
                freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.offerPrice(), offererId);
                freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.freeholdContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
                wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.OFFER_BUY.name(),
                        offererId, authorityId, payment.offerPrice());
                wrapper.session().commit();
                return new PayOfferResult.FullyPaid(authorityId, titleHolderId);
            } else {
                paymentMapper.updatePayment(worldGuardRegionId, worldId, offererId, newTotal);
            }
            wrapper.session().commit();
            return new PayOfferResult.Success(newTotal, payment.offerPrice() - newTotal);
        }
    }

    // --- Pay Bid ---

    public sealed interface PayBidResult {
        record Success(double newTotal, double remaining) implements PayBidResult {}
        record FullyPaid(@NotNull UUID authorityId, @Nullable UUID titleHolderId) implements PayBidResult {}
        record NoPaymentRecord() implements PayBidResult {}
        record PaymentExpired() implements PayBidResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayBidResult {}
    }

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
            if (amount > amountOwed) {
                return new PayBidResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = payment.currentPayment() + amount;
            if (newTotal == payment.bidPrice()) {
                // Fully paid — transfer ownership, reset price (not for freehold)
                FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
                FreeholdContractEntity freehold = freeholdMapper.selectByRegion(worldGuardRegionId, worldId);
                UUID authorityId = freehold.authorityId();
                UUID titleHolderId = freehold.titleHolderId();
                freeholdMapper.updateFreeholdByRegion(worldGuardRegionId, worldId, payment.bidPrice(), bidderId);
                freeholdMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.freeholdContractSanctionedAuctioneerMapper().deleteAllByRegion(worldGuardRegionId, worldId);
                wrapper.freeholdHistoryMapper().insert(worldGuardRegionId, worldId, HistoryEventType.AUCTION_BUY.name(),
                        bidderId, authorityId, payment.bidPrice());
                wrapper.session().commit();
                return new PayBidResult.FullyPaid(authorityId, titleHolderId);
            }
            paymentMapper.updatePayment(worldGuardRegionId, worldId, bidderId, newTotal);
            wrapper.session().commit();
            return new PayBidResult.Success(newTotal, payment.bidPrice() - newTotal);
        }
    }

    // --- Expired Bid Payments ---

    public record ExpiredBidPayment(@NotNull UUID bidderId, double refundAmount, @NotNull String regionId) {}

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
                refunds.add(new ExpiredBidPayment(payment.bidderId(), payment.currentPayment(), regionName));
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

    public record ExpiredOfferPayment(@NotNull UUID offererId, double refundAmount, @NotNull String regionId) {}

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
                refunds.add(new ExpiredOfferPayment(payment.offererId(), payment.currentPayment(), regionName));
            }
        }
        return refunds;
    }

    // --- Expired Leases ---

    public record ExpiredLease(
            @NotNull UUID tenantId,
            @NotNull UUID landlordId,
            @NotNull String worldGuardRegionId,
            @NotNull UUID worldId
    ) {}

    public @NotNull List<ExpiredLease> clearExpiredLeases() {
        List<ExpiredLeaseView> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.leaseContractMapper().selectExpiredLeases();
        }
        List<ExpiredLease> results = new ArrayList<>();
        for (ExpiredLeaseView lease : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                wrapper.leaseContractMapper().clearTenant(lease.leaseContractId());
                wrapper.leaseHistoryMapper().insert(lease.worldGuardRegionId(), lease.worldId(),
                        HistoryEventType.LEASE_EXPIRY.name(), lease.tenantId(), lease.landlordId(),
                        null, null, null);
                wrapper.session().commit();
                results.add(new ExpiredLease(lease.tenantId(), lease.landlordId(),
                        lease.worldGuardRegionId(), lease.worldId()));
            }
        }
        return results;
    }

    // --- History Search ---

    public record HistoryResult(@NotNull List<HistoryEntry> entries, int totalCount) {}

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
            int leaseCount = wrapper.leaseHistoryMapper()
                    .countHistory(worldGuardRegionId, worldId, eventType, since, playerId);
            int agentCount = wrapper.agentHistoryMapper()
                    .countHistory(worldGuardRegionId, worldId, eventType, since, playerId);
            int totalCount = freeholdCount + leaseCount + agentCount;

            List<FreeholdHistoryEntity> freeholdResults = wrapper.freeholdHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);
            List<LeaseHistoryEntity> leaseResults = wrapper.leaseHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);
            List<AgentHistoryEntity> agentResults = wrapper.agentHistoryMapper()
                    .searchHistory(worldGuardRegionId, worldId, eventType, since, playerId, limit, offset);

            List<HistoryEntry> combined = new ArrayList<>(freeholdResults.size() + leaseResults.size() + agentResults.size());
            for (FreeholdHistoryEntity e : freeholdResults) {
                combined.add(new HistoryEntry.Freehold(e.eventType(), e.eventTime(),
                        e.buyerId(), e.authorityId(), e.price()));
            }
            for (LeaseHistoryEntity e : leaseResults) {
                combined.add(new HistoryEntry.Lease(e.eventType(), e.eventTime(),
                        e.tenantId(), e.landlordId(), e.price(), e.durationSeconds(), e.extensionsRemaining()));
            }
            for (AgentHistoryEntity e : agentResults) {
                combined.add(new HistoryEntry.Agent(e.eventType(), e.eventTime(),
                        e.agentId(), e.actorId()));
            }
            combined.sort((a, b) -> b.eventTime().compareTo(a.eventTime()));
            return new HistoryResult(combined, totalCount);
        }
    }

}
