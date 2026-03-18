package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
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
) implements CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("reload")
                .requires(source -> source.getSender().hasPermission("realty.command.reload"))
                .executes(this::execute);
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
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
        return Command.SINGLE_SUCCESS;
    }

}
