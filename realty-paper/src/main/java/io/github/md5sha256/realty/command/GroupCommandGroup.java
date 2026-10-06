package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.PartyNameParser;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /realty group map|unmap|list}, which give a permission group an account. A
 * group can hold a landlord or authority role only once it has one, because every role raises
 * or takes money.
 *
 * <ul>
 *   <li>{@code /realty group map <group> <account> --government|--business|--system} — give the
 *       group an account, or change the one it has. Contracts that name the group keep naming it
 *       and use the new account from then on.</li>
 *   <li>{@code /realty group unmap <group>} — remove the group's account; refused while any
 *       contract or history entry names the group.</li>
 *   <li>{@code /realty group list} — each group, its account, and how many contracts name it.</li>
 * </ul>
 *
 * <p>Permission: {@code realty.command.group}.</p>
 */
public record GroupCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull PartyResolver partyResolver,
        @NotNull SuggestionProvider<Source> groupSuggestions,
        @NotNull SuggestionProvider<Source> accountSuggestions,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull PartyNames partyNames
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("group")
                .permission("realty.command.group");
        return List.of(
                // The type flag goes last: flags are read only after the arguments.
                PartyFlags.addTo(base.literal("map")
                                .required("group", StringParser.stringParser(), groupSuggestions)
                                .required("account", PartyNameParser.partyName(), accountSuggestions))
                        .handler(this::executeMap)
                        .build(),
                base.literal("unmap")
                        .required("group", StringParser.stringParser(), groupSuggestions)
                        .handler(this::executeUnmap)
                        .build(),
                base.literal("list")
                        .handler(this::executeList)
                        .build()
        );
    }

    private void executeMap(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        // A group's account must be an account: neither a player nor another group.
        if (flag == null || flag == PartyFlag.GROUP) {
            sender.sendMessage(messages.messageFor(MessageKeys.GROUP_ACCOUNT_FLAG_REQUIRED));
            return;
        }
        String groupName = ctx.get("group");
        String accountName = ctx.get("account");
        PartyFlags.resolveThen(partyResolver, executorState, messages, sender, accountName, flag, party -> {
            if (!(party instanceof Party.Account account)) {
                throw new IllegalStateException(accountName + " did not resolve to an account");
            }
            api.mapGroup(groupName, account)
                    .thenApplyAsync(result -> {
                        Party.Group mapped = switch (result) {
                            case RealtyBackend.MapGroupResult.Created created -> created.group();
                            case RealtyBackend.MapGroupResult.Changed changed -> changed.current();
                            case RealtyBackend.MapGroupResult.NoChange noChange -> noChange.group();
                        };
                        return messages.messageFor(MessageKeys.GROUP_MAPPED,
                                Placeholder.unparsed("group", mapped.groupName()),
                                Placeholder.unparsed("account", accountName(mapped, partyNames)));
                    }, executorState.dbExec())
                    .thenAccept(sender::sendMessage)
                    .exceptionally(ex -> sendError(sender, ex));
        });
    }

    private void executeUnmap(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String groupName = ctx.get("group");
        api.unmapGroup(groupName).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.UnmapGroupResult.Success success ->
                        sender.sendMessage(messages.messageFor(MessageKeys.GROUP_UNMAPPED,
                                Placeholder.unparsed("group", success.group().groupName())));
                case RealtyBackend.UnmapGroupResult.NotMapped ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.GROUP_NOT_MAPPED,
                                Placeholder.unparsed("group", groupName)));
                case RealtyBackend.UnmapGroupResult.StillInUse stillInUse ->
                        sender.sendMessage(messages.messageFor(MessageKeys.GROUP_STILL_IN_USE,
                                Placeholder.unparsed("group", groupName.toLowerCase(Locale.ROOT)),
                                Placeholder.unparsed("contracts", String.valueOf(stillInUse.contractCount())),
                                Placeholder.unparsed("history", String.valueOf(stillInUse.historyCount()))));
            }
        }).exceptionally(ex -> sendError(sender, ex));
    }

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        // The account names come from Treasury, so the lines are built on the database executor too.
        api.listGroupMappings()
                .thenApplyAsync(mappings -> mappings.stream()
                        .map(mapping -> messages.messageFor(MessageKeys.GROUP_LIST_ENTRY,
                                Placeholder.unparsed("group", mapping.group().groupName()),
                                Placeholder.unparsed("account", accountName(mapping.group(), partyNames)),
                                Placeholder.unparsed("contracts", String.valueOf(mapping.contractCount()))))
                        .toList(), executorState.dbExec())
                .thenAccept(lines -> {
                    if (lines.isEmpty()) {
                        sender.sendMessage(messages.messageFor(MessageKeys.GROUP_LIST_EMPTY));
                    } else {
                        lines.forEach(sender::sendMessage);
                    }
                })
                .exceptionally(ex -> sendError(sender, ex));
    }

    /**
     * The group's account as it is shown everywhere else, such as {@code GovSecurity (government)}.
     * Asks Treasury, so call it off the main thread.
     */
    static @NotNull String accountName(@NotNull Party.Group group, @NotNull PartyNames partyNames) {
        return partyNames.display(Party.account(group.accountId(), group.accountKind()));
    }

    private @Nullable Void sendError(@NotNull CommandSender sender, @NotNull Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
        return null;
    }
}
