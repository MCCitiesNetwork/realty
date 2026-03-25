package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.command.util.NamedAuthority;
import io.github.md5sha256.realty.command.util.NamedAuthorityParser;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty list [owned|rented] [--page <n>] [--player <name>]}.
 *
 * <p>Permission: {@code realty.command.list}.</p>
 */
public record ListCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final int PAGE_SIZE = 10;

    private static final CommandFlag<NamedAuthority> PLAYER_FLAG =
            CommandFlag.<Source>builder("player")
                    .withComponent(NamedAuthorityParser.namedAuthority())
                    .build();

    private static final CommandFlag<Integer> PAGE_FLAG =
            CommandFlag.<Source>builder("page")
                    .withComponent(IntegerParser.integerParser(1))
                    .build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("list")
                .permission("realty.command.list")
                .flag(PLAYER_FLAG)
                .flag(PAGE_FLAG);
        var meProxy = builder.literal("me")
                .senderType(PlayerSource.class)
                .permission("realty.command.list")
                .flag(PAGE_FLAG)
                .handler(ctx -> {
                    var player = ctx.sender().source();
                    ctx.flags()
                            .addValueFlag(PLAYER_FLAG,
                                    new NamedAuthority(player.getUniqueId(), player.getName()));
                    execute(ctx, null);
                })
                .build();
        return List.of(
                meProxy,
                base.handler(ctx -> execute(ctx, null))
                        .build(),
                base.literal("owned")
                        .handler(ctx -> execute(ctx, "owned"))
                        .build(),
                base.literal("rented")
                        .handler(ctx -> execute(ctx, "rented"))
                        .build()
        );
    }

    private void execute(@NotNull CommandContext<? extends Source> ctx,
                         @Nullable String category) {
        CommandSender sender = ctx.sender().source();
        int page = ctx.flags().getValue(PAGE_FLAG, 1);
        NamedAuthority authority = ctx.flags().getValue(PLAYER_FLAG, null);
        if (authority != null) {
            resolvePlayer(sender, authority, category, page);
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_PLAYERS_ONLY));
                return;
            }
            listRegions(sender, player.getUniqueId(), player.getName(), category, page);
        }
    }

    private void resolvePlayer(@NotNull CommandSender sender, @NotNull NamedAuthority authority,
                               @Nullable String category, int page) {
        listRegions(sender, authority.uuid(), authority.name(), category, page);
    }

    private void listRegions(@NotNull CommandSender sender, @NotNull UUID targetId,
                             @NotNull String targetName, @Nullable String category, int page) {
        CompletableFuture.runAsync(() -> {
            try {
                if (category == null) {
                    listAll(sender, targetId, targetName, page);
                } else {
                    listCategory(sender, targetId, targetName, category, page);
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.LIST_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void listAll(@NotNull CommandSender sender, @NotNull UUID targetId,
                         @NotNull String targetName, int page) {
        int globalOffset = (page - 1) * PAGE_SIZE;
        RealtyApi.ListResult result = logic.listRegions(targetId, PAGE_SIZE, globalOffset);

        int totalCount = result.totalCount();
        if (totalCount == 0) {
            sender.sendMessage(messages.messageFor(MessageKeys.LIST_NO_REGIONS,
                    Placeholder.unparsed("player", targetName)));
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
        builder.append(parseMiniMessage(MessageKeys.LIST_HEADER, "<player>", targetName));
        appendCategory(builder, "Owned", result.owned());
        appendCategory(builder, "Landlord", result.landlord());
        appendRentedCategory(builder, "Rented", result.rented());
        appendFooter(builder, targetName, null, page, totalPages);
        sender.sendMessage(builder.build());
    }

    private void listCategory(@NotNull CommandSender sender, @NotNull UUID targetId,
                              @NotNull String targetName, @NotNull String category, int page) {
        RealtyApi.SingleCategoryResult result = "owned".equals(category)
                ? logic.listOwnedRegions(targetId, PAGE_SIZE, (page - 1) * PAGE_SIZE)
                : logic.listRentedRegions(targetId, PAGE_SIZE, (page - 1) * PAGE_SIZE);

        if (result.totalCount() == 0) {
            sender.sendMessage(messages.messageFor(MessageKeys.LIST_NO_REGIONS,
                    Placeholder.unparsed("player", targetName)));
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
        builder.append(parseMiniMessage(MessageKeys.LIST_HEADER, "<player>", targetName));
        if ("owned".equals(category)) {
            appendCategory(builder, label, result.regions());
        } else {
            appendRentedCategory(builder, label, result.regions());
        }
        appendFooter(builder, targetName, category, page, totalPages);
        sender.sendMessage(builder.build());
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

    private void appendRentedCategory(@NotNull TextComponent.Builder builder, @NotNull String label,
                                      @NotNull List<RealtyRegionEntity> regions) {
        if (regions.isEmpty()) {
            return;
        }
        builder.appendNewline()
                .append(parseMiniMessage(MessageKeys.LIST_CATEGORY, "<label>", label));
        for (RealtyRegionEntity region : regions) {
            LeaseholdContractEntity leasehold = logic.getLeaseholdContract(region.worldGuardRegionId(), region.worldId());
            String timeLeft = DurationFormatter.formatTimeLeft(leasehold != null ? leasehold.endDate() : null);
            builder.appendNewline()
                    .append(parseMiniMessage(
                            MessageKeys.LIST_RENTED_ENTRY,
                            "<region>", region.worldGuardRegionId(),
                            "<time_left>", timeLeft
                    ));
        }
    }

    private void appendFooter(@NotNull TextComponent.Builder builder, @NotNull String targetName,
                              @Nullable String category, int page, int totalPages) {
        Component previousComponent = page > 1
                ? buildNavComponent(MessageKeys.LIST_PREVIOUS, targetName, category, page - 1)
                : Component.empty();
        Component nextComponent = page < totalPages
                ? buildNavComponent(MessageKeys.LIST_NEXT, targetName, category, page + 1)
                : Component.empty();
        builder.appendNewline()
                .append(messages.messageFor(MessageKeys.LIST_FOOTER,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages)),
                        Placeholder.component("previous", previousComponent),
                        Placeholder.component("next", nextComponent)));
    }

    private @NotNull Component buildNavComponent(@NotNull String key, @NotNull String playerName,
                                                 @Nullable String category, int targetPage) {
        StringBuilder command = new StringBuilder("/realty list");
        if (category != null) {
            command.append(' ').append(category);
        }
        command.append(" --page ").append(targetPage);
        command.append(" --player ").append(playerName);
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
