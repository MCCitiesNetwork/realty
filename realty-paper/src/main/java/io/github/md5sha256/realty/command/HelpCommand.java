package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty help [category]}.
 *
 * <p>Categories: basics, management, offers, auctions, admin (hidden).
 * Permission: {@code realty.command.help}.</p>
 */
public record HelpCommand(
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final Set<String> VISIBLE_CATEGORIES = Set.of("basics", "management", "offers", "auctions");
    private static final Set<String> ALL_CATEGORIES = Set.of("basics", "management", "offers", "auctions", "admin");

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder) {
        var base = builder
                .literal("help")
                .permission("realty.command.help");
        return List.of(
                base.handler(this::executeMain).build(),
                base.required("category", StringParser.stringParser(), categorySuggestions())
                        .handler(this::executeCategory)
                        .build()
        );
    }

    private static @NotNull SuggestionProvider<CommandSourceStack> categorySuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                VISIBLE_CATEGORIES.stream()
                        .sorted()
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void executeMain(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        sender.sendMessage(messages.messageFor(MessageKeys.HELP_MAIN));
    }

    private void executeCategory(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        String category = ctx.<String>get("category").toLowerCase();
        if (!ALL_CATEGORIES.contains(category)) {
            sender.sendMessage(messages.messageFor(MessageKeys.HELP_UNKNOWN_CATEGORY));
            return;
        }
        String key = switch (category) {
            case "basics" -> MessageKeys.HELP_BASICS;
            case "management" -> MessageKeys.HELP_MANAGEMENT;
            case "offers" -> MessageKeys.HELP_OFFERS;
            case "auctions" -> MessageKeys.HELP_AUCTIONS;
            case "admin" -> MessageKeys.HELP_ADMIN;
            default -> MessageKeys.HELP_UNKNOWN_CATEGORY;
        };
        sender.sendMessage(messages.messageFor(key));
    }

}
