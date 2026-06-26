package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.api.ExecutorState;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Handles {@code /realty sign place <region>}, {@code /realty sign remove},
 * and {@code /realty sign list <region>}.
 *
 * <p>Permissions: {@code realty.command.sign.place}, {@code realty.command.sign.remove},
 * {@code realty.command.sign.list}.</p>
 */
public record SignCommand(@NotNull RealtyPaperApi api,
                           @NotNull ExecutorState executorState,
                           @NotNull MessageContainer messages) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        Command<Source> place = builder
                .literal("sign")
                .literal("place")
                .required("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .permission("realty.command.sign.place")
                .handler(this::executePlace)
                .build();
        Command<Source> remove = builder
                .literal("sign")
                .literal("remove")
                .permission("realty.command.sign.remove")
                .handler(this::executeRemove)
                .build();
        Command<Source> list = builder
                .literal("sign")
                .literal("list")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .permission("realty.command.sign.list")
                .handler(this::executeList)
                .build();
        return List.of(place, remove, list);
    }

    private static final String BYPASS_PERMISSION = "realty.command.sign.place.bypass";

    private void executePlace(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
            sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_NOT_A_SIGN));
            return;
        }
        // The player must be able to build where the sign physically sits, so signs cannot
        // be registered inside a region the player has no access to.
        if (!canBuildAt(player, targetBlock)) {
            sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_NO_BUILD_ACCESS));
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        int blockX = targetBlock.getX();
        int blockY = targetBlock.getY();
        int blockZ = targetBlock.getZ();
        UUID signWorldId = targetBlock.getWorld().getUID();

        // Staff with the bypass permission may place signs for any region; everyone else
        // may only place signs for regions they are the landlord of.
        boolean canBypass = player.hasPermission(BYPASS_PERMISSION);
        if (canBypass) {
            placeSign(player, region, regionId, signWorldId, blockX, blockY, blockZ);
            return;
        }
        UUID worldId = region.world().getUID();
        api.getLeaseholdContract(regionId, worldId)
                .thenAccept(lease -> {
                    if (lease == null || !player.getUniqueId().equals(lease.landlordId())) {
                        player.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_NOT_LANDLORD,
                                Placeholder.unparsed("region", regionId)));
                        return;
                    }
                    placeSign(player, region, regionId, signWorldId, blockX, blockY, blockZ);
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    player.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    private void placeSign(@NotNull Player sender, @NotNull WorldGuardRegion region,
                           @NotNull String regionId, @NotNull UUID signWorldId,
                           int blockX, int blockY, int blockZ) {
        api.placeSign(region, signWorldId, blockX, blockY, blockZ)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.PlaceSignResult.Success ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_SUCCESS,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyPaperApi.PlaceSignResult.NotRegistered ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_NOT_REGISTERED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyPaperApi.PlaceSignResult.Error error ->
                                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    /**
     * Tests whether the player is allowed to build at the given block, honouring WorldGuard
     * region membership and the {@code BUILD} flag (and the WorldGuard bypass permission).
     */
    private boolean canBuildAt(@NotNull Player player, @NotNull Block block) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(block.getWorld());
        if (WorldGuard.getInstance().getPlatform().getSessionManager()
                .hasBypass(localPlayer, weWorld)) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().createQuery();
        return query.testBuild(BukkitAdapter.adapt(block.getLocation()), localPlayer, Flags.BUILD);
    }

    private void executeRemove(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
            sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_NOT_A_SIGN));
            return;
        }
        int blockX = targetBlock.getX();
        int blockY = targetBlock.getY();
        int blockZ = targetBlock.getZ();
        UUID signWorldId = targetBlock.getWorld().getUID();
        Sign sign = (Sign) targetBlock.getState();

        api.removeSign(signWorldId, blockX, blockY, blockZ)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.RemoveSignResult.Success ignored -> {
                            executorState.mainThreadExec().execute(
                                    () -> SignTextApplicator.clearLines(sign));
                            sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_SUCCESS));
                        }
                        case RealtyPaperApi.RemoveSignResult.NotRegistered ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_NOT_REGISTERED));
                        case RealtyPaperApi.RemoveSignResult.Error error ->
                                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        api.listSigns(regionId, worldId)
                .thenAccept(signs -> {
                    if (signs.isEmpty()) {
                        sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_NO_SIGNS,
                                Placeholder.unparsed("region", regionId)));
                        return;
                    }
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_HEADER,
                            Placeholder.unparsed("region", regionId)));
                    for (RealtySignEntity signEntity : signs) {
                        World signWorld = Bukkit.getWorld(signEntity.worldId());
                        String worldName = signWorld != null
                                ? signWorld.getName() : signEntity.worldId().toString();
                        sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_ENTRY,
                                Placeholder.parsed("world", worldName),
                                Placeholder.parsed("x", String.valueOf(signEntity.blockX())),
                                Placeholder.parsed("y", String.valueOf(signEntity.blockY())),
                                Placeholder.parsed("z", String.valueOf(signEntity.blockZ()))));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }
}
