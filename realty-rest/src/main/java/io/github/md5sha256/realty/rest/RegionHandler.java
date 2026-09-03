package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code GET /v1/region?world=...&region=...} -- the HTTP form of {@code /realty info}.
 */
final class RegionHandler {

    private final RealtyBackend backend;
    private final Database database;
    private final WorldLookup worldLookup;
    private final ModuleClient moduleClient;

    RegionHandler(@NotNull RealtyBackend backend, @NotNull Database database, @NotNull WorldLookup worldLookup,
                 @NotNull ModuleClient moduleClient) {
        this.backend = backend;
        this.database = database;
        this.worldLookup = worldLookup;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        String worldParam = QueryParams.required(ctx, "world");
        String regionParam = QueryParams.required(ctx, "region");

        UUID worldId = this.worldLookup.resolve(worldParam);

        RealtyBackend.RegionInfo info = this.backend.getRegionInfo(regionParam, worldId);
        RegionState state = this.backend.getRegionState(regionParam, worldId);

        if (state == null && info.freehold() == null && info.leasehold() == null && info.auction() == null) {
            throw ApiException.notFound("REGION_NOT_FOUND",
                    "No region '" + regionParam + "' in world '" + worldParam + "'");
        }

        WorldRef worldRef = this.worldLookup.refFor(worldId);
        List<String> tags;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            tags = session.regionTagMapper().selectTagIdsByRegionId(regionParam);
        }

        List<UUID> playerIds = new ArrayList<>();
        if (info.freehold() != null) {
            playerIds.add(info.freehold().titleHolderId());
            playerIds.add(info.freehold().authorityId());
        }
        if (info.leasehold() != null) {
            playerIds.add(info.leasehold().landlordId());
            playerIds.add(info.leasehold().tenantId());
        }
        if (info.auction() != null) {
            playerIds.add(info.auction().auctioneerId());
        }
        if (info.highestBid() != null) {
            playerIds.add(info.highestBid().bidderId());
        }
        // Both module calls are independent, and each carries the same timeout budget.
        // Run them concurrently so a wedged module costs one timeout, not two.
        CompletableFuture<Map<UUID, String>> pendingNames =
                CompletableFuture.supplyAsync(() -> PlayerNames.resolve(this.moduleClient, playerIds));
        RegionResponse.Dimensions dimensions = this.moduleClient.dimensions(worldId, regionParam).orElse(null);
        Map<UUID, String> names = pendingNames.join();

        RegionResponse response = new RegionResponse(
                regionParam,
                worldRef,
                state == null ? null : state.name(),
                toFreehold(info.freehold(), info.lastSoldPrice(), names),
                toLeasehold(info.leasehold(), names),
                toAuction(info.auction(), info.highestBid(), names),
                dimensions,
                tags);

        ctx.json(response);
    }

    private static @Nullable RegionResponse.Freehold toFreehold(@Nullable FreeholdContractEntity freehold,
                                                                @Nullable Double lastSoldPrice,
                                                                @NotNull Map<UUID, String> names) {
        if (freehold == null) {
            return null;
        }
        PlayerRef titleHolder = PlayerNames.ref(freehold.titleHolderId(), names);
        PlayerRef authority = Objects.requireNonNull(PlayerNames.ref(freehold.authorityId(), names));
        return new RegionResponse.Freehold(titleHolder, authority, freehold.price(), lastSoldPrice,
                freehold.acceptingOffers());
    }

    private static @Nullable RegionResponse.Leasehold toLeasehold(@Nullable LeaseholdContractEntity leasehold,
                                                                   @NotNull Map<UUID, String> names) {
        if (leasehold == null) {
            return null;
        }
        PlayerRef landlord = Objects.requireNonNull(PlayerNames.ref(leasehold.landlordId(), names));
        PlayerRef tenant = PlayerNames.ref(leasehold.tenantId(), names);
        return new RegionResponse.Leasehold(
                landlord,
                tenant,
                leasehold.price(),
                leasehold.durationSeconds(),
                formatOrNull(leasehold.startDate()),
                formatOrNull(leasehold.endDate()),
                leasehold.currentMaxExtensions(),
                leasehold.maxExtensions(),
                formatOrNull(leasehold.terminationEffectiveDate()),
                leasehold.terminatedByRole(),
                leasehold.acceptingTenants());
    }

    private static @Nullable RegionResponse.Auction toAuction(@Nullable FreeholdContractAuctionEntity auction,
                                                               @Nullable FreeholdContractBid highestBid,
                                                               @NotNull Map<UUID, String> names) {
        if (auction == null) {
            return null;
        }
        LocalDateTime lastActivity = highestBid != null ? highestBid.bidTime() : auction.startDate();
        LocalDateTime endDate = lastActivity.plusSeconds(auction.biddingDurationSeconds());
        RegionResponse.Bid bid = highestBid == null
                ? null
                : new RegionResponse.Bid(Objects.requireNonNull(PlayerNames.ref(highestBid.bidderId(), names)), highestBid.bidAmount());
        return new RegionResponse.Auction(
                IsoDates.format(endDate),
                bid,
                Objects.requireNonNull(PlayerNames.ref(auction.auctioneerId(), names)),
                IsoDates.format(auction.startDate()),
                auction.minBid(),
                auction.minStep(),
                auction.biddingDurationSeconds(),
                auction.paymentDurationSeconds());
    }

    private static @Nullable String formatOrNull(@Nullable LocalDateTime dateTime) {
        return dateTime == null ? null : IsoDates.format(dateTime);
    }

}
