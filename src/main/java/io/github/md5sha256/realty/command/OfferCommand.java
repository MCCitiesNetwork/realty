package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty offer <price> <region>}.
 *
 * <p>Permission: {@code realty.command.offer}.</p>
 */
public record OfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
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
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.OfferResult result = logic.placeOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), price);
                switch (result) {
                    case RealtyLogicImpl.OfferResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("offer.success",
                                    Placeholder.unparsed("price", String.valueOf(price)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("offer.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.IsAuthority ignored ->
                            sender.sendMessage(messages.messageFor("offer.is-authority"));
                    case RealtyLogicImpl.OfferResult.AlreadyHasOffer ignored ->
                            sender.sendMessage(messages.messageFor("offer.already-has-offer",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("offer.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.InsertFailed ignored ->
                            sender.sendMessage(messages.messageFor("offer.insert-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
