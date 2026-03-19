package io.github.md5sha256.realty.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomCommandBean {

    @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager);

    interface Single extends CustomCommandBean {
        @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager);

        @Override
        default @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
            return List.of(command(manager));
        }
    }

}
