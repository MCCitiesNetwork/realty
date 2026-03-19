package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
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
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("offer")
                .permission("realty.command.offer")
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
                RealtyLogicImpl.OfferResult result = logic.placeOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), price);
                switch (result) {
                    case RealtyLogicImpl.OfferResult.Success success -> {
                            sender.sendMessage(messages.messageFor("offer.success",
                                    Placeholder.unparsed("price", String.valueOf(price)),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(success.authorityId(),
                                    messages.prefixedMessageFor("notification.offer-placed",
                                            Placeholder.unparsed("player", sender.getName()),
                                            Placeholder.unparsed("price", String.valueOf(price)),
                                            Placeholder.unparsed("region", regionId)));
                    }
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
    }

}
