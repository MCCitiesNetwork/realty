package io.github.md5sha256.realty.command;

import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CustomCommandBean {

    @NotNull List<Command<Source>> commands(@NotNull Command.Builder<Source> builder);

    interface Single extends CustomCommandBean {
        @NotNull Command<Source> command(@NotNull Command.Builder<Source> builder);

        @Override
        default @NotNull List<Command<Source>> commands(@NotNull Command.Builder<Source> builder) {
            return List.of(command(builder));
        }
    }

}
