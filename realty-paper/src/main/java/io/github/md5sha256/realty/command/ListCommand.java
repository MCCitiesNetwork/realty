package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Handles {@code /realty list [owned|rented] [name] [--page <n>] [type flag]} and {@code /realty me}.
 *
 * <p>Without a name the sender's own regions are listed. A name is a player, or with
 * {@code --government}, {@code --business}, {@code --system} or {@code --group} an account or a
 * mapped group, resolved as every other command resolves a party. Flags come after the name.</p>
 *
 * <p>Permission: {@code realty.command.list}.</p>
 */
public record ListCommand(
        @NotNull RealtyPaperApi api,
        @NotNull PartyResolver partyResolver,
        @NotNull SuggestionProvider<Source> partySuggestions,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull PartyNames partyNames
) implements CustomCommandBean {

    private static final int PAGE_SIZE = 10;

    private static final CommandFlag<Integer> PAGE_FLAG =
            CommandFlag.<Source>builder("page")
                    .withComponent(IntegerParser.integerParser(1))
                    .build();

    /**
     * Who is listed, and the name and type flag the page links repeat.
     *
     * @param playerName how the sender is shown when listing their own regions; {@code null} when a
     *                   name was given, whose display form is looked up off the main thread
     */
    private record Target(@NotNull Party party, @Nullable String playerName,
                          @Nullable String name, @Nullable PartyFlag flag) {}

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("list")
                .permission("realty.command.list");
        var meProxy = builder.literal("me")
                .senderType(PlayerSource.class)
                .permission("realty.command.list")
                .flag(PAGE_FLAG)
                .handler(ctx -> {
                    Player player = ctx.sender().source();
                    int page = ctx.flags().getValue(PAGE_FLAG, 1);
                    listRegions(player, new Target(new Party.Personal(player.getUniqueId()), player.getName(),
                            null, null), null, page);
                })
                .build();
        return List.of(
                meProxy,
                withName(base).handler(ctx -> execute(ctx, null)).build(),
                withName(base.literal("owned")).handler(ctx -> execute(ctx, "owned")).build(),
                withName(base.literal("rented")).handler(ctx -> execute(ctx, "rented")).build()
        );
    }

    /**
     * The optional name, then the flags. The name steps aside for a flag, so that
     * {@code /realty list --page 2} lists the sender instead of looking for a player called
     * {@code --page}.
     */
    private @NotNull Command.Builder<Source> withName(@NotNull Command.Builder<Source> builder) {
        return PartyFlags.addTo(builder
                .optional("name", RegionOrFlagParser.of(StringParser.<Source>stringParser()), partySuggestions)
                .flag(PAGE_FLAG));
    }

    private void execute(@NotNull CommandContext<Source> ctx, @Nullable String category) {
        CommandSender sender = ctx.sender().source();
        int page = ctx.flags().getValue(PAGE_FLAG, 1);
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        String name = ctx.<Optional<String>>optional("name").flatMap(Function.identity()).orElse(null);
        if (name == null) {
            if (flag != null) {
                sender.sendMessage(messages.messageFor(MessageKeys.PARTY_TYPE_FLAG_WITHOUT_NAME,
                        Placeholder.unparsed("usage", "/realty list" + (category != null ? " " + category : "")
                                + " <name> --" + flag.name().toLowerCase(Locale.ROOT))));
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_PLAYERS_ONLY));
                return;
            }
            listRegions(sender, new Target(new Party.Personal(player.getUniqueId()), player.getName(),
                    null, null), category, page);
            return;
        }
        PartyFlags.resolveThen(partyResolver, executorState, messages, sender, name, flag, party ->
                listRegions(sender, new Target(party, null, name, flag), category, page));
    }

    /**
     * A player by name; an account or a group in its display form, such as {@code Mint (system)}.
     * Looking that up may ask Treasury, so call it only off the main thread.
     */
    private @NotNull String shownName(@NotNull Target target) {
        return target.playerName() != null ? target.playerName() : partyNames.display(target.party());
    }

    private void listRegions(@NotNull CommandSender sender, @NotNull Target target,
                             @Nullable String category, int page) {
        if (category == null) {
            listAll(sender, target, page);
        } else {
            listCategory(sender, target, category, page);
        }
    }

    private void listAll(@NotNull CommandSender sender, @NotNull Target target, int page) {
        int globalOffset = (page - 1) * PAGE_SIZE;
        // The callbacks look up names and leases, so they stay on the database executor.
        api.listRegions(target.party(), PAGE_SIZE, globalOffset).thenAcceptAsync(result -> {
            String shownName = shownName(target);
            int totalCount = result.totalCount();
            if (totalCount == 0) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_NO_REGIONS,
                        Placeholder.unparsed("player", shownName)));
                return;
            }

            int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > totalPages) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_INVALID_PAGE,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages))));
                return;
            }

            TextComponent.Builder builder = Component.text();
            builder.append(parseMiniMessage(MessageKeys.LIST_HEADER, "<player>", shownName));
            appendCategory(builder, "Owned", result.owned());
            appendCategory(builder, "Landlord", result.landlord());
            appendRentedCategory(builder, "Rented", result.rented());
            appendFooter(builder, target, null, page, totalPages);
            sender.sendMessage(builder.build());
        }, executorState.dbExec()).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.LIST_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    private void listCategory(@NotNull CommandSender sender, @NotNull Target target,
                              @NotNull String category, int page) {
        var future = "owned".equals(category)
                ? api.listOwnedRegions(target.party(), PAGE_SIZE, (page - 1) * PAGE_SIZE)
                : api.listRentedRegions(target.party(), PAGE_SIZE, (page - 1) * PAGE_SIZE);

        future.thenAcceptAsync(result -> {
            String shownName = shownName(target);
            if (result.totalCount() == 0) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_NO_REGIONS,
                        Placeholder.unparsed("player", shownName)));
                return;
            }

            int totalPages = (result.totalCount() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > totalPages) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_INVALID_PAGE,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages))));
                return;
            }

            String label = "owned".equals(category) ? "Owned" : "Rented";
            TextComponent.Builder builder = Component.text();
            builder.append(parseMiniMessage(MessageKeys.LIST_HEADER, "<player>", shownName));
            if ("owned".equals(category)) {
                appendCategory(builder, label, result.regions());
            } else {
                appendRentedCategory(builder, label, result.regions());
            }
            appendFooter(builder, target, category, page, totalPages);
            sender.sendMessage(builder.build());
        }, executorState.dbExec()).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.LIST_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    private void appendCategory(@NotNull TextComponent.Builder builder, @NotNull String label,
                                @NotNull List<RealtyRegionEntity> regions) {
        if (regions.isEmpty()) {
            return;
        }
        builder.appendNewline()
                .append(parseMiniMessage(MessageKeys.LIST_CATEGORY, "<label>", label));
        for (RealtyRegionEntity region : regions) {
            builder.appendNewline()
                    .append(parseMiniMessage(MessageKeys.LIST_ENTRY,
                            "<region>",
                            region.worldGuardRegionId()));
        }
    }

    /**
     * Appends rented regions with time-left info. Calls
     * {@link RealtyPaperApi#getLeaseholdContract} via {@code .join()} for each region.
     * This is safe because the callback runs on the db executor thread.
     */
    private void appendRentedCategory(@NotNull TextComponent.Builder builder, @NotNull String label,
                                      @NotNull List<RealtyRegionEntity> regions) {
        if (regions.isEmpty()) {
            return;
        }
        builder.appendNewline()
                .append(parseMiniMessage(MessageKeys.LIST_CATEGORY, "<label>", label));
        for (RealtyRegionEntity region : regions) {
            LeaseholdContractEntity leasehold = api.getLeaseholdContract(
                    region.worldGuardRegionId(), region.worldId()).join();
            String timeLeft = DurationFormatter.formatTimeLeft(leasehold != null ? leasehold.endDate() : null);
            builder.appendNewline()
                    .append(parseMiniMessage(
                            MessageKeys.LIST_RENTED_ENTRY,
                            "<region>", region.worldGuardRegionId(),
                            "<time_left>", timeLeft
                    ));
        }
    }

    private void appendFooter(@NotNull TextComponent.Builder builder, @NotNull Target target,
                              @Nullable String category, int page, int totalPages) {
        Component previousComponent = page > 1
                ? buildNavComponent(MessageKeys.LIST_PREVIOUS, target, category, page - 1)
                : Component.empty();
        Component nextComponent = page < totalPages
                ? buildNavComponent(MessageKeys.LIST_NEXT, target, category, page + 1)
                : Component.empty();
        builder.appendNewline()
                .append(messages.messageFor(MessageKeys.LIST_FOOTER,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages)),
                        Placeholder.component("previous", previousComponent),
                        Placeholder.component("next", nextComponent)));
    }

    /** A page link repeats the name and the type flag it was given; flags follow the name. */
    private @NotNull Component buildNavComponent(@NotNull String key, @NotNull Target target,
                                                 @Nullable String category, int targetPage) {
        StringBuilder command = new StringBuilder("/realty list");
        if (category != null) {
            command.append(' ').append(category);
        }
        if (target.name() != null) {
            command.append(' ').append(target.name());
        }
        command.append(" --page ").append(targetPage);
        if (target.flag() != null) {
            command.append(" --").append(target.flag().name().toLowerCase(Locale.ROOT));
        }
        return parseMiniMessage(key,
                "<command>", command.toString());
    }

    private @NotNull Component parseMiniMessage(@NotNull String key,
                                                @NotNull String... replacements) {
        String raw = messages.miniMessageFormattedFor(key);
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return messages.deserializeRaw(raw);
    }

}
