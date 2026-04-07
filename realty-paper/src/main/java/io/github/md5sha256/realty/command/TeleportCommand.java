package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles {@code /realty tp <region>}.
 *
 * <p>Teleports the player to a safe location near a region sign if one exists,
 * otherwise falls back to an expanding-cube search within the region bounds.</p>
 *
 * <p>Permission: {@code realty.command.tp}.</p>
 */
public record TeleportCommand(
        @NotNull Logger logger,
        @NotNull RealtyPaperApi api,
                              @NotNull MessageContainer messages,
                              @NotNull SafeLocationFinder safeLocationFinder) implements CustomCommandBean.Single {

    private static final int SIGN_SEARCH_RADIUS = 3;
    private static final int REGION_MAX_TRIES = 50000;

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("tp")
                .permission("realty.command.tp")
                .required("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        ProtectedRegion protectedRegion = region.region();
        com.sk89q.worldedit.util.Location flagLoc = protectedRegion.getFlag(Flags.TELE_LOC);

        if (flagLoc != null) {
            if (safeLocationFinder.isSafe(region.world(),
                    flagLoc.getBlockX(),
                    flagLoc.getBlockY(),
                    flagLoc.getBlockZ())) {
                processTeleport(player,
                        regionId,
                        new Location(region.world(),
                                flagLoc.getBlockX(),
                                flagLoc.getBlockY(),
                                flagLoc.getBlockZ()),
                        null);
            } else  {
                logger.warning("WG Flag unsafe teleport location for region: " + regionId);
            }
        }

        api.listSigns(regionId, worldId).thenCompose(signs -> {
            // Build an async search chain: try each sign inside the region, then fall back
            CompletableFuture<Location> search = CompletableFuture.completedFuture(null);
            for (RealtySignEntity sign : signs) {
                if (!region.region().contains(BlockVector3.at(
                        sign.blockX(), sign.blockY(), sign.blockZ()))) {
                    continue;
                }
                search = search.thenCompose(loc -> {
                    if (loc != null) {
                        return CompletableFuture.completedFuture(loc);
                    }
                    World signWorld = Bukkit.getWorld(sign.worldId());
                    if (signWorld == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return safeLocationFinder.findSafeNearSign(
                            signWorld, sign.blockX(), sign.blockY(), sign.blockZ(),
                            SIGN_SEARCH_RADIUS);
                });
            }
            return search;
        }).thenCompose(loc -> {
            if (loc != null) {
                return CompletableFuture.completedFuture(loc);
            }
            return safeLocationFinder.findSafeInRegion(
                    region.region(), region.world(), REGION_MAX_TRIES);
        }).whenComplete((loc, ex) -> processTeleport(player, regionId, loc, ex));
    }

    private void processTeleport(@NotNull Player player,
                                 @NotNull String regionId,
                                 @Nullable Location loc,
                                 @Nullable Throwable ex) {
        if (!player.isOnline()) {
            return;
        }
        if (ex != null) {
            ex.printStackTrace();
            player.sendMessage(messages.messageFor(MessageKeys.TP_ERROR,
                    Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            return;
        }
        if (loc != null) {
            player.teleportAsync(loc);
            player.sendMessage(messages.messageFor(MessageKeys.TP_SUCCESS,
                    Placeholder.unparsed("region", regionId)));
        } else {
            player.sendMessage(messages.messageFor(MessageKeys.TP_NO_SAFE_LOCATION,
                    Placeholder.unparsed("region", regionId)));
        }
    }
}
