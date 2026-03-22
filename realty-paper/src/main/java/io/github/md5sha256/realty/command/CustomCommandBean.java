package io.github.md5sha256.realty.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomCommandBean {

    @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder);

    interface Single extends CustomCommandBean {
        @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder);

        @Override
        default @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder) {
            return List.of(command(builder));
        }
    }

}
