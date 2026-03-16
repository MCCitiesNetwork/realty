package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty offer <price> <region>}.
 *
 * <p>Permission: {@code realty.command.offer}.</p>
 */
public record OfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull Database database
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("offer")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.offer"))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0, Double.MAX_VALUE))
                        .then(Commands.argument("region", new WorldGuardRegionArgument())
                                .executes(this::execute)));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double price = DoubleArgumentType.getDouble(ctx, "price");
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                SaleContractOfferMapper offerMapper = wrapper.saleContractOfferMapper();
                String regionId = region.region().getId();
                UUID worldId = region.world().getUID();

                if (saleMapper.selectByRegion(regionId, worldId) == null) {
                    sender.sendMessage("Region " + regionId + " does not have an active sale contract.");
                    return;
                }

                if (saleMapper.existsByRegionAndAuthority(regionId,
                        worldId,
                        sender.getUniqueId())) {
                    sender.sendMessage(
                            "You cannot place an offer on a region where you are the authority.");
                    return;
                }

                if (offerMapper.existsByOfferer(regionId, worldId, sender.getUniqueId())) {
                    sender.sendMessage(
                            "You already have an offer on region " + regionId + ". Withdraw it first before placing a new one.");
                    return;
                }

                int inserted = offerMapper.insertOffer(regionId,
                        worldId,
                        sender.getUniqueId(),
                        price);
                wrapper.session().commit();
                if (inserted == 0) {
                    sender.sendMessage("Failed to place offer on region " + regionId + ".");
                    return;
                }
                sender.sendMessage("Offer of " + price + " placed on region " + regionId + ".");
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to place offer: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
