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
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty unsetprice <region>}.
 *
 * <p>Permission: {@code realty.command.unsetprice}.</p>
 */
public record UnsetPriceCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("unsetprice")
                .permission("realty.command.unsetprice")
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.UnsetPriceResult result = logic.unsetPrice(
                        regionId, region.world().getUID());
                switch (result) {
                    case RealtyLogicImpl.UnsetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.success",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.offer-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.bid-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("unset-price.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("unset-price.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
