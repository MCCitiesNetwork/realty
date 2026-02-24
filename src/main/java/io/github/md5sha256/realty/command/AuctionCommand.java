package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty auction <player> <duration> <paymentduration> <minimumbid> <minimumpricestep> [region]}.
 *
 * <p>Base permission: {@code realty.command.auction}.
 * Auctioning on behalf of another player additionally requires {@code realty.command.auction.others}.</p>
 */
public class AuctionCommand implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("auction")
                .requires(source -> source.getSender().hasPermission("realty.command.auction"))
                .executes(this::execute);
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Command.SINGLE_SUCCESS;
    }

}
