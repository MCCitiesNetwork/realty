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
 * Handles {@code /realty bid <price> <region>}.
 *
 * <p>Permission: {@code realty.command.bid}.</p>
 */
public record BidCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("bid")
                .permission("realty.command.bid")
                .required("bid", DoubleParser.doubleParser(0))
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        double bidAmount = ctx.<Double>get("bid");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.BidResult result = logic.performBid(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), bidAmount);
                switch (result) {
                    case RealtyLogicImpl.BidResult.Success success -> {
                            sender.sendMessage(messages.messageFor("bid.success",
                                    Placeholder.unparsed("amount", String.valueOf(bidAmount)),
                                    Placeholder.unparsed("region", regionId)));
                            if (success.previousBidderId() != null) {
                                notificationService.queueNotification(success.previousBidderId(),
                                        messages.prefixedMessageFor("notification.outbid",
                                                Placeholder.unparsed("region", regionId),
                                                Placeholder.unparsed("amount", String.valueOf(bidAmount))));
                            }
                    }
                    case RealtyLogicImpl.BidResult.NoAuction ignored ->
                            sender.sendMessage(messages.messageFor("bid.no-auction"));
                    case RealtyLogicImpl.BidResult.BidTooLowMinimum r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-minimum",
                                    Placeholder.unparsed("amount", String.valueOf(r.minBid()))));
                    case RealtyLogicImpl.BidResult.BidTooLowCurrent r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-current",
                                    Placeholder.unparsed("amount", String.valueOf(r.currentHighest()))));
                }
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("bid.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
