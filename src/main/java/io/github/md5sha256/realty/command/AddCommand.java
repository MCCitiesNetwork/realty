package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty add <player|group> [region] [time]}.
 *
 * <p>Base permission: {@code realty.command.add}.
 * Acting on another player's region additionally requires {@code realty.command.add.others}.</p>
 */
public class AddCommand implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("add")
                .requires(source -> source.getSender().hasPermission("realty.command.add"))
                .executes(this::execute);
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Command.SINGLE_SUCCESS;
    }

}
