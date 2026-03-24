package io.github.md5sha256.realty.importer;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseholdContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.RealtySignMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import io.github.md5sha256.realty.settings.Settings;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.features.signs.RegionSign;
import me.wiefferink.areashop.features.signs.SignsFeature;
import me.wiefferink.areashop.managers.IFileManager;
import me.wiefferink.areashop.regions.GeneralRegion;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class ImportJob {

    private static final int COMMIT_INTERVAL = 1000;

    private static @NotNull ImportResult importAll(
            @NotNull Database database,
            @NotNull Audience audience,
            @NotNull List<FreeholdDto> freeholds,
            @NotNull List<LeaseholdDto> leaseholds,
            @NotNull List<SignDto> signs) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int processed = 0;
        int signsImported = 0;
        int total = freeholds.size() + leaseholds.size();
        try (SqlSessionWrapper wrapper = database.openSession(ExecutorType.REUSE, false);
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            LeaseholdContractMapper leaseholdMapper = wrapper.leaseholdContractMapper();
            ContractMapper contractMapper = wrapper.contractMapper();
            FreeholdHistoryMapper freeholdHistoryMapper = wrapper.freeholdHistoryMapper();
            RealtySignMapper signMapper = wrapper.realtySignMapper();
            for (FreeholdDto freehold : freeholds) {
                try {
                    if (regionMapper.selectByWorldGuardRegion(freehold.worldGuardRegionId(),
                            freehold.worldId()) != null) {
                        skipped++;
                    } else {
                        int regionId = regionMapper.registerWorldGuardRegion(freehold.worldGuardRegionId(),
                                freehold.worldId());
                        int freeholdContractId = freeholdMapper.insertFreehold(regionId,
                                freehold.price(),
                                freehold.authority(),
                                freehold.titleHolder());
                        contractMapper.insert(new ContractEntity(freeholdContractId, "freehold", regionId));
                        if (freehold.lastSoldPrice() != null && freehold.titleHolder() != null) {
                            freeholdHistoryMapper.insert(freehold.worldGuardRegionId(),
                                    freehold.worldId(),
                                    HistoryEventType.BUY.name(),
                                    freehold.titleHolder(),
                                    freehold.authority(),
                                    freehold.lastSoldPrice());
                        }
                        imported++;
                    }
                } catch (Exception ex) {
                    failed++;
                    audience.sendMessage(Component.text(
                            "Failed to import freehold region " + freehold.worldGuardRegionId() + ": " + ex.getMessage(),
                            NamedTextColor.RED));
                }
                processed++;
                if (processed % COMMIT_INTERVAL == 0) {
                    session.commit();
                    reportProgress(audience, processed, total);
                }
            }
            for (LeaseholdDto lease : leaseholds) {
                try {
                    if (regionMapper.selectByWorldGuardRegion(lease.worldGuardRegionId(),
                            lease.worldId()) != null) {
                        skipped++;
                    } else {
                        int regionId = regionMapper.registerWorldGuardRegion(lease.worldGuardRegionId(),
                                lease.worldId());
                        int leaseholdContractId = leaseholdMapper.insertLeasehold(regionId,
                                lease.price(),
                                lease.durationSeconds(),
                                lease.maxRenewals(),
                                lease.landlordId(),
                                lease.tenantId());
                        contractMapper.insert(new ContractEntity(leaseholdContractId,
                                "leasehold",
                                regionId));
                        imported++;
                    }
                } catch (Exception ex) {
                    failed++;
                    audience.sendMessage(Component.text(
                            "Failed to import leasehold region " + lease.worldGuardRegionId() + ": " + ex.getMessage(),
                            NamedTextColor.RED));
                }
                processed++;
                if (processed % COMMIT_INTERVAL == 0) {
                    session.commit();
                    reportProgress(audience, processed, total);
                }
            }
            // Import signs
            for (SignDto sign : signs) {
                try {
                    if (regionMapper.selectByWorldGuardRegion(sign.worldGuardRegionId(),
                            sign.regionWorldId()) == null) {
                        continue;
                    }
                    int rows = signMapper.insert(sign.signWorldId(),
                            sign.blockX(), sign.blockY(), sign.blockZ(),
                            sign.worldGuardRegionId(), sign.regionWorldId());
                    if (rows > 0) {
                        signsImported++;
                    }
                } catch (Exception ex) {
                    audience.sendMessage(Component.text(
                            "Failed to import sign at " + sign.blockX() + "," + sign.blockY() + ","
                                    + sign.blockZ() + " for region " + sign.worldGuardRegionId()
                                    + ": " + ex.getMessage(),
                            NamedTextColor.RED));
                }
            }
            session.commit();
            if (signsImported > 0) {
                audience.sendMessage(Component.text(
                        "Imported " + signsImported + " sign(s)", NamedTextColor.GREEN));
            }
        }
        return new ImportResult(imported, skipped, failed);
    }

    @NotNull
    public static CompletableFuture<ImportResult> performImport(@NotNull Database database,
                                                                @NotNull Settings settings,
                                                                @NotNull Executor executor,
                                                                @NotNull Audience audience) {
        IFileManager fileManager = AreaShop.getInstance().getFileManager();
        List<FreeholdDto> buyRegions = fileManager.getBuysRef().stream()
                .map(region -> {
                    ProtectedRegion protectedRegion = region.getRegion();
                    World world = region.getWorld();
                    if (protectedRegion == null || world == null) {
                        audience.sendMessage(Component.text("Skipping invalid buy region " + region.getName()));
                        return null;
                    }
                    UUID landlord = Objects.requireNonNullElse(region.getLandlord(), settings.defaultFreeholdAuthority());
                    UUID owner = region.getOwner();
                    boolean forFreehold = region.getState() == GeneralRegion.RegionState.FORSALE;
                    Double price = forFreehold ? region.getPrice() : null;
                    Double lastSoldPrice = !forFreehold ? region.getPrice() : null;
                    return new FreeholdDto(protectedRegion.getId(),
                            world.getUID(),
                            price,
                            lastSoldPrice,
                            landlord,
                            owner != null ? owner : settings.defaultFreeholdTitleholder());
                })
                .filter(Objects::nonNull)
                .toList();
        List<LeaseholdDto> rentRegions = fileManager.getRentsRef().stream()
                .map(region -> {
                    ProtectedRegion protectedRegion = region.getRegion();
                    World world = region.getWorld();
                    if (protectedRegion == null || world == null) {
                        audience.sendMessage(Component.text("Skipping invalid rent region " + region.getName()));
                        return null;
                    }

                    UUID authorityId = Objects.requireNonNullElse(region.getLandlord(), settings.defaultLeaseholdAuthority());
                    UUID tenantId = region.getRenter();
                    return new LeaseholdDto(protectedRegion.getId(),
                            world.getUID(),
                            region.getPrice(),
                            TimeUnit.MILLISECONDS.toSeconds(region.getDuration()),
                            region.getMaxExtends(),
                            region.getTimesExtended(),
                            authorityId,
                            tenantId
                    );
                }).filter(Objects::nonNull)
                .toList();
        List<SignDto> signs = new ArrayList<>();
        collectSigns(fileManager.getBuysRef(), signs, audience);
        collectSigns(fileManager.getRentsRef(), signs, audience);
        int totalRegions = buyRegions.size() + rentRegions.size();
        audience.sendMessage(Component.text(
                "Starting import: " + buyRegions.size() + " freehold regions, "
                        + rentRegions.size() + " leasehold regions (" + totalRegions + " total), "
                        + signs.size() + " sign(s)",
                NamedTextColor.YELLOW));
        return CompletableFuture.supplyAsync(() -> importAll(database, audience, buyRegions, rentRegions, signs), executor);
    }

    private static void reportProgress(@NotNull Audience audience, int processed, int total) {
        audience.sendMessage(Component.text(
                "Import progress: " + processed + "/" + total + " regions processed",
                NamedTextColor.GRAY));
    }

    public record ImportResult(int imported, int skipped, int failed) {
    }

    private record FreeholdDto(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           @Nullable Double price,
                           @Nullable Double lastSoldPrice,
                           @NotNull UUID authority,
                           @Nullable UUID titleHolder) {
    }

    private record LeaseholdDto(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price,
                            long durationSeconds,
                            int maxRenewals,
                            int currentRenewals,
                            @NotNull UUID landlordId,
                            @Nullable UUID tenantId) {
    }

    private record SignDto(@NotNull UUID signWorldId,
                            int blockX,
                            int blockY,
                            int blockZ,
                            @NotNull String worldGuardRegionId,
                            @NotNull UUID regionWorldId) {
    }

    private static void collectSigns(@NotNull Collection<? extends GeneralRegion> regions,
                                      @NotNull List<SignDto> signs,
                                      @NotNull Audience audience) {
        for (GeneralRegion region : regions) {
            ProtectedRegion protectedRegion = region.getRegion();
            World world = region.getWorld();
            if (protectedRegion == null || world == null) {
                continue;
            }
            if (!SignsFeature.exists(region)) {
                continue;
            }
            Collection<RegionSign> regionSigns = region.getSignsFeature().signManager().allSigns();
            for (RegionSign regionSign : regionSigns) {
                Location loc = regionSign.getLocation();
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                int blockX = loc.getBlockX();
                int blockY = loc.getBlockY();
                int blockZ = loc.getBlockZ();
                signs.add(new SignDto(
                        loc.getWorld().getUID(),
                        blockX, blockY, blockZ,
                        protectedRegion.getId(),
                        world.getUID()));
            }
        }
    }

}
