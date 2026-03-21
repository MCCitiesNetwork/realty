package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty acceptoffer <player> <region>}.
 *
 * <p>Permission: {@code realty.command.acceptoffer}.</p>
 */
public record AcceptOfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("acceptoffer")
                .permission("realty.command.acceptoffer")
                .required("player", StringParser.stringParser(), playerSuggestions())
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private static @NotNull SuggestionProvider<CommandSourceStack> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        String playerName = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        CommandSender sender = ctx.sender().getSender();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.AcceptOfferResult result = logic.acceptOffer(
                        regionId, region.world().getUID(),
                        target.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.AcceptOfferResult.Success ignored -> {
                            sender.sendMessage(messages.messageFor("accept-offer.success",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(target.getUniqueId(),
                                    messages.messageFor("notification.offer-accepted",
                                            Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.AcceptOfferResult.NoOffer ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.no-offer",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.AlreadyAccepted ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.already-accepted",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.InsertFailed ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.insert-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("accept-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
