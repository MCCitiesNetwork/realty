package io.github.md5sha256.realty.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the base {@code /realty} command (no subcommand).
 *
 * <p>Displays the plugin version.</p>
 */
public record VersionCommand(
        @NotNull String version
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder.literal("version")
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        ctx.sender().getSender().sendMessage(Component.text("Running Realty version " + version));
    }

}
