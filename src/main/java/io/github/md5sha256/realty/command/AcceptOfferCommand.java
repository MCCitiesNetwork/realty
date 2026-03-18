package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
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
        @NotNull MessageContainer messages
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("acceptoffer")
                .requires(source -> source.getSender().hasPermission("realty.command.acceptoffer"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ArgumentTypes.player()::listSuggestions)
                        .then(Commands.argument("region", new WorldGuardRegionArgument())
                                .executes(this::execute)));
    }

    @SuppressWarnings("deprecation")
    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String playerName = ctx.getArgument("player", String.class);
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        CommandSender sender = ctx.getSource().getSender();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return Command.SINGLE_SUCCESS;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.AcceptOfferResult result = logic.acceptOffer(
                        regionId, region.world().getUID(),
                        target.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.AcceptOfferResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.success",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
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
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("accept-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
