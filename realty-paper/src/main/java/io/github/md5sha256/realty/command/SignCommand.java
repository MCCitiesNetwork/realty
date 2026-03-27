package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.SignCache;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
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
public record SignCommand(@NotNull ExecutorState executorState,
                           @NotNull Database database,
                           @NotNull RealtyLogicImpl logic,
                           @NotNull RegionProfileService regionProfileService,
                           @NotNull SignCache signCache,
                           @NotNull SignTextApplicator signTextApplicator,
                           @NotNull MessageContainer messages) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder) {
        Command<CommandSourceStack> place = builder
                .literal("sign")
                .literal("place")
                .required("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .permission("realty.command.sign.place")
                .handler(this::executePlace)
                .build();
        Command<CommandSourceStack> remove = builder
                .literal("sign")
                .literal("remove")
                .permission("realty.command.sign.remove")
                .handler(this::executeRemove)
                .build();
        Command<CommandSourceStack> list = builder
                .literal("sign")
                .literal("list")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .permission("realty.command.sign.list")
                .handler(this::executeList)
                .build();
        return List.of(place, remove, list);
    }

    private void executePlace(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
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
        UUID worldId = region.world().getUID();
        int blockX = targetBlock.getX();
        int blockY = targetBlock.getY();
        int blockZ = targetBlock.getZ();
        UUID signWorldId = targetBlock.getWorld().getUID();
        executorState.dbExec().execute(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RealtySignEntity existing = session.realtySignMapper()
                        .selectByPosition(signWorldId, blockX, blockY, blockZ);
                if (existing == null) {
                    int rows = session.realtySignMapper()
                            .insert(signWorldId, blockX, blockY, blockZ, regionId, worldId);
                    if (rows == 0) {
                        sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_NOT_REGISTERED,
                                Placeholder.unparsed("region", regionId)));
                        return;
                    }
                    RealtyRegionEntity regionEntity = session.realtyRegionMapper()
                            .selectByWorldGuardRegion(regionId, worldId);
                    if (regionEntity != null) {
                        signCache.put(signWorldId, blockX, blockY, blockZ,
                                regionEntity.realtyRegionId(), regionId, worldId);
                    }
                }
                RealtyLogicImpl.RegionWithState rws = logic.getRegionWithState(regionId, worldId);
                if (rws != null) {
                    executorState.mainThreadExec().execute(() -> {
                        Block block = player.getWorld().getBlockAt(blockX, blockY, blockZ);
                        if (block.getState(false) instanceof Sign) {
                            signTextApplicator.applySignText(player.getWorld(),
                                    blockX, blockY, blockZ,
                                    regionId, rws.state(), rws.placeholders());
                        }
                    });
                }
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_SUCCESS,
                        Placeholder.unparsed("region", regionId)));
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_PLACE_ERROR,
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }

    private void executeRemove(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
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
        executorState.dbExec().execute(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                int rows = session.realtySignMapper()
                        .deleteByPosition(signWorldId, blockX, blockY, blockZ);
                if (rows == 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_NOT_REGISTERED));
                    return;
                }
                signCache.remove(signWorldId, blockX, blockY, blockZ);
                executorState.mainThreadExec().execute(() -> SignTextApplicator.clearLines(sign));
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_SUCCESS));
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_ERROR,
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }

    private void executeList(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
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
        executorState.dbExec().execute(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                List<RealtySignEntity> signs = session.realtySignMapper()
                        .selectByRegion(regionId, worldId);
                if (signs.isEmpty()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_NO_SIGNS,
                            Placeholder.unparsed("region", regionId)));
                    return;
                }
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_HEADER,
                        Placeholder.unparsed("region", regionId)));
                for (RealtySignEntity sign : signs) {
                    World signWorld = Bukkit.getWorld(sign.worldId());
                    String worldName = signWorld != null ? signWorld.getName() : sign.worldId().toString();
                    sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_ENTRY,
                            Placeholder.parsed("world", worldName),
                            Placeholder.parsed("x", String.valueOf(sign.blockX())),
                            Placeholder.parsed("y", String.valueOf(sign.blockY())),
                            Placeholder.parsed("z", String.valueOf(sign.blockZ()))));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.SIGN_LIST_ERROR,
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }
}
