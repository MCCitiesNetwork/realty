package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
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
 * Handles {@code /realty withdrawoffer <region>}.
 *
 * <p>Permission: {@code realty.command.withdrawoffer}.</p>
 */
public record WithdrawOfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("withdrawoffer")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.withdrawoffer"))
                .then(Commands.argument("region", new WorldGuardRegionArgument())
                        .executes(this::execute));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                int deleted = logic.withdrawOffer(regionId, region.world().getUID(), sender.getUniqueId());
                if (deleted == 0) {
                    sender.sendMessage(messages.messageFor("withdraw-offer.no-offer",
                            Placeholder.unparsed("region", regionId)));
                    return;
                }
                sender.sendMessage(messages.messageFor("withdraw-offer.success",
                        Placeholder.unparsed("region", regionId)));
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("withdraw-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
