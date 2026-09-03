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
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

        RegionResponse response = new RegionResponse(
                regionParam,
                worldRef,
                state == null ? null : state.name(),
                toFreehold(info.freehold(), info.lastSoldPrice()),
                toLeasehold(info.leasehold()),
                toAuction(info.auction(), info.highestBid()),
                null,
                tags);

        ctx.json(response);
    }

    private static @Nullable RegionResponse.Freehold toFreehold(@Nullable FreeholdContractEntity freehold,
                                                                @Nullable Double lastSoldPrice) {
        if (freehold == null) {
            return null;
        }
        PlayerRef titleHolder = freehold.titleHolderId() == null
                ? null
                : new PlayerRef(freehold.titleHolderId().toString(), null);
        PlayerRef authority = new PlayerRef(freehold.authorityId().toString(), null);
        return new RegionResponse.Freehold(titleHolder, authority, freehold.price(), lastSoldPrice);
    }

    private static @Nullable RegionResponse.Leasehold toLeasehold(@Nullable LeaseholdContractEntity leasehold) {
        if (leasehold == null) {
            return null;
        }
        PlayerRef landlord = new PlayerRef(leasehold.landlordId().toString(), null);
        PlayerRef tenant = leasehold.tenantId() == null
                ? null
                : new PlayerRef(leasehold.tenantId().toString(), null);
        return new RegionResponse.Leasehold(
                landlord,
                tenant,
                leasehold.price(),
                leasehold.durationSeconds(),
                formatOrNull(leasehold.startDate()),
                formatOrNull(leasehold.endDate()),
                leasehold.currentMaxExtensions(),
                leasehold.maxExtensions());
    }

    private static @Nullable RegionResponse.Auction toAuction(@Nullable FreeholdContractAuctionEntity auction,
                                                               @Nullable FreeholdContractBid highestBid) {
        if (auction == null) {
            return null;
        }
        LocalDateTime lastActivity = highestBid != null ? highestBid.bidTime() : auction.startDate();
        LocalDateTime endDate = lastActivity.plusSeconds(auction.biddingDurationSeconds());
        RegionResponse.Bid bid = highestBid == null
                ? null
                : new RegionResponse.Bid(new PlayerRef(highestBid.bidderId().toString(), null), highestBid.bidAmount());
        return new RegionResponse.Auction(IsoDates.format(endDate), bid);
    }

    private static @Nullable String formatOrNull(@Nullable LocalDateTime dateTime) {
        return dateTime == null ? null : IsoDates.format(dateTime);
    }

}
