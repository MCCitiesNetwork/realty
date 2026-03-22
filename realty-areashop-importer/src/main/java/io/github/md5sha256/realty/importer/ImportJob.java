package io.github.md5sha256.realty.importer;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import io.github.md5sha256.realty.settings.Settings;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.managers.IFileManager;
import me.wiefferink.areashop.regions.GeneralRegion;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            @NotNull List<LeaseDto> leases) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int processed = 0;
        int total = freeholds.size() + leases.size();
        try (SqlSessionWrapper wrapper = database.openSession(ExecutorType.REUSE, false);
             SqlSession session = wrapper.session()) {
            RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
            FreeholdContractMapper freeholdMapper = wrapper.freeholdContractMapper();
            LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
            ContractMapper contractMapper = wrapper.contractMapper();
            FreeholdHistoryMapper freeholdHistoryMapper = wrapper.freeholdHistoryMapper();
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
            for (LeaseDto lease : leases) {
                try {
                    if (regionMapper.selectByWorldGuardRegion(lease.worldGuardRegionId(),
                            lease.worldId()) != null) {
                        skipped++;
                    } else {
                        int regionId = regionMapper.registerWorldGuardRegion(lease.worldGuardRegionId(),
                                lease.worldId());
                        int leaseContractId = leaseMapper.insertLease(regionId,
                                lease.price(),
                                lease.durationSeconds(),
                                lease.maxRenewals(),
                                lease.landlordId(),
                                lease.tenantId());
                        contractMapper.insert(new ContractEntity(leaseContractId,
                                "contract",
                                regionId));
                        imported++;
                    }
                } catch (Exception ex) {
                    failed++;
                    audience.sendMessage(Component.text(
                            "Failed to import lease region " + lease.worldGuardRegionId() + ": " + ex.getMessage(),
                            NamedTextColor.RED));
                }
                processed++;
                if (processed % COMMIT_INTERVAL == 0) {
                    session.commit();
                    reportProgress(audience, processed, total);
                }
            }
            session.commit();
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
        List<LeaseDto> rentRegions = fileManager.getRentsRef().stream()
                .map(region -> {
                    ProtectedRegion protectedRegion = region.getRegion();
                    World world = region.getWorld();
                    if (protectedRegion == null || world == null) {
                        audience.sendMessage(Component.text("Skipping invalid rent region " + region.getName()));
                        return null;
                    }

                    UUID authorityId = Objects.requireNonNullElse(region.getLandlord(), settings.defaultLeaseAuthority());
                    UUID tenantId = region.getRenter();
                    return new LeaseDto(protectedRegion.getId(),
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
        int totalRegions = buyRegions.size() + rentRegions.size();
        audience.sendMessage(Component.text(
                "Starting import: " + buyRegions.size() + " freehold regions, "
                        + rentRegions.size() + " lease regions (" + totalRegions + " total)",
                NamedTextColor.YELLOW));
        return CompletableFuture.supplyAsync(() -> importAll(database, audience, buyRegions, rentRegions), executor);
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

    private record LeaseDto(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price,
                            long durationSeconds,
                            int maxRenewals,
                            int currentRenewals,
                            @NotNull UUID landlordId,
                            @Nullable UUID tenantId) {
    }

}
