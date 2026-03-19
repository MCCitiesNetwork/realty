package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty reload}.
 *
 * <p>Permission: {@code realty.command.reload}.</p>
 */
public record ReloadCommand(
        @NotNull ExecutorState executorState,
        @NotNull Callable<Void> reloadTask,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("reload")
                .permission("realty.command.reload")
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        CompletableFuture.runAsync(() -> {
            try {
                reloadTask.call();
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("reload.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return;
            }
            sender.sendMessage(messages.messageFor("reload.success"));
        }, executorState.dbExec());
    }

}
