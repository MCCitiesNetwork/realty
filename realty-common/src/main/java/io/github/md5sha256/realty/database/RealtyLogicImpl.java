package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractBid;
import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.database.entity.SaleContractBidPaymentEntity;
import io.github.md5sha256.realty.database.entity.SaleContractOfferPaymentEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferPaymentMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    // --- Auction ---

    public void createAuction(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              @NotNull UUID auctioneerId,
                              long biddingDurationSeconds,
                              long paymentDurationSeconds,
                              double minBid,
                              double minBidStep) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.saleContractAuctionMapper().createAuction(
                    worldGuardRegionId, worldId, auctioneerId, LocalDateTime.now(),
                    biddingDurationSeconds, paymentDurationSeconds, minBid, minBidStep);
            session.commit();
        }
    }

    public record CancelAuctionResult(int deleted, @NotNull List<UUID> bidderIds) {}

    public @NotNull CancelAuctionResult cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            List<UUID> bidderIds = wrapper.saleContractBidMapper().selectDistinctBidders(worldGuardRegionId, worldId);
            int deleted = wrapper.saleContractAuctionMapper().deleteActiveAuctionByRegion(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return new CancelAuctionResult(deleted, bidderIds);
        }
    }

    // --- Bid ---

    public sealed interface BidResult {
        record Success(@Nullable UUID previousBidderId) implements BidResult {}
        record NoAuction() implements BidResult {}
        record BidTooLowMinimum(double minBid) implements BidResult {}
        record BidTooLowCurrent(double currentHighest) implements BidResult {}
    }

    public @NotNull BidResult performBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID bidderId,
                                         double bidAmount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();
            SaleContractBidMapper bidMapper = wrapper.saleContractBidMapper();

            SaleContractAuctionEntity auction = auctionMapper.selectActiveByRegion(worldGuardRegionId, worldId);
            if (auction == null) {
                return new BidResult.NoAuction();
            }
            if (bidAmount < auction.minBid()) {
                return new BidResult.BidTooLowMinimum(auction.minBid());
            }
            SaleContractBid highestBid = bidMapper.selectHighestBid(worldGuardRegionId, worldId);
            if (highestBid != null && bidAmount < highestBid.bidAmount()) {
                return new BidResult.BidTooLowCurrent(highestBid.bidAmount());
            }
            UUID previousBidderId = highestBid != null ? highestBid.bidderId() : null;
            int inserted = bidMapper.performContractBid(new SaleContractBid(
                    auction.saleContractAuctionId(), bidderId, bidAmount, LocalDateTime.now()));
            if (inserted == 0) {
                // Re-fetch highest bid in case it was inserted concurrently
                SaleContractBid current = bidMapper.selectHighestBid(worldGuardRegionId, worldId);
                if (current != null && bidAmount < current.bidAmount()) {
                    return new BidResult.BidTooLowCurrent(current.bidAmount());
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
        record NoSaleContract() implements SetPriceResult {}
        record AuctionExists() implements SetPriceResult {}
        record OfferPaymentInProgress() implements SetPriceResult {}
        record BidPaymentInProgress() implements SetPriceResult {}
        record UpdateFailed() implements SetPriceResult {}
    }

    public @NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             double price) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
            if (sale == null) {
                return new SetPriceResult.NoSaleContract();
            }
            if (wrapper.saleContractAuctionMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new SetPriceResult.AuctionExists();
            }
            if (wrapper.saleContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new SetPriceResult.OfferPaymentInProgress();
            }
            if (wrapper.saleContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new SetPriceResult.BidPaymentInProgress();
            }
            int updated = saleMapper.updatePriceByRegion(worldGuardRegionId, worldId, price);
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
        record NoSaleContract() implements UnsetPriceResult {}
        record OfferPaymentInProgress() implements UnsetPriceResult {}
        record BidPaymentInProgress() implements UnsetPriceResult {}
        record UpdateFailed() implements UnsetPriceResult {}
    }

    public @NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId,
                                                  @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
            if (sale == null) {
                return new UnsetPriceResult.NoSaleContract();
            }
            if (wrapper.saleContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.OfferPaymentInProgress();
            }
            if (wrapper.saleContractBidPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new UnsetPriceResult.BidPaymentInProgress();
            }
            int updated = saleMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
            if (updated == 0) {
                return new UnsetPriceResult.UpdateFailed();
            }
            wrapper.session().commit();
            return new UnsetPriceResult.Success();
        }
    }

    // --- Buy (fixed-price) ---

    public sealed interface BuyValidation {
        record Eligible(double price, @NotNull UUID authorityId) implements BuyValidation {}
        record NoSaleContract() implements BuyValidation {}
        record NotForSale() implements BuyValidation {}
        record IsAuthority() implements BuyValidation {}
        record IsTitleHolder() implements BuyValidation {}
    }

    public @NotNull BuyValidation validateBuy(@NotNull String worldGuardRegionId,
                                               @NotNull UUID worldId,
                                               @NotNull UUID buyerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
            if (sale == null) {
                return new BuyValidation.NoSaleContract();
            }
            if (sale.price() == null) {
                return new BuyValidation.NotForSale();
            }
            if (sale.authorityId().equals(buyerId)) {
                return new BuyValidation.IsAuthority();
            }
            if (buyerId.equals(sale.titleHolderId())) {
                return new BuyValidation.IsTitleHolder();
            }
            return new BuyValidation.Eligible(sale.price(), sale.authorityId());
        }
    }

    public sealed interface BuyResult {
        record Success(@NotNull UUID authorityId) implements BuyResult {}
        record NoSaleContract() implements BuyResult {}
        record NotForSale() implements BuyResult {}
    }

    public @NotNull BuyResult executeBuy(@NotNull String worldGuardRegionId,
                                          @NotNull UUID worldId,
                                          @NotNull UUID buyerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
            if (sale == null) {
                return new BuyResult.NoSaleContract();
            }
            if (sale.price() == null) {
                return new BuyResult.NotForSale();
            }
            UUID authorityId = sale.authorityId();
            saleMapper.updateSaleByRegion(worldGuardRegionId, worldId, sale.price(), buyerId);
            saleMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
            wrapper.saleContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return new BuyResult.Success(authorityId);
        }
    }

    // --- Create Sale ---

    public boolean createSale(@NotNull String worldGuardRegionId,
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
            int saleContractId = wrapper.saleContractMapper().insertSale(regionId, price, authority, titleHolder);
            wrapper.contractMapper().insert(new ContractEntity(saleContractId, "sale", regionId));
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
            @Nullable SaleContractEntity sale,
            @Nullable LeaseContractEntity lease,
            @Nullable SaleContractAuctionEntity auction
    ) {}

    public @NotNull RegionInfo getRegionInfo(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractEntity sale = wrapper.saleContractMapper().selectByRegion(worldGuardRegionId, worldId);
            LeaseContractEntity lease = wrapper.leaseContractMapper().selectByRegion(worldGuardRegionId, worldId);
            SaleContractAuctionEntity auction = wrapper.saleContractAuctionMapper().selectActiveByRegion(worldGuardRegionId, worldId);
            return new RegionInfo(sale, lease, auction);
        }
    }

    // --- Add/Remove permission check ---

    public boolean checkRegionAuthority(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull UUID playerId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            if (saleMapper.existsByRegionAndAuthority(worldGuardRegionId, worldId, playerId)) {
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

    // --- List Outbound Offers ---

    public @NotNull List<OutboundOfferView> listOutboundOffers(@NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.saleContractOfferMapper().selectAllByOfferer(offererId);
        }
    }

    // --- List Inbound Offers ---

    public @NotNull List<InboundOfferView> listInboundOffers(@NotNull UUID authorityId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            return wrapper.saleContractOfferMapper().selectAllByAuthority(authorityId);
        }
    }

    // --- Withdraw Offer ---

    public sealed interface WithdrawOfferResult {
        record Success(@NotNull UUID authorityId) implements WithdrawOfferResult {}
        record NoOffer() implements WithdrawOfferResult {}
        record OfferAccepted() implements WithdrawOfferResult {}
    }

    public @NotNull WithdrawOfferResult withdrawOffer(@NotNull String worldGuardRegionId,
                                                       @NotNull UUID worldId,
                                                       @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            if (!wrapper.saleContractOfferMapper().existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new WithdrawOfferResult.NoOffer();
            }
            if (wrapper.saleContractOfferPaymentMapper().existsByRegion(worldGuardRegionId, worldId)) {
                return new WithdrawOfferResult.OfferAccepted();
            }
            SaleContractEntity sale = wrapper.saleContractMapper().selectByRegion(worldGuardRegionId, worldId);
            wrapper.saleContractOfferMapper().deleteOfferByOfferer(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return new WithdrawOfferResult.Success(sale.authorityId());
        }
    }

    // --- Place Offer ---

    public sealed interface OfferResult {
        record Success(@NotNull UUID authorityId) implements OfferResult {}
        record NoSaleContract() implements OfferResult {}
        record IsAuthority() implements OfferResult {}
        record AlreadyHasOffer() implements OfferResult {}
        record AuctionExists() implements OfferResult {}
        record InsertFailed() implements OfferResult {}
    }

    public @NotNull OfferResult placeOffer(@NotNull String worldGuardRegionId,
                                           @NotNull UUID worldId,
                                           @NotNull UUID offererId,
                                           double price) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractMapper saleMapper = wrapper.saleContractMapper();
            SaleContractOfferMapper offerMapper = wrapper.saleContractOfferMapper();
            SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();

            SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
            if (sale == null) {
                return new OfferResult.NoSaleContract();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new OfferResult.AuctionExists();
            }
            if (sale.authorityId().equals(offererId)) {
                return new OfferResult.IsAuthority();
            }
            if (offerMapper.existsByOfferer(worldGuardRegionId, worldId, offererId)) {
                return new OfferResult.AlreadyHasOffer();
            }
            int inserted = offerMapper.insertOffer(worldGuardRegionId, worldId, offererId, price);
            wrapper.session().commit();
            if (inserted == 0) {
                return new OfferResult.InsertFailed();
            }
            return new OfferResult.Success(sale.authorityId());
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
            SaleContractOfferMapper offerMapper = wrapper.saleContractOfferMapper();
            SaleContractOfferPaymentMapper paymentMapper = wrapper.saleContractOfferPaymentMapper();
            SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();

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
        record FullyPaid(@NotNull UUID authorityId) implements PayOfferResult {}
        record NoPaymentRecord() implements PayOfferResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayOfferResult {}
    }

    public @NotNull PayOfferResult payOffer(@NotNull String worldGuardRegionId,
                                             @NotNull UUID worldId,
                                             @NotNull UUID offererId,
                                             double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractOfferPaymentMapper paymentMapper = wrapper.saleContractOfferPaymentMapper();
            SaleContractOfferPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
            if (payment == null || !payment.offererId().equals(offererId)) {
                return new PayOfferResult.NoPaymentRecord();
            }
            double amountOwed = payment.offerPrice() - payment.currentPayment();
            if (amount > amountOwed) {
                return new PayOfferResult.ExceedsAmountOwed(amountOwed);
            }
            double newTotal = payment.currentPayment() + amount;
            if (newTotal == payment.offerPrice()) {
                // Fully paid — transfer ownership, reset price (not for sale)
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
                UUID authorityId = sale.authorityId();
                saleMapper.updateSaleByRegion(worldGuardRegionId, worldId, payment.offerPrice(), offererId);
                saleMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.saleContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
                wrapper.session().commit();
                return new PayOfferResult.FullyPaid(authorityId);
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
        record FullyPaid(@NotNull UUID authorityId) implements PayBidResult {}
        record NoPaymentRecord() implements PayBidResult {}
        record PaymentExpired() implements PayBidResult {}
        record ExceedsAmountOwed(double amountOwed) implements PayBidResult {}
    }

    public @NotNull PayBidResult payBid(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID bidderId,
                                         double amount) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            SaleContractBidPaymentMapper paymentMapper = wrapper.saleContractBidPaymentMapper();
            SaleContractBidPaymentEntity payment = paymentMapper.selectByRegion(worldGuardRegionId, worldId);
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
                // Fully paid — transfer ownership, reset price (not for sale)
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                SaleContractEntity sale = saleMapper.selectByRegion(worldGuardRegionId, worldId);
                UUID authorityId = sale.authorityId();
                saleMapper.updateSaleByRegion(worldGuardRegionId, worldId, payment.bidPrice(), bidderId);
                saleMapper.updatePriceByRegion(worldGuardRegionId, worldId, null);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.session().commit();
                return new PayBidResult.FullyPaid(authorityId);
            }
            paymentMapper.updatePayment(worldGuardRegionId, worldId, bidderId, newTotal);
            wrapper.session().commit();
            return new PayBidResult.Success(newTotal, payment.bidPrice() - newTotal);
        }
    }

    // --- Expired Bid Payments ---

    public record ExpiredBidPayment(@NotNull UUID bidderId, double refundAmount, @NotNull String regionId) {}

    public @NotNull List<ExpiredBidPayment> clearExpiredBidPayments() {
        List<SaleContractBidPaymentEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.saleContractBidPaymentMapper().selectAllExpired();
        }
        List<ExpiredBidPayment> refunds = new ArrayList<>();
        for (SaleContractBidPaymentEntity payment : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractBidPaymentMapper paymentMapper = wrapper.saleContractBidPaymentMapper();
                SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                paymentMapper.deleteByBidId(payment.bidId());
                String regionName = region != null ? region.worldGuardRegionId() : "unknown";
                refunds.add(new ExpiredBidPayment(payment.bidderId(), payment.currentPayment(), regionName));
                SaleContractAuctionEntity auction = auctionMapper.selectById(payment.saleContractAuctionId());
                if (auction != null) {
                    LocalDateTime nextDeadline = LocalDateTime.now().plusSeconds(auction.paymentDurationSeconds());
                    paymentMapper.insertNextPayment(
                            payment.saleContractAuctionId(),
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
        List<SaleContractOfferPaymentEntity> expired;
        try (SqlSessionWrapper wrapper = database.openSession()) {
            expired = wrapper.saleContractOfferPaymentMapper().selectAllExpired();
        }
        List<ExpiredOfferPayment> refunds = new ArrayList<>();
        for (SaleContractOfferPaymentEntity payment : expired) {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                RealtyRegionEntity region = wrapper.realtyRegionMapper().selectById(payment.realtyRegionId());
                wrapper.saleContractOfferPaymentMapper().deleteByOfferId(payment.offerId());
                wrapper.session().commit();
                String regionName = region != null ? region.worldGuardRegionId() : "unknown";
                refunds.add(new ExpiredOfferPayment(payment.offererId(), payment.currentPayment(), regionName));
            }
        }
        return refunds;
    }

}
