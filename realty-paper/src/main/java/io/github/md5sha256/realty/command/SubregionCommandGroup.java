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
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public record SubregionCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull AtomicReference<Settings> settings,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final CloudKey<WorldGuardRegion> PARENT_REGION =
            CloudKey.of("parentRegion", WorldGuardRegion.class);
    private static final CloudKey<String> NAME = CloudKey.of("name", String.class);
    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> DURATION = CloudKey.of("duration", Duration.class);
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    @Override
    public @NotNull List<Command<? extends Source>> commands(
            @NotNull Command.Builder<Source> builder) {
        var base = builder.literal("subregion");
        return List.of(
                base.literal("quickcreate")
                        .permission("realty.command.subregion.quickcreate")
                        .required(PARENT_REGION, WorldGuardRegionParser.worldGuardRegion())
                        .required(NAME, StringParser.stringParser())
                        .required(PRICE, DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
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
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_INVALID_NAME,
                    Placeholder.unparsed("region", name)));
            return;
        }
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

        Collection<String> blacklist = settings.get().subregionTagBlacklist();
        if (!blacklist.isEmpty()) {
            String parentId = parentRegion.region().getId();
            api.getTagIdsByRegion(parentId).thenAccept(tags -> {
                for (String tag : tags) {
                    if (blacklist.contains(tag)) {
                        player.sendMessage(messages.messageFor(
                                MessageKeys.SUBREGION_TAG_BLACKLISTED,
                                Placeholder.unparsed("region", parentId),
                                Placeholder.unparsed("tag", tag)));
                        return;
                    }
                }
                continueQuickCreate(player, parentRegion, name, price, duration,
                        canBypass, regionManager);
            }).exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                cause.printStackTrace();
                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                        Placeholder.unparsed("error", cause.getMessage())));
                return null;
            });
            return;
        }

        continueQuickCreate(player, parentRegion, name, price, duration,
                canBypass, regionManager);
    }

    private void continueQuickCreate(@NotNull Player player,
                                      @NotNull WorldGuardRegion parentRegion,
                                      @NotNull String name,
                                      double price,
                                      @NotNull Duration duration,
                                      boolean canBypass,
                                      @NotNull RegionManager regionManager) {
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
                if (!canBypass && !parentRegion.region().getOwners().contains(playerId)) {
                    player.sendMessage(messages.messageFor(
                            MessageKeys.SUBREGION_NOT_TITLEHOLDER,
                            Placeholder.unparsed("region", parentId)));
                    return;
                }
                api.quickCreateSubregion(parentRegion, name, success.selection(),
                                price, duration.toSeconds(), playerId)
                        .thenAccept(qcResult -> {
                            switch (qcResult) {
                                case RealtyPaperApi.QuickCreateSubregionResult.Success s ->
                                        player.sendMessage(messages.messageFor(
                                                MessageKeys.SUBREGION_CREATE_SUCCESS,
                                                Placeholder.unparsed("region", s.regionId()),
                                                Placeholder.unparsed("parent", s.parentId())));
                                case RealtyPaperApi.QuickCreateSubregionResult.NoFreeholdContract nfc ->
                                        player.sendMessage(messages.messageFor(
                                                MessageKeys.SUBREGION_NO_FREEHOLD,
                                                Placeholder.unparsed("region", nfc.parentId())));
                                case RealtyPaperApi.QuickCreateSubregionResult.RegionExists re ->
                                        player.sendMessage(messages.messageFor(
                                                MessageKeys.SUBREGION_REGION_EXISTS,
                                                Placeholder.unparsed("region", re.regionId())));
                                case RealtyPaperApi.QuickCreateSubregionResult.Error error ->
                                        player.sendMessage(messages.messageFor(
                                                MessageKeys.SUBREGION_CREATE_ERROR,
                                                Placeholder.unparsed("error", error.message())));
                            }
                        }).exceptionally(ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            cause.printStackTrace();
                            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_CREATE_ERROR,
                                    Placeholder.unparsed("error", cause.getMessage())));
                            return null;
                        });
            }
        }
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
