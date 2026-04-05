package io.github.md5sha256.realty.command;

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
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        int blockX = targetBlock.getX();
        int blockY = targetBlock.getY();
        int blockZ = targetBlock.getZ();
        UUID signWorldId = targetBlock.getWorld().getUID();

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
