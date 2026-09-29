package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The type flags {@code --government}, {@code --business}, {@code --system} and {@code --group},
 * which make the name a command is given for a landlord or an authority a Treasury account or a
 * mapped group instead of a player. At most one of them may be given.
 */
public final class PartyFlags {

    private PartyFlags() {}

    public sealed interface Read {
        /** @param flag the one type flag given, or {@code null} if none was */
        record One(@Nullable PartyFlag flag) implements Read {}
        record TooMany() implements Read {}
    }

    private static @NotNull String flagName(@NotNull PartyFlag flag) {
        return flag.name().toLowerCase(Locale.ROOT);
    }

    /** Adds the four presence flags --government, --business, --system and --group. */
    public static <C> Command.Builder<C> addTo(@NotNull Command.Builder<C> builder) {
        Command.Builder<C> result = builder;
        for (PartyFlag flag : PartyFlag.values()) {
            result = result.flag(CommandFlag.<C>builder(flagName(flag)).build());
        }
        return result;
    }

    /**
     * A {@code --<role> <name>} flag, such as {@code --landlord}, whose name is resolved to a party
     * in the handler once the type flag is known.
     */
    public static @NotNull CommandFlag<String> nameFlag(@NotNull String role,
                                                        @NotNull SuggestionProvider<Source> suggestions) {
        return CommandFlag.<Source>builder(role)
                .withComponent(CommandComponent.<Source, String>builder(role, StringParser.stringParser())
                        .suggestionProvider(suggestions))
                .build();
    }

    public static @NotNull Read read(@NotNull CommandContext<?> ctx) {
        PartyFlag found = null;
        for (PartyFlag flag : PartyFlag.values()) {
            if (!ctx.flags().isPresent(flagName(flag))) {
                continue;
            }
            if (found != null) {
                return new Read.TooMany();
            }
            found = flag;
        }
        return new Read.One(found);
    }

    /**
     * Online players first, then the sender's accounts by display name, then mapped groups.
     * The accounts and groups are looked up on {@code lookupExecutor}, because both lookups do
     * blocking I/O. An account whose display name contains a space is offered as {@code #<id>},
     * since a name with a space cannot be typed as one argument.
     */
    public static @NotNull SuggestionProvider<Source> suggestions(@Nullable TreasuryApi treasury,
                                                                  @NotNull RealtyBackend backend,
                                                                  @NotNull Executor lookupExecutor) {
        return (ctx, input) -> {
            List<Suggestion> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .map(Suggestion::suggestion)
                    .toList();
            if (treasury == null) {
                // Without Treasury only players can be parties.
                return CompletableFuture.completedFuture(players);
            }
            UUID senderId = ctx.sender().source() instanceof Player player ? player.getUniqueId() : null;
            return CompletableFuture.supplyAsync(() -> {
                List<Suggestion> all = new ArrayList<>(players);
                all.addAll(accountsOf(treasury, senderId));
                all.addAll(mappedGroups(backend));
                return all;
            }, lookupExecutor).exceptionally(_ -> players);
        };
    }

    /**
     * The sender's accounts only, offered as {@link #suggestions} offers them, for an argument
     * that must name an account.
     */
    public static @NotNull SuggestionProvider<Source> accountSuggestions(@Nullable TreasuryApi treasury,
                                                                         @NotNull Executor lookupExecutor) {
        return (ctx, input) -> {
            if (treasury == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            UUID senderId = ctx.sender().source() instanceof Player player ? player.getUniqueId() : null;
            return CompletableFuture.supplyAsync(() -> accountsOf(treasury, senderId), lookupExecutor)
                    .exceptionally(_ -> List.of());
        };
    }

    /** Mapped groups only, looked up on {@code lookupExecutor}. */
    public static @NotNull SuggestionProvider<Source> groupSuggestions(@NotNull RealtyBackend backend,
                                                                       @NotNull Executor lookupExecutor) {
        return (ctx, input) -> CompletableFuture.supplyAsync(() -> mappedGroups(backend), lookupExecutor)
                .exceptionally(_ -> List.of());
    }

    private static @NotNull List<Suggestion> accountsOf(@NotNull TreasuryApi treasury, @Nullable UUID senderId) {
        if (senderId == null) {
            return List.of();
        }
        List<Suggestion> accounts = new ArrayList<>();
        for (Account account : treasury.getAccountsByMember(senderId)) {
            if (account.getAccountType() == AccountType.PERSONAL || account.isArchived()) {
                continue;
            }
            String name = account.getDisplayName();
            accounts.add(Suggestion.suggestion(name == null || name.isBlank() || name.contains(" ")
                    ? "#" + account.getAccountId() : name));
        }
        return accounts;
    }

    private static @NotNull List<Suggestion> mappedGroups(@NotNull RealtyBackend backend) {
        return backend.listGroupMappings().stream()
                .map(mapping -> Suggestion.suggestion(mapping.group().groupName()))
                .toList();
    }

    /**
     * Decides which party gets a role that a command names with {@code --<role> <name>}, and passes it
     * to {@code onResolved}:
     * <ul>
     *   <li>a name is resolved with the type flag, as {@link #resolveThen} does;</li>
     *   <li>a type flag without a name is refused, since it describes nothing;</li>
     *   <li>with neither, {@code fallback} (the default from settings.yml) is used, or the command is
     *       refused when that default could not be resolved.</li>
     * </ul>
     */
    public static void resolveOrDefault(@NotNull PartyResolver resolver, @NotNull ExecutorState executorState,
                                        @NotNull MessageContainer messages, @NotNull CommandSender sender,
                                        @NotNull String role, @Nullable String name, @Nullable PartyFlag flag,
                                        @Nullable Party fallback, @NotNull Consumer<Party> onResolved) {
        if (name != null) {
            resolveThen(resolver, executorState, messages, sender, name, flag, onResolved);
        } else if (flag != null) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_TYPE_FLAG_WITHOUT_NAME,
                    Placeholder.unparsed("usage", "--" + role + " <name>")));
        } else if (fallback == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_DEFAULT_PARTY_UNRESOLVED,
                    Placeholder.unparsed("role", role)));
        } else {
            onResolved.accept(fallback);
        }
    }

    /**
     * Resolves {@code name} with {@code resolver} on the database executor, then on the main
     * thread either passes the party to {@code onResolved} or tells {@code sender} why it was
     * refused. An error while resolving ends the command with an error message.
     */
    public static void resolveThen(@NotNull PartyResolver resolver, @NotNull ExecutorState executorState,
                                   @NotNull MessageContainer messages, @NotNull CommandSender sender,
                                   @NotNull String name, @Nullable PartyFlag flag,
                                   @NotNull Consumer<Party> onResolved) {
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        CompletableFuture.supplyAsync(() -> resolver.resolve(name, flag, senderId), executorState.dbExec())
                .thenAcceptAsync(resolution -> {
                    switch (resolution) {
                        case PartyResolver.Resolution.Resolved resolved -> onResolved.accept(resolved.party());
                        case PartyResolver.Resolution.Refused refused ->
                                sender.sendMessage(refusal(messages, refused, flag));
                    }
                }, executorState.mainThreadExec())
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    /**
     * The message for a refused name. A refusal carries only its key and the name, so the
     * {@code <type>} some of the messages name is the flag's, in lower case.
     */
    public static @NotNull Component refusal(@NotNull MessageContainer messages,
                                             @NotNull PartyResolver.Resolution.Refused refused,
                                             @Nullable PartyFlag flag) {
        return messages.messageFor(refused.messageKey(),
                Placeholder.unparsed("name", refused.name()),
                // The player-not-found message names the player as <player>.
                Placeholder.unparsed("player", refused.name()),
                Placeholder.unparsed("type", flag != null ? flagName(flag) : ""));
    }
}
