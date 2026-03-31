package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ExecutorState;
import org.incendo.cloud.paper.util.sender.Source;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public record SubregionCommandGroup(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull AtomicReference<Settings> settings,
        @NotNull RegionProfileService regionProfileService,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final CloudKey<WorldGuardRegion> PARENT_REGION =
            CloudKey.of("parentRegion", WorldGuardRegion.class);
    private static final CloudKey<String> NAME = CloudKey.of("name", String.class);
    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> DURATION = CloudKey.of("duration", Duration.class);

    @Override
    public @NotNull List<Command<Source>> commands(
            @NotNull Command.Builder<Source> builder) {
        var base = builder.literal("subregion");
        return List.of(
                base.literal("quickcreate")
                        .permission("realty.command.subregion.quickcreate")
                        .required(PARENT_REGION, WorldGuardRegionParser.worldGuardRegion())
                        .required(NAME, StringParser.stringParser())
                        .required(PRICE, DoubleParser.doubleParser(0))
                        .required(DURATION, DurationParser.duration())
                        .handler(this::executeQuickCreate)
                        .build()
        );
    }

    sealed interface SelectionResult {
        record Success(@NotNull Region selection) implements SelectionResult {}
        record WrongWorld() implements SelectionResult {}
        record IncompleteSelection() implements SelectionResult {}
        record ExceedsParentBounds() implements SelectionResult {}
        record NoRegionManager() implements SelectionResult {}
        record OverlapsSibling(@NotNull ProtectedRegion sibling) implements SelectionResult {}
        record TooSmall(long volume, int minVolume) implements SelectionResult {}
    }

    private static final String BYPASS_PERMISSION = "realty.command.subregion.quickcreate.bypass";

    private void executeQuickCreate(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion parentRegion = ctx.get(PARENT_REGION);
        String name = ctx.get(NAME);
        double price = ctx.get(PRICE);
        Duration duration = ctx.get(DURATION);
        boolean canBypass = player.hasPermission(BYPASS_PERMISSION);

        RegionContainer regionContainer = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();
        RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(parentRegion.world()));
        if (regionManager == null) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Region manager unavailable")));
            return;
        }

        if (regionManager.getRegion(name) != null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_REGION_EXISTS,
                    Placeholder.unparsed("region", name)));
            return;
        }

        SelectionResult result = validateSelection(player, parentRegion, regionManager,
                settings.get().subregionMinVolume());
        switch (result) {
            case SelectionResult.WrongWorld ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_WRONG_WORLD));
            case SelectionResult.IncompleteSelection ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_INCOMPLETE_SELECTION));
            case SelectionResult.ExceedsParentBounds ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_EXCEEDS_BOUNDS,
                            Placeholder.unparsed("region", parentRegion.region().getId())));
            case SelectionResult.NoRegionManager ignored ->
                    player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                            Placeholder.unparsed("error", "Region manager unavailable")));
            case SelectionResult.OverlapsSibling overlap ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_OVERLAPS_SIBLING,
                            Placeholder.unparsed("sibling", overlap.sibling().getId())));
            case SelectionResult.TooSmall tooSmall ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_TOO_SMALL,
                            Placeholder.unparsed("volume", String.valueOf(tooSmall.volume())),
                            Placeholder.unparsed("min-volume", String.valueOf(tooSmall.minVolume()))));
            case SelectionResult.Success success -> {
                UUID playerId = player.getUniqueId();
                String parentId = parentRegion.region().getId();
                UUID worldId = parentRegion.world().getUID();
                if (!canBypass && !parentRegion.region().getOwners().contains(playerId)) {
                    player.sendMessage(messages.messageFor(
                            MessageKeys.SUBREGION_NOT_TITLEHOLDER,
                            Placeholder.unparsed("region", parentId)));
                    return;
                }
                CompletableFuture.supplyAsync(() -> {
                    try {
                        FreeholdContractEntity freehold = logic.getFreeholdContract(parentId, worldId);
                        if (freehold == null) {
                            return new QuickCreateResult.NoFreeholdContract();
                        }
                        boolean created = logic.createLeasehold(
                                name, worldId,
                                price, duration.toSeconds(), -1, playerId);
                        if (!created) {
                            return new QuickCreateResult.RegionExists();
                        }
                        Map<String, String> placeholders = logic.getRegionPlaceholders(name, worldId);
                        return new QuickCreateResult.Created(placeholders);
                    } catch (Exception ex) {
                        throw new CompletionException(ex);
                    }
                }, executorState.dbExec()).thenAcceptAsync(dbResult -> {
                    switch (dbResult) {
                        case QuickCreateResult.NoFreeholdContract ignored ->
                                player.sendMessage(messages.messageFor(
                                        MessageKeys.SUBREGION_NO_FREEHOLD,
                                        Placeholder.unparsed("region", parentId)));
                        case QuickCreateResult.RegionExists ignored ->
                                player.sendMessage(messages.messageFor(
                                        MessageKeys.SUBREGION_REGION_EXISTS,
                                        Placeholder.unparsed("region", name)));
                        case QuickCreateResult.Created created -> {
                            ProtectedRegion childRegion = createProtectedRegion(name, success.selection());
                            try {
                                childRegion.setParent(parentRegion.region());
                            } catch (ProtectedRegion.CircularInheritanceException ex) {
                                player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                                        Placeholder.unparsed("error", "Circular region inheritance")));
                                return;
                            }
                            regionManager.addRegion(childRegion);
                            childRegion.getOwners().addPlayer(playerId);
                            WorldGuardRegion childWgRegion = new WorldGuardRegion(
                                    childRegion, parentRegion.world());
                            regionProfileService.applyFlags(
                                    childWgRegion, RegionState.FOR_LEASE, created.placeholders());
                            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_SUCCESS,
                                    Placeholder.unparsed("region", name),
                                    Placeholder.unparsed("parent", parentId)));
                        }
                    }
                }, executorState.mainThreadExec()).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                    return null;
                });
            }
        }
    }

    private sealed interface QuickCreateResult {
        record NoFreeholdContract() implements QuickCreateResult {}
        record RegionExists() implements QuickCreateResult {}
        record Created(@NotNull Map<String, String> placeholders) implements QuickCreateResult {}
    }

    private static @NotNull SelectionResult validateSelection(@NotNull Player player,
                                                               @NotNull WorldGuardRegion parentRegion,
                                                               @NotNull RegionManager regionManager,
                                                               int minVolume) {
        SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
        LocalSession localSession = sessionManager.get(BukkitAdapter.adapt(player));
        if (!Objects.equals(localSession.getSelectionWorld(),
                BukkitAdapter.adapt(parentRegion.world()))) {
            return new SelectionResult.WrongWorld();
        }
        Region selection;
        try {
            selection = localSession.getSelection().clone();
        } catch (IncompleteRegionException ex) {
            return new SelectionResult.IncompleteSelection();
        }
        long volume = selection.getVolume();
        if (volume < minVolume) {
            return new SelectionResult.TooSmall(volume, minVolume);
        }
        ProtectedRegion parent = parentRegion.region();
        if (!regionIsFullyContainedByParent(selection, parent)) {
            return new SelectionResult.ExceedsParentBounds();
        }
        for (ProtectedRegion sibling : regionManager.getRegions().values()) {
            if (!Objects.equals(sibling.getParent(), parent)) {
                continue;
            }
            for (BlockVector3 point : selection) {
                if (sibling.contains(point)) {
                    return new SelectionResult.OverlapsSibling(sibling);
                }
            }
        }
        return new SelectionResult.Success(selection);
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

    private static boolean regionIsFullyContainedByParent(@NotNull Region region,
                                                           @NotNull ProtectedRegion parent) {
        if (region instanceof CuboidRegion cuboid) {
            return checkCuboidFacesContained(parent,
                    cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
        } else if (region instanceof Polygonal2DRegion polygon) {
            return checkPolygonSurfaceContained(parent, polygon);
        }
        for (BlockVector3 point : region) {
            if (!parent.contains(point)) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkCuboidFacesContained(ProtectedRegion region,
                                                      BlockVector3 min,
                                                      BlockVector3 max) {
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(min.x(), y, z) || !region.contains(max.x(), y, z)) {
                    return false;
                }
            }
        }
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(x, min.y(), z) || !region.contains(x, max.y(), z)) {
                    return false;
                }
            }
        }
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                if (!region.contains(x, y, min.z()) || !region.contains(x, y, max.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean checkPolygonSurfaceContained(ProtectedRegion parent,
                                                         Polygonal2DRegion polygon) {
        List<BlockVector2> points = polygon.getPoints();
        int minY = polygon.getMinimumY();
        int maxY = polygon.getMaximumY();
        BlockVector3 min = polygon.getMinimumPoint();
        BlockVector3 max = polygon.getMaximumPoint();

        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (polygon.contains(BlockVector3.at(x, minY, z))) {
                    if (!parent.contains(x, minY, z) || !parent.contains(x, maxY, z)) {
                        return false;
                    }
                }
            }
        }

        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            int dx = Math.abs(b.x() - a.x());
            int dz = Math.abs(b.z() - a.z());
            int sx = a.x() < b.x() ? 1 : -1;
            int sz = a.z() < b.z() ? 1 : -1;
            int err = dx - dz;
            int cx = a.x();
            int cz = a.z();
            while (true) {
                for (int y = minY; y <= maxY; y++) {
                    if (!parent.contains(cx, y, cz)) {
                        return false;
                    }
                }
                if (cx == b.x() && cz == b.z()) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 > -dz) {
                    err -= dz;
                    cx += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    cz += sz;
                }
            }
        }
        return true;
    }

}
