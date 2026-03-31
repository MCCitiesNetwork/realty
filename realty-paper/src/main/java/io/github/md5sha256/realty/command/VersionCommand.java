package io.github.md5sha256.realty.command;

import net.kyori.adventure.text.Component;
import org.incendo.cloud.paper.util.sender.Source;
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
    public @NotNull Command<Source> command(@NotNull Command.Builder<Source> builder) {
        return builder.literal("version")
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        ctx.sender().source().sendMessage(Component.text("Running Realty version " + version));
    }

}
