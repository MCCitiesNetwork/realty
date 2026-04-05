package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.util.ExecutorState;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealtyPaperApiImpl implements RealtyPaperApi {

    private final RealtyBackend realtyApi;
    private final Economy economy;
    private final ExecutorState executorState;
    private final Database database;
    private final RegionProfileService regionProfileService;
    private final SignTextApplicator signTextApplicator;
    private final SignCache signCache;

    public RealtyPaperApiImpl(@NotNull RealtyBackend realtyApi,
                              @NotNull Economy economy,
                              @NotNull ExecutorState executorState,
                              @NotNull Database database,
                              @NotNull RegionProfileService regionProfileService,
                              @NotNull SignTextApplicator signTextApplicator,
                              @NotNull SignCache signCache) {
        this.realtyApi = realtyApi;
        this.economy = economy;
        this.executorState = executorState;
        this.database = database;
        this.regionProfileService = regionProfileService;
        this.signTextApplicator = signTextApplicator;
        this.signCache = signCache;
    }

    // ═══════════════════════════════════════════════════
    // COMPLEX OPERATIONS
    // ═══════════════════════════════════════════════════

    @Override
    public @NotNull CompletableFuture<BuyResult> buy(@NotNull WorldGuardRegion region,
                                                      @NotNull UUID buyerId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(
                () -> realtyApi.validateBuy(regionId, worldId, buyerId),
                executorState.dbExec()
        ).thenComposeAsync(validation -> switch (validation) {
            case RealtyBackend.BuyValidation.Eligible eligible ->
                    handleBuyPayment(region, regionId, worldId, buyerId, eligible);
            case RealtyBackend.BuyValidation.NoFreeholdContract ignored ->
                    CompletableFuture.completedFuture(new BuyResult.NoFreeholdContract(regionId));
            case RealtyBackend.BuyValidation.NotForFreehold ignored ->
                    CompletableFuture.completedFuture(new BuyResult.NotForSale(regionId));
            case RealtyBackend.BuyValidation.IsAuthority ignored ->
                    CompletableFuture.completedFuture(new BuyResult.IsAuthority());
            case RealtyBackend.BuyValidation.IsTitleHolder ignored ->
                    CompletableFuture.completedFuture(new BuyResult.IsTitleHolder());
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new BuyResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    private @NotNull CompletableFuture<BuyResult> handleBuyPayment(
            @NotNull WorldGuardRegion region, @NotNull String regionId,
            @NotNull UUID worldId, @NotNull UUID buyerId,
            @NotNull RealtyBackend.BuyValidation.Eligible eligible) {
        double price = eligible.price();
        OfflinePlayer buyer = Bukkit.getOfflinePlayer(buyerId);
        double balance = economy.getBalance(buyer);
        if (balance < price) {
            return CompletableFuture.completedFuture(
                    new BuyResult.InsufficientFunds(price, balance));
        }
        EconomyResponse response = economy.withdrawPlayer(buyer, price);
        if (!response.transactionSuccess()) {
            return CompletableFuture.completedFuture(
                    new BuyResult.PaymentFailed(response.errorMessage));
        }
        return CompletableFuture.supplyAsync(() -> {
            RealtyBackend.BuyResult result = realtyApi.executeBuy(regionId, worldId, buyerId);
            Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
            return Map.entry(result, placeholders);
        }, executorState.dbExec()).thenApplyAsync(entry -> {
            if (!(entry.getKey() instanceof RealtyBackend.BuyResult.Success success)) {
                economy.depositPlayer(buyer, price);
                return new BuyResult.TransferFailed(regionId);
            }
            UUID recipientId = success.titleHolderId() != null
                    ? success.titleHolderId() : success.authorityId();
            OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
            economy.depositPlayer(recipient, price);
            ProtectedRegion protectedRegion = region.region();
            protectedRegion.getOwners().clear();
            protectedRegion.getOwners().addPlayer(buyerId);
            protectedRegion.getMembers().clear();
            regionProfileService.applyFlags(region, RegionState.SOLD, entry.getValue());
            signTextApplicator.updateLoadedSigns(region.world(), regionId,
                    RegionState.SOLD, entry.getValue());
            updateChildLandlords(regionId, region.world(), buyerId);
            return (BuyResult) new BuyResult.Success(price, regionId, success.titleHolderId());
        }, executorState.mainThreadExec());
    }

    @Override
    public @NotNull CompletableFuture<RentResult> rent(@NotNull WorldGuardRegion region,
                                                        @NotNull UUID tenantId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(
                () -> realtyApi.previewRent(regionId, worldId),
                executorState.dbExec()
        ).thenComposeAsync(preview -> switch (preview) {
            case RealtyBackend.RentResult.Success success ->
                    handleRentPayment(region, regionId, worldId, tenantId, success);
            case RealtyBackend.RentResult.NoLeaseholdContract ignored ->
                    CompletableFuture.completedFuture(new RentResult.NoLeaseholdContract(regionId));
            case RealtyBackend.RentResult.AlreadyOccupied ignored ->
                    CompletableFuture.completedFuture(new RentResult.AlreadyOccupied(regionId));
            case RealtyBackend.RentResult.UpdateFailed ignored ->
                    CompletableFuture.completedFuture(new RentResult.UpdateFailed(regionId));
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new RentResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    private @NotNull CompletableFuture<RentResult> handleRentPayment(
            @NotNull WorldGuardRegion region, @NotNull String regionId,
            @NotNull UUID worldId, @NotNull UUID tenantId,
            @NotNull RealtyBackend.RentResult.Success preview) {
        double price = preview.price();
        OfflinePlayer tenant = Bukkit.getOfflinePlayer(tenantId);
        double balance = economy.getBalance(tenant);
        if (balance < price) {
            return CompletableFuture.completedFuture(
                    new RentResult.InsufficientFunds(price, balance));
        }
        if (price > 0) {
            EconomyResponse response = economy.withdrawPlayer(tenant, price);
            if (!response.transactionSuccess()) {
                return CompletableFuture.completedFuture(
                        new RentResult.PaymentFailed(response.errorMessage));
            }
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(preview.landlordId());
            economy.depositPlayer(landlord, price);
        }
        return CompletableFuture.supplyAsync(() -> {
            RealtyBackend.RentResult result = realtyApi.rentRegion(regionId, worldId, tenantId);
            if (result instanceof RealtyBackend.RentResult.Success) {
                return realtyApi.getRegionPlaceholders(regionId, worldId);
            }
            return null;
        }, executorState.dbExec()).thenApplyAsync(placeholders -> {
            if (placeholders == null) {
                if (price > 0) {
                    economy.depositPlayer(tenant, price);
                }
                return (RentResult) new RentResult.UpdateFailed(regionId);
            }
            ProtectedRegion protectedRegion = region.region();
            protectedRegion.getOwners().clear();
            protectedRegion.getMembers().clear();
            protectedRegion.getOwners().addPlayer(tenantId);
            regionProfileService.applyFlags(region, RegionState.LEASED, placeholders);
            signTextApplicator.updateLoadedSigns(region.world(), regionId,
                    RegionState.LEASED, placeholders);
            return (RentResult) new RentResult.Success(price, preview.durationSeconds(),
                    regionId, preview.landlordId());
        }, executorState.mainThreadExec());
    }

    @Override
    public @NotNull CompletableFuture<UnrentResult> unrent(@NotNull WorldGuardRegion region,
                                                            @NotNull UUID tenantId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getLeaseholdContract(regionId, worldId),
                executorState.dbExec()
        ).thenComposeAsync(lease -> {
            if (lease == null) {
                return CompletableFuture.completedFuture(
                        (UnrentResult) new UnrentResult.NoLeaseholdContract(regionId));
            }
            long totalSeconds = lease.durationSeconds();
            long remainingSeconds = lease.endDate() == null ? 0
                    : Math.max(0, Duration.between(LocalDateTime.now(), lease.endDate()).getSeconds());
            double refund = totalSeconds > 0 ? lease.price() * remainingSeconds / totalSeconds : 0;
            if (refund > 0) {
                OfflinePlayer landlord = Bukkit.getOfflinePlayer(lease.landlordId());
                EconomyResponse withdrawResponse = economy.withdrawPlayer(landlord, refund);
                if (!withdrawResponse.transactionSuccess()) {
                    return CompletableFuture.completedFuture(
                            (UnrentResult) new UnrentResult.RefundFailed(withdrawResponse.errorMessage));
                }
                OfflinePlayer tenantPlayer = Bukkit.getOfflinePlayer(tenantId);
                EconomyResponse depositResponse = economy.depositPlayer(tenantPlayer, refund);
                if (!depositResponse.transactionSuccess()) {
                    economy.depositPlayer(landlord, refund);
                    return CompletableFuture.completedFuture(
                            (UnrentResult) new UnrentResult.RefundFailed(depositResponse.errorMessage));
                }
            }
            return CompletableFuture.supplyAsync(() -> {
                RealtyBackend.UnrentResult result = realtyApi.unrentRegion(regionId, worldId, tenantId);
                if (result instanceof RealtyBackend.UnrentResult.Success) {
                    Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
                    return Map.entry(result, placeholders);
                }
                return Map.<RealtyBackend.UnrentResult, Map<String, String>>entry(result, Map.of());
            }, executorState.dbExec()).thenApplyAsync(entry -> {
                switch (entry.getKey()) {
                    case RealtyBackend.UnrentResult.Success ignored -> {
                        ProtectedRegion protectedRegion = region.region();
                        protectedRegion.getOwners().clear();
                        protectedRegion.getMembers().clear();
                        regionProfileService.applyFlags(region, RegionState.FOR_LEASE, entry.getValue());
                        signTextApplicator.updateLoadedSigns(region.world(), regionId,
                                RegionState.FOR_LEASE, entry.getValue());
                        return (UnrentResult) new UnrentResult.Success(refund, regionId, lease.landlordId());
                    }
                    default -> {
                        revertUnrentEconomy(tenantId, lease, refund);
                        return (UnrentResult) new UnrentResult.UpdateFailed(regionId);
                    }
                }
            }, executorState.mainThreadExec());
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new UnrentResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    private void revertUnrentEconomy(@NotNull UUID tenantId,
                                     @NotNull LeaseholdContractEntity lease,
                                     double refund) {
        if (refund > 0) {
            OfflinePlayer tenantPlayer = Bukkit.getOfflinePlayer(tenantId);
            economy.withdrawPlayer(tenantPlayer, refund);
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(lease.landlordId());
            economy.depositPlayer(landlord, refund);
        }
    }

    @Override
    public @NotNull CompletableFuture<ExtendResult> extend(@NotNull WorldGuardRegion region,
                                                            @NotNull UUID tenantId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(
                () -> realtyApi.previewRenewLeasehold(regionId, worldId),
                executorState.dbExec()
        ).thenComposeAsync(preview -> switch (preview) {
            case RealtyBackend.RenewLeaseholdResult.Success success ->
                    handleExtendPayment(region, regionId, worldId, tenantId, success);
            case RealtyBackend.RenewLeaseholdResult.NoLeaseholdContract ignored ->
                    CompletableFuture.completedFuture(new ExtendResult.NoLeaseholdContract(regionId));
            case RealtyBackend.RenewLeaseholdResult.NoExtensionsRemaining ignored ->
                    CompletableFuture.completedFuture(new ExtendResult.NoExtensionsRemaining(regionId));
            case RealtyBackend.RenewLeaseholdResult.UpdateFailed ignored ->
                    CompletableFuture.completedFuture(new ExtendResult.UpdateFailed(regionId));
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new ExtendResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    private @NotNull CompletableFuture<ExtendResult> handleExtendPayment(
            @NotNull WorldGuardRegion region, @NotNull String regionId,
            @NotNull UUID worldId, @NotNull UUID tenantId,
            @NotNull RealtyBackend.RenewLeaseholdResult.Success preview) {
        double price = preview.price();
        OfflinePlayer tenant = Bukkit.getOfflinePlayer(tenantId);
        double balance = economy.getBalance(tenant);
        if (balance < price) {
            return CompletableFuture.completedFuture(
                    new ExtendResult.InsufficientFunds(price, balance));
        }
        if (price > 0) {
            EconomyResponse response = economy.withdrawPlayer(tenant, price);
            if (!response.transactionSuccess()) {
                return CompletableFuture.completedFuture(
                        new ExtendResult.PaymentFailed(response.errorMessage));
            }
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(preview.landlordId());
            economy.depositPlayer(landlord, price);
        }
        return CompletableFuture.supplyAsync(() -> {
            RealtyBackend.RenewLeaseholdResult result = realtyApi.renewLeasehold(regionId, worldId, tenantId);
            if (result instanceof RealtyBackend.RenewLeaseholdResult.Success) {
                return realtyApi.getRegionPlaceholders(regionId, worldId);
            }
            return null;
        }, executorState.dbExec()).thenApplyAsync(placeholders -> {
            if (placeholders == null) {
                if (price > 0) {
                    economy.depositPlayer(tenant, price);
                }
                return (ExtendResult) new ExtendResult.UpdateFailed(regionId);
            }
            signTextApplicator.updateLoadedSigns(region.world(), regionId,
                    RegionState.LEASED, placeholders);
            return (ExtendResult) new ExtendResult.Success(price, regionId);
        }, executorState.mainThreadExec());
    }

    @Override
    public @NotNull CompletableFuture<PayBidResult> payBid(@NotNull WorldGuardRegion region,
                                                            @NotNull UUID bidderId,
                                                            double amount) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        OfflinePlayer bidder = Bukkit.getOfflinePlayer(bidderId);
        double balance = economy.getBalance(bidder);
        if (balance < amount) {
            return CompletableFuture.completedFuture(new PayBidResult.InsufficientFunds(balance));
        }
        EconomyResponse response = economy.withdrawPlayer(bidder, amount);
        if (!response.transactionSuccess()) {
            return CompletableFuture.completedFuture(
                    new PayBidResult.PaymentFailed(response.errorMessage));
        }
        return CompletableFuture.supplyAsync(
                () -> realtyApi.payBid(regionId, worldId, bidderId, amount),
                executorState.dbExec()
        ).thenApplyAsync(result -> switch (result) {
            case RealtyBackend.PayBidResult.Success success -> {
                UUID recipientId = success.titleHolderId() != null
                        ? success.titleHolderId() : success.authorityId();
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                economy.depositPlayer(recipient, amount);
                yield (PayBidResult) new PayBidResult.Success(amount, success.newTotal(),
                        success.remaining(), regionId);
            }
            case RealtyBackend.PayBidResult.FullyPaid fullyPaid -> {
                UUID recipientId = fullyPaid.titleHolderId() != null
                        ? fullyPaid.titleHolderId() : fullyPaid.authorityId();
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                economy.depositPlayer(recipient, amount);
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager == null) {
                    yield (PayBidResult) new PayBidResult.TransferFailed();
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(bidderId);
                protectedRegion.getMembers().clear();
                Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
                regionProfileService.applyFlags(region, RegionState.SOLD, placeholders);
                signTextApplicator.updateLoadedSigns(region.world(), regionId,
                        RegionState.SOLD, placeholders);
                updateChildLandlords(regionId, region.world(), bidderId);
                yield (PayBidResult) new PayBidResult.FullyPaid(amount, regionId,
                        fullyPaid.titleHolderId());
            }
            case RealtyBackend.PayBidResult.NoPaymentRecord ignored -> {
                economy.depositPlayer(bidder, amount);
                yield (PayBidResult) new PayBidResult.NoPaymentRecord(regionId);
            }
            case RealtyBackend.PayBidResult.PaymentExpired ignored -> {
                economy.depositPlayer(bidder, amount);
                yield (PayBidResult) new PayBidResult.PaymentExpired(regionId);
            }
            case RealtyBackend.PayBidResult.ExceedsAmountOwed exceeds -> {
                economy.depositPlayer(bidder, amount);
                yield (PayBidResult) new PayBidResult.ExceedsAmountOwed(amount,
                        exceeds.amountOwed(), regionId);
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            executorState.mainThreadExec().execute(() -> economy.depositPlayer(bidder, amount));
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new PayBidResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<PayOfferResult> payOffer(@NotNull WorldGuardRegion region,
                                                                @NotNull UUID offererId,
                                                                double amount) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        OfflinePlayer offerer = Bukkit.getOfflinePlayer(offererId);
        double balance = economy.getBalance(offerer);
        if (balance < amount) {
            return CompletableFuture.completedFuture(new PayOfferResult.InsufficientFunds(balance));
        }
        EconomyResponse response = economy.withdrawPlayer(offerer, amount);
        if (!response.transactionSuccess()) {
            return CompletableFuture.completedFuture(
                    new PayOfferResult.PaymentFailed(response.errorMessage));
        }
        return CompletableFuture.supplyAsync(
                () -> realtyApi.payOffer(regionId, worldId, offererId, amount),
                executorState.dbExec()
        ).thenApplyAsync(result -> switch (result) {
            case RealtyBackend.PayOfferResult.Success success -> {
                UUID recipientId = success.titleHolderId() != null
                        ? success.titleHolderId() : success.authorityId();
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                economy.depositPlayer(recipient, amount);
                yield (PayOfferResult) new PayOfferResult.Success(amount, success.newTotal(),
                        success.remaining(), regionId);
            }
            case RealtyBackend.PayOfferResult.FullyPaid fullyPaid -> {
                UUID recipientId = fullyPaid.titleHolderId() != null
                        ? fullyPaid.titleHolderId() : fullyPaid.authorityId();
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                economy.depositPlayer(recipient, amount);
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager == null) {
                    yield (PayOfferResult) new PayOfferResult.TransferFailed();
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(offererId);
                protectedRegion.getMembers().clear();
                Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
                regionProfileService.applyFlags(region, RegionState.SOLD, placeholders);
                signTextApplicator.updateLoadedSigns(region.world(), regionId,
                        RegionState.SOLD, placeholders);
                updateChildLandlords(regionId, region.world(), offererId);
                yield (PayOfferResult) new PayOfferResult.FullyPaid(amount, regionId,
                        fullyPaid.titleHolderId());
            }
            case RealtyBackend.PayOfferResult.NoPaymentRecord ignored -> {
                economy.depositPlayer(offerer, amount);
                yield (PayOfferResult) new PayOfferResult.NoPaymentRecord(regionId);
            }
            case RealtyBackend.PayOfferResult.ExceedsAmountOwed exceeds -> {
                economy.depositPlayer(offerer, amount);
                yield (PayOfferResult) new PayOfferResult.ExceedsAmountOwed(amount,
                        exceeds.amountOwed(), regionId);
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            executorState.mainThreadExec().execute(() -> economy.depositPlayer(offerer, amount));
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new PayOfferResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<SetTitleHolderResult> setTitleHolder(
            @NotNull WorldGuardRegion region, @Nullable UUID titleHolderId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            RealtyBackend.SetTitleHolderResult result = realtyApi.setTitleHolder(regionId, worldId, titleHolderId);
            if (result instanceof RealtyBackend.SetTitleHolderResult.Success) {
                Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
                return Map.entry(result, placeholders);
            }
            return Map.<RealtyBackend.SetTitleHolderResult, Map<String, String>>entry(result, Map.of());
        }, executorState.dbExec()).thenApplyAsync(entry -> switch (entry.getKey()) {
            case RealtyBackend.SetTitleHolderResult.Success success -> {
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getMembers().clear();
                RegionState state;
                if (titleHolderId != null) {
                    protectedRegion.getOwners().addPlayer(titleHolderId);
                    state = RegionState.SOLD;
                    updateChildLandlords(regionId, region.world(), titleHolderId);
                } else {
                    state = RegionState.FOR_SALE;
                }
                regionProfileService.applyFlags(region, state, entry.getValue());
                signTextApplicator.updateLoadedSigns(region.world(), regionId,
                        state, entry.getValue());
                yield (SetTitleHolderResult) new SetTitleHolderResult.Success(
                        success.previousTitleHolder(), regionId);
            }
            case RealtyBackend.SetTitleHolderResult.NoFreeholdContract ignored ->
                    (SetTitleHolderResult) new SetTitleHolderResult.NoFreeholdContract(regionId);
            case RealtyBackend.SetTitleHolderResult.UpdateFailed ignored ->
                    (SetTitleHolderResult) new SetTitleHolderResult.UpdateFailed(regionId);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new SetTitleHolderResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<SetTenantResult> setTenant(
            @NotNull WorldGuardRegion region, @Nullable UUID tenantId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            RealtyBackend.SetTenantResult result = realtyApi.setTenant(regionId, worldId, tenantId);
            if (result instanceof RealtyBackend.SetTenantResult.Success) {
                Map<String, String> placeholders = realtyApi.getRegionPlaceholders(regionId, worldId);
                return Map.entry(result, placeholders);
            }
            return Map.<RealtyBackend.SetTenantResult, Map<String, String>>entry(result, Map.of());
        }, executorState.dbExec()).thenApplyAsync(entry -> switch (entry.getKey()) {
            case RealtyBackend.SetTenantResult.Success success -> {
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getMembers().clear();
                RegionState state;
                if (tenantId != null) {
                    protectedRegion.getOwners().addPlayer(tenantId);
                    state = RegionState.LEASED;
                } else {
                    state = RegionState.FOR_LEASE;
                }
                regionProfileService.applyFlags(region, state, entry.getValue());
                signTextApplicator.updateLoadedSigns(region.world(), regionId,
                        state, entry.getValue());
                yield (SetTenantResult) new SetTenantResult.Success(
                        success.previousTenant(), success.landlordId(), regionId);
            }
            case RealtyBackend.SetTenantResult.NoLeaseholdContract ignored ->
                    (SetTenantResult) new SetTenantResult.NoLeaseholdContract(regionId);
            case RealtyBackend.SetTenantResult.UpdateFailed ignored ->
                    (SetTenantResult) new SetTenantResult.UpdateFailed(regionId);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new SetTenantResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<SetLandlordResult> setLandlord(
            @NotNull WorldGuardRegion region, @NotNull UUID landlordId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(
                () -> realtyApi.setLandlord(regionId, worldId, landlordId),
                executorState.dbExec()
        ).thenApplyAsync(result -> switch (result) {
            case RealtyBackend.SetLandlordResult.Success success -> {
                region.region().getMembers().clear();
                yield (SetLandlordResult) new SetLandlordResult.Success(
                        success.previousLandlord(), regionId);
            }
            case RealtyBackend.SetLandlordResult.NoLeaseholdContract ignored ->
                    (SetLandlordResult) new SetLandlordResult.NoLeaseholdContract(regionId);
            case RealtyBackend.SetLandlordResult.UpdateFailed ignored ->
                    (SetLandlordResult) new SetLandlordResult.UpdateFailed(regionId);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new SetLandlordResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<DeleteResult> deleteRegion(
            @NotNull WorldGuardRegion region, boolean includeWorldGuard) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            int deleted = realtyApi.deleteRegion(regionId, worldId);
            if (deleted == 0) {
                return (DeleteResult) new DeleteResult.NotRegistered();
            }
            return (DeleteResult) new DeleteResult.Success();
        }, executorState.dbExec()).thenComposeAsync(result -> {
            if (!(result instanceof DeleteResult.Success)) {
                return CompletableFuture.completedFuture(result);
            }
            if (includeWorldGuard) {
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager != null) {
                    regionManager.removeRegion(regionId);
                    try {
                        regionManager.save();
                    } catch (StorageException ex) {
                        ex.printStackTrace();
                        return CompletableFuture.completedFuture(
                                (DeleteResult) new DeleteResult.WorldGuardSaveError(ex.getMessage()));
                    }
                }
            }
            regionProfileService.clearAllFlags(region);
            return CompletableFuture.completedFuture(result);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new DeleteResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<CreateFreeholdResult> createFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull UUID authority,
            @Nullable UUID titleHolder) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            boolean created = realtyApi.createFreehold(regionId, worldId, price, authority, titleHolder);
            Map<String, String> placeholders = created
                    ? realtyApi.getRegionPlaceholders(regionId, worldId)
                    : Map.<String, String>of();
            return Map.entry(created, placeholders);
        }, executorState.dbExec()).thenApplyAsync(entry -> {
            if (entry.getKey()) {
                region.region().getMembers().addPlayer(authority);
                regionProfileService.applyFlags(region,
                        titleHolder != null ? RegionState.SOLD : RegionState.FOR_SALE,
                        entry.getValue());
                return (CreateFreeholdResult) new CreateFreeholdResult.Success(regionId);
            }
            return (CreateFreeholdResult) new CreateFreeholdResult.AlreadyRegistered(regionId);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new CreateFreeholdResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<CreateFreeholdResult> registerFreehold(
            @NotNull WorldGuardRegion region,
            @Nullable Double price,
            @NotNull UUID authority,
            @Nullable UUID titleHolder) {
        return createFreehold(region, price, authority, titleHolder);
    }

    @Override
    public @NotNull CompletableFuture<CreateLeaseholdResult> createLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull UUID landlordId) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            boolean created = realtyApi.createLeasehold(regionId, worldId,
                    price, durationSeconds, maxRenewals, landlordId);
            Map<String, String> placeholders = created
                    ? realtyApi.getRegionPlaceholders(regionId, worldId)
                    : Map.<String, String>of();
            return Map.entry(created, placeholders);
        }, executorState.dbExec()).thenApplyAsync(entry -> {
            if (entry.getKey()) {
                regionProfileService.applyFlags(region, RegionState.FOR_LEASE, entry.getValue());
                return (CreateLeaseholdResult) new CreateLeaseholdResult.Success(regionId);
            }
            return (CreateLeaseholdResult) new CreateLeaseholdResult.AlreadyRegistered(regionId);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new CreateLeaseholdResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<CreateLeaseholdResult> registerLeasehold(
            @NotNull WorldGuardRegion region,
            double price, long durationSeconds,
            int maxRenewals, @NotNull UUID landlordId) {
        return createLeasehold(region, price, durationSeconds, maxRenewals, landlordId);
    }

    @Override
    public @NotNull CompletableFuture<QuickCreateSubregionResult> quickCreateSubregion(
            @NotNull WorldGuardRegion parentRegion,
            @NotNull String childName,
            @NotNull Region selection,
            double price, long durationSeconds,
            @NotNull UUID landlordId) {
        String parentId = parentRegion.region().getId();
        UUID worldId = parentRegion.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            FreeholdContractEntity freehold = realtyApi.getFreeholdContract(parentId, worldId);
            if (freehold == null) {
                return (QuickCreateSubregionResult) new QuickCreateSubregionResult.NoFreeholdContract(parentId);
            }
            boolean created = realtyApi.createLeasehold(
                    childName, worldId, price, durationSeconds, -1, landlordId);
            if (!created) {
                return (QuickCreateSubregionResult) new QuickCreateSubregionResult.RegionExists(childName);
            }
            Map<String, String> placeholders = realtyApi.getRegionPlaceholders(childName, worldId);
            return (QuickCreateSubregionResult) new QuickCreateSubregionResult.Success(childName, parentId);
        }, executorState.dbExec()).thenComposeAsync(result -> {
            if (result instanceof QuickCreateSubregionResult.Success) {
                Map<String, String> placeholders = realtyApi.getRegionPlaceholders(childName, worldId);
                ProtectedRegion childRegion = createProtectedRegion(childName, selection);
                try {
                    childRegion.setParent(parentRegion.region());
                } catch (ProtectedRegion.CircularInheritanceException ex) {
                    return CompletableFuture.completedFuture(
                            (QuickCreateSubregionResult) new QuickCreateSubregionResult.Error(
                                    "Circular region inheritance"));
                }
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(parentRegion.world()));
                if (regionManager != null) {
                    regionManager.addRegion(childRegion);
                }
                childRegion.getOwners().addPlayer(landlordId);
                WorldGuardRegion childWgRegion = new WorldGuardRegion(childRegion, parentRegion.world());
                regionProfileService.applyFlags(childWgRegion, RegionState.FOR_LEASE, placeholders);
            }
            return CompletableFuture.completedFuture(result);
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new QuickCreateSubregionResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    // --- Sign Operations ---

    @Override
    public @NotNull CompletableFuture<PlaceSignResult> placeSign(
            @NotNull WorldGuardRegion region,
            @NotNull UUID signWorldId, int blockX, int blockY, int blockZ) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        return CompletableFuture.supplyAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RealtySignEntity existing = session.realtySignMapper()
                        .selectByPosition(signWorldId, blockX, blockY, blockZ);
                if (existing == null) {
                    int rows = session.realtySignMapper()
                            .insert(signWorldId, blockX, blockY, blockZ, regionId, worldId);
                    if (rows == 0) {
                        return (PlaceSignResult) new PlaceSignResult.NotRegistered(regionId);
                    }
                    RealtyRegionEntity regionEntity = session.realtyRegionMapper()
                            .selectByWorldGuardRegion(regionId, worldId);
                    if (regionEntity != null) {
                        signCache.put(signWorldId, blockX, blockY, blockZ,
                                regionEntity.realtyRegionId(), regionId, worldId);
                    }
                }
                RealtyBackend.RegionWithState rws = realtyApi.getRegionWithState(regionId, worldId);
                if (rws != null) {
                    executorState.mainThreadExec().execute(() ->
                            signTextApplicator.applySignText(
                                    Bukkit.getWorld(signWorldId),
                                    blockX, blockY, blockZ,
                                    regionId, rws.state(), rws.placeholders()));
                }
                return (PlaceSignResult) new PlaceSignResult.Success(regionId);
            }
        }, executorState.dbExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new PlaceSignResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<RemoveSignResult> removeSign(
            @NotNull UUID signWorldId, int blockX, int blockY, int blockZ) {
        return CompletableFuture.supplyAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                int rows = session.realtySignMapper()
                        .deleteByPosition(signWorldId, blockX, blockY, blockZ);
                if (rows == 0) {
                    return (RemoveSignResult) new RemoveSignResult.NotRegistered();
                }
                signCache.remove(signWorldId, blockX, blockY, blockZ);
                return (RemoveSignResult) new RemoveSignResult.Success();
            }
        }, executorState.dbExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            return new RemoveSignResult.Error(String.valueOf(cause.getMessage()));
        });
    }

    @Override
    public @NotNull CompletableFuture<List<RealtySignEntity>> listSigns(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                return session.realtySignMapper().selectByRegion(regionId, worldId);
            }
        }, executorState.dbExec());
    }

    // ═══════════════════════════════════════
    // SIMPLE PROXY OPERATIONS
    // ═══════════════════════════════════════

    @Override
    public @NotNull CompletableFuture<RealtyBackend.InviteAgentResult> inviteAgent(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID inviterId, @NotNull UUID inviteeId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.inviteAgent(regionId, worldId, inviterId, inviteeId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.AcceptAgentInviteResult> acceptAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.acceptAgentInvite(regionId, worldId, inviteeId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.WithdrawAgentInviteResult> withdrawAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.withdrawAgentInvite(regionId, worldId, inviteeId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.RejectAgentInviteResult> rejectAgentInvite(
            @NotNull String regionId, @NotNull UUID worldId, @NotNull UUID inviteeId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.rejectAgentInvite(regionId, worldId, inviteeId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<Integer> removeSanctionedAuctioneer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID auctioneerId, @NotNull UUID actorId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.removeSanctionedAuctioneer(regionId, worldId, auctioneerId, actorId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.CreateAuctionResult> createAuction(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID auctioneerId, long biddingDurationSeconds,
            long paymentDurationSeconds, double minBid, double minBidStep) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.createAuction(regionId, worldId, auctioneerId,
                        biddingDurationSeconds, paymentDurationSeconds, minBid, minBidStep),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.CancelAuctionResult> cancelAuction(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.cancelAuction(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.BidResult> performBid(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID bidderId, double bidAmount) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.performBid(regionId, worldId, bidderId, bidAmount),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.OfferResult> placeOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID offererId, double price) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.placeOffer(regionId, worldId, offererId, price),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.AcceptOfferResult> acceptOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, @NotNull UUID offererId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.acceptOffer(regionId, worldId, callerId, offererId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.WithdrawOfferResult> withdrawOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID offererId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.withdrawOffer(regionId, worldId, offererId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.RejectOfferResult> rejectOffer(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, @NotNull UUID offererId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.rejectOffer(regionId, worldId, callerId, offererId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.RejectAllOffersResult> rejectAllOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.rejectAllOffers(regionId, worldId, callerId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.ToggleOffersResult> toggleOffers(
            @NotNull String regionId, @NotNull UUID worldId,
            @NotNull UUID callerId, boolean accepting, boolean bypassAuth) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.toggleOffers(regionId, worldId, callerId, accepting, bypassAuth),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<List<OutboundOfferView>> listOutboundOffers(
            @NotNull UUID offererId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.listOutboundOffers(offererId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<List<InboundOfferView>> listInboundOffers(
            @NotNull UUID titleHolderId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.listInboundOffers(titleHolderId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.SetPriceResult> setPrice(
            @NotNull String regionId, @NotNull UUID worldId, double price) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.setPrice(regionId, worldId, price),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.UnsetPriceResult> unsetPrice(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.unsetPrice(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.SetDurationResult> setDuration(
            @NotNull String regionId, @NotNull UUID worldId, long durationSeconds) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.setDuration(regionId, worldId, durationSeconds),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.SetMaxRenewalsResult> setMaxRenewals(
            @NotNull String regionId, @NotNull UUID worldId, int maxRenewals) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.setMaxRenewals(regionId, worldId, maxRenewals),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.RegionInfo> getRegionInfo(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getRegionInfo(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<@Nullable FreeholdContractEntity> getFreeholdContract(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getFreeholdContract(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<@Nullable LeaseholdContractEntity> getLeaseholdContract(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getLeaseholdContract(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.ListResult> listRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.listRegions(targetId, limit, offset),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listOwnedRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.listOwnedRegions(targetId, limit, offset),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.SingleCategoryResult> listRentedRegions(
            @NotNull UUID targetId, int limit, int offset) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.listRentedRegions(targetId, limit, offset),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.HistoryResult> searchHistory(
            @NotNull String regionId, @NotNull UUID worldId,
            @Nullable String eventType, @Nullable LocalDateTime since,
            @Nullable UUID playerId, int limit, int offset) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.searchHistory(regionId, worldId, eventType, since, playerId, limit, offset),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<RealtyBackend.RegionWithState> getRegionWithState(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getRegionWithState(regionId, worldId),
                executorState.dbExec());
    }

    @Override
    public @NotNull CompletableFuture<@NotNull Map<String, String>> getRegionPlaceholders(
            @NotNull String regionId, @NotNull UUID worldId) {
        return CompletableFuture.supplyAsync(
                () -> realtyApi.getRegionPlaceholders(regionId, worldId),
                executorState.dbExec());
    }

    // ═══════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════

    private void updateChildLandlords(@NotNull String parentRegionId,
                                      @NotNull World world,
                                      @NotNull UUID newLandlord) {
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return;
        }
        ProtectedRegion parent = regionManager.getRegion(parentRegionId);
        if (parent == null) {
            return;
        }
        List<String> childIds = new ArrayList<>();
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            if (parent.equals(region.getParent())) {
                childIds.add(region.getId());
            }
        }
        if (childIds.isEmpty()) {
            return;
        }
        UUID worldId = world.getUID();
        CompletableFuture.runAsync(
                () -> realtyApi.updateSubregionLandlords(childIds, worldId, newLandlord),
                executorState.dbExec());
    }

    private static @NotNull ProtectedRegion createProtectedRegion(@NotNull String name,
                                                                    @NotNull Region selection) {
        if (selection instanceof CuboidRegion cuboid) {
            return new ProtectedCuboidRegion(name,
                    cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
        } else if (selection instanceof Polygonal2DRegion polygon) {
            return new ProtectedPolygonalRegion(name,
                    polygon.getPoints(), polygon.getMinimumY(), polygon.getMaximumY());
        }
        return new ProtectedCuboidRegion(name,
                selection.getMinimumPoint(), selection.getMaximumPoint());
    }
}
