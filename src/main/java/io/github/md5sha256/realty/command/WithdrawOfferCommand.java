package io.github.md5sha256.realty.command;

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
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty withdrawoffer <region>}.
 *
 * <p>Permission: {@code realty.command.withdrawoffer}.</p>
 */
public record WithdrawOfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("withdrawoffer")
                .permission("realty.command.withdrawoffer")
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
                RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, region.world().getUID(), sender.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.WithdrawOfferResult.Success() ->
                            sender.sendMessage(messages.messageFor("withdraw-offer.success",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.WithdrawOfferResult.NoOffer() ->
                            sender.sendMessage(messages.messageFor("withdraw-offer.no-offer",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.WithdrawOfferResult.OfferAccepted() ->
                            sender.sendMessage(messages.messageFor("withdraw-offer.accepted",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("withdraw-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
