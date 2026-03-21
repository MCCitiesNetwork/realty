package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.RealtyLogicImpl.CreateAuctionResult;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty auction <bidDuration> <paymentDuration> <minBid> <minBidStep> <region>}.
 *
 * <p>Base permission: {@code realty.command.auction}.
 * Auctioning on behalf of another player additionally requires {@code realty.command.auction.others}.</p>
 */
public record AuctionCommand(@NotNull ExecutorState executorState,
                             @NotNull RealtyLogicImpl logic,
                             @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("auction")
                .permission("realty.command.auction")
                .required("bidDuration", DurationParser.duration())
                .required("paymentDuration", DurationParser.duration())
                .required("minBid", DoubleParser.doubleParser(0))
                .required("minBidStep", DoubleParser.doubleParser(0))
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        Duration bidDuration = ctx.get("bidDuration");
        Duration paymentDuration = ctx.get("paymentDuration");
        double minBid = ctx.get("minBid");
        double minBidStep = ctx.get("minBidStep");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                CreateAuctionResult result = logic.createAuction(
                        regionId,
                        region.world().getUID(),
                        player.getUniqueId(),
                        bidDuration.toSeconds(),
                        paymentDuration.toSeconds(),
                        minBid,
                        minBidStep
                );
                switch (result) {
                    case CreateAuctionResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("auction.success",
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NotSanctioned ignored ->
                            sender.sendMessage(messages.messageFor("auction.not-sanctioned",
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("auction.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("auction.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
