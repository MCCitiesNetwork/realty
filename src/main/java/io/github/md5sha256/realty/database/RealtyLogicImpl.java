package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
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
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferPaymentMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
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
                              long biddingDurationSeconds,
                              long paymentDurationSeconds,
                              double minBid,
                              double minBidStep) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.saleContractAuctionMapper().createAuction(
                    worldGuardRegionId, worldId, LocalDateTime.now(),
                    biddingDurationSeconds, paymentDurationSeconds, minBid, minBidStep);
            session.commit();
        }
    }

    public int cancelAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            int deleted = wrapper.saleContractAuctionMapper().deleteActiveAuctionByRegion(worldGuardRegionId, worldId);
            wrapper.session().commit();
            return deleted;
        }
    }

    // --- Bid ---

    public sealed interface BidResult {
        record Success() implements BidResult {}
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
            return new BidResult.Success();
        }
    }

    // --- Create Sale ---

    public boolean createSale(@NotNull String worldGuardRegionId,
                              @NotNull UUID worldId,
                              double price,
                              @NotNull UUID authority,
                              @NotNull UUID titleHolder) {
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
                                @NotNull UUID landlord) {
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            if (regionMapper.selectByWorldGuardRegion(worldGuardRegionId, worldId) != null) {
                return false;
            }
            int regionId = regionMapper.registerWorldGuardRegion(worldGuardRegionId, worldId);
            int leaseContractId = wrapper.leaseContractMapper().insertLease(regionId, price, durationSeconds, maxRenewals, landlord);
            wrapper.contractMapper().insert(new ContractEntity(leaseContractId, "contract", regionId));
            session.commit();
            return true;
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
            return leaseMapper.existsByRegionAndTenant(worldGuardRegionId, worldId, playerId);
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

    // --- Withdraw Offer ---

    public int withdrawOffer(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @NotNull UUID offererId) {
        try (SqlSessionWrapper wrapper = database.openSession()) {
            int deleted = wrapper.saleContractOfferMapper().deleteOfferByOfferer(worldGuardRegionId, worldId, offererId);
            wrapper.session().commit();
            return deleted;
        }
    }

    // --- Place Offer ---

    public sealed interface OfferResult {
        record Success() implements OfferResult {}
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

            if (saleMapper.selectByRegion(worldGuardRegionId, worldId) == null) {
                return new OfferResult.NoSaleContract();
            }
            if (auctionMapper.existsByRegion(worldGuardRegionId, worldId)) {
                return new OfferResult.AuctionExists();
            }
            if (saleMapper.existsByRegionAndAuthority(worldGuardRegionId, worldId, offererId)) {
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
            return new OfferResult.Success();
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
            wrapper.session().commit();
            if (inserted == 0) {
                return new AcceptOfferResult.InsertFailed();
            }
            return new AcceptOfferResult.Success();
        }
    }

    // --- Pay Offer ---

    public sealed interface PayOfferResult {
        record Success(double newTotal, double remaining) implements PayOfferResult {}
        record FullyPaid() implements PayOfferResult {}
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
                // Fully paid — transfer ownership
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                saleMapper.updateSaleByRegion(worldGuardRegionId, worldId, payment.offerPrice(), offererId);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.saleContractOfferMapper().deleteOffers(worldGuardRegionId, worldId);
                wrapper.session().commit();
                return new PayOfferResult.FullyPaid();
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
        record FullyPaid() implements PayBidResult {}
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
                // Fully paid — transfer ownership
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                saleMapper.updateSaleByRegion(worldGuardRegionId, worldId, payment.bidPrice(), bidderId);
                paymentMapper.deleteByRegion(worldGuardRegionId, worldId);
                wrapper.session().commit();
                return new PayBidResult.FullyPaid();
            }
            paymentMapper.updatePayment(worldGuardRegionId, worldId, bidderId, newTotal);
            wrapper.session().commit();
            return new PayBidResult.Success(newTotal, payment.bidPrice() - newTotal);
        }
    }

}
