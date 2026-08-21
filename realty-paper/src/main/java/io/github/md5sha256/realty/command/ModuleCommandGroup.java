package io.github.md5sha256.realty.command;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.LoadedModule;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.ModuleLifecycleManager;
import io.github.md5sha256.realty.Realty;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty module list} and {@code /realty module reload <module>}.
 *
 * <p>{@link ModuleLifecycleManager} is not thread-safe, so both handlers hop onto the main thread
 * before touching it.</p>
 *
 * <p>Permissions: {@code realty.command.module.list}, {@code realty.command.module.reload}.</p>
 */
public record ModuleCommandGroup(
        @NotNull ModuleLifecycleManager<Realty> moduleManager,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        Command<? extends Source> list = builder
                .literal("module")
                .literal("list")
                .permission("realty.command.module.list")
                .handler(this::executeList)
                .build();
        Command<? extends Source> reload = builder
                .literal("module")
                .literal("reload")
                .required("module", StringParser.stringParser(), moduleSuggestions())
                .permission("realty.command.module.reload")
                .handler(this::executeReload)
                .build();
        return List.of(list, reload);
    }

    private @NotNull SuggestionProvider<Source> moduleSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                moduleManager.getActiveModules().keySet().stream()
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        executorState.mainThreadExec().execute(() -> {
            Map<String, LoadedModule<Realty>> active = moduleManager.getActiveModules();
            if (active.isEmpty()) {
                sender.sendMessage(messages.messageFor(MessageKeys.MODULE_LIST_EMPTY));
                return;
            }
            sender.sendMessage(messages.messageFor(MessageKeys.MODULE_LIST_HEADER,
                    Placeholder.unparsed("count", String.valueOf(active.size()))));
            for (LoadedModule<Realty> module : active.values()) {
                sender.sendMessage(messages.messageFor(MessageKeys.MODULE_LIST_ENTRY,
                        Placeholder.unparsed("module", module.manifest().moduleName()),
                        Placeholder.unparsed("author", module.manifest().author()),
                        Placeholder.unparsed("reloadable",
                                String.valueOf(module.manifest().reloadable()))));
            }
        });
    }

    private void executeReload(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String moduleName = ctx.get("module");
        executorState.mainThreadExec().execute(() ->
                moduleManager.reloadAsync(moduleName).whenComplete((ignored, error) -> {
                    if (error == null) {
                        sender.sendMessage(messages.messageFor(MessageKeys.MODULE_RELOAD_SUCCESS,
                                Placeholder.unparsed("module", moduleName)));
                        return;
                    }
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    sender.sendMessage(messages.messageFor(MessageKeys.MODULE_RELOAD_ERROR,
                            Placeholder.unparsed("module", moduleName),
                            Placeholder.unparsed("error", describe(cause))));
                }));
    }

    private static @NotNull String describe(@NotNull Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }

}
