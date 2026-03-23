package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty tp <region>}.
 *
 * <p>Teleports the player to a safe location near a region sign if one exists,
 * otherwise falls back to an expanding-cube search within the region bounds.</p>
 *
 * <p>Permission: {@code realty.command.tp}.</p>
 */
public record TeleportCommand(@NotNull ExecutorState executorState,
                               @NotNull Database database,
                               @NotNull MessageContainer messages,
                               @NotNull SafeLocationFinder safeLocationFinder) implements CustomCommandBean.Single {

    private static final int SIGN_SEARCH_RADIUS = 3;
    private static final int REGION_MAX_TRIES = 50000;

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("tp")
                .permission("realty.command.tp")
                .required("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        executorState.dbExec().execute(() -> {
            try {
                List<RealtySignEntity> signs;
                try (SqlSessionWrapper session = database.openSession(true)) {
                    signs = session.realtySignMapper().selectByRegion(regionId, worldId);
                }

                // Build an async search chain: try each sign, then fall back to region
                CompletableFuture<Location> search = CompletableFuture.completedFuture(null);
                for (RealtySignEntity sign : signs) {
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

                search.thenCompose(loc -> {
                    if (loc != null) {
                        return CompletableFuture.completedFuture(loc);
                    }
                    return safeLocationFinder.findSafeInRegion(
                            region.region(), region.world(), REGION_MAX_TRIES);
                }).whenComplete((loc, ex) -> {
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
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                player.sendMessage(messages.messageFor(MessageKeys.TP_ERROR,
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }
}
