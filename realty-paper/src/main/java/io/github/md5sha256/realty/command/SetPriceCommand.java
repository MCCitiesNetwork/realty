package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty setprice <price> <region>}.
 *
 * <p>Permission: {@code realty.command.setprice}.</p>
 */
public record SetPriceCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("setprice")
                .permission("realty.command.setprice")
                .required("price", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        double price = ctx.get("price");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetPriceResult result = logic.setPrice(
                        regionId, region.world().getUID(), price);
                switch (result) {
                    case RealtyLogicImpl.SetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("set-price.success",
                                    Placeholder.unparsed("price", String.valueOf(price)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("set-price.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("set-price.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("set-price.offer-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("set-price.bid-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-price.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-price.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
