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
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
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
        @NotNull RealtyLogicImpl logic
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
            sender.sendMessage("Player " + playerName + " has never played on this server.");
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
                            sender.sendMessage("Accepted offer from " + playerName + " on region " + regionId + ".");
                    case RealtyLogicImpl.AcceptOfferResult.NoOffer ignored ->
                            sender.sendMessage("Player " + playerName + " does not have an offer on region " + regionId + ".");
                    case RealtyLogicImpl.AcceptOfferResult.AuctionExists ignored ->
                            sender.sendMessage("Region " + regionId + " has an auction. Offers cannot be accepted while an auction exists.");
                    case RealtyLogicImpl.AcceptOfferResult.AlreadyAccepted ignored ->
                            sender.sendMessage("Region " + regionId + " already has an accepted offer.");
                    case RealtyLogicImpl.AcceptOfferResult.InsertFailed ignored ->
                            sender.sendMessage("Failed to accept offer on region " + regionId + ".");
                }
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to accept offer: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
