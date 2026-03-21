package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
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
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final int PAGE_SIZE = 10;

    private static final CommandFlag<String> PLAYER_FLAG =
            CommandFlag.<CommandSourceStack>builder("player")
                    .withComponent(StringParser.stringParser())
                    .build();

    private static final CommandFlag<Integer> PAGE_FLAG =
            CommandFlag.<CommandSourceStack>builder("page")
                    .withComponent(IntegerParser.integerParser(1))
                    .build();

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("list")
                .permission("realty.command.list")
                .flag(PLAYER_FLAG)
                .flag(PAGE_FLAG);
        return List.of(
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

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx,
                         @Nullable String category) {
        CommandSender sender = ctx.sender().getSender();
        int page = ctx.flags().getValue(PAGE_FLAG, 1);
        String playerName = ctx.flags().getValue(PLAYER_FLAG, null);
        if (playerName != null) {
            resolvePlayer(sender, playerName, category, page);
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.messageFor("list.players-only"));
                return;
            }
            listRegions(sender, player.getUniqueId(), player.getName(), category, page);
        }
    }

    private void resolvePlayer(@NotNull CommandSender sender, @NotNull String playerName,
                                @Nullable String category, int page) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String name = target.getName() != null ? target.getName() : playerName;
        listRegions(sender, target.getUniqueId(), name, category, page);
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
                sender.sendMessage(messages.messageFor("list.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void listAll(@NotNull CommandSender sender, @NotNull UUID targetId,
                         @NotNull String targetName, int page) {
        int globalOffset = (page - 1) * PAGE_SIZE;
        RealtyLogicImpl.ListResult result = logic.listRegions(targetId, PAGE_SIZE, globalOffset);

        int totalCount = result.totalCount();
        if (totalCount == 0) {
            sender.sendMessage(messages.messageFor("list.no-regions",
                    Placeholder.unparsed("player", targetName)));
            return;
        }

        int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > totalPages) {
            sender.sendMessage(messages.messageFor("list.invalid-page",
                    Placeholder.unparsed("page", String.valueOf(page)),
                    Placeholder.unparsed("total", String.valueOf(totalPages))));
            return;
        }

        TextComponent.Builder builder = Component.text();
        builder.append(parseMiniMessage("list.header", "<player>", targetName));
        appendCategory(builder, "Owned", result.owned());
        appendCategory(builder, "Landlord", result.landlord());
        appendCategory(builder, "Rented", result.rented());
        appendFooter(builder, targetName, null, page, totalPages);
        sender.sendMessage(builder.build());
    }

    private void listCategory(@NotNull CommandSender sender, @NotNull UUID targetId,
                               @NotNull String targetName, @NotNull String category, int page) {
        RealtyLogicImpl.SingleCategoryResult result = "owned".equals(category)
                ? logic.listOwnedRegions(targetId, PAGE_SIZE, (page - 1) * PAGE_SIZE)
                : logic.listRentedRegions(targetId, PAGE_SIZE, (page - 1) * PAGE_SIZE);

        if (result.totalCount() == 0) {
            sender.sendMessage(messages.messageFor("list.no-regions",
                    Placeholder.unparsed("player", targetName)));
            return;
        }

        int totalPages = (result.totalCount() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > totalPages) {
            sender.sendMessage(messages.messageFor("list.invalid-page",
                    Placeholder.unparsed("page", String.valueOf(page)),
                    Placeholder.unparsed("total", String.valueOf(totalPages))));
            return;
        }

        String label = "owned".equals(category) ? "Owned" : "Rented";
        TextComponent.Builder builder = Component.text();
        builder.append(parseMiniMessage("list.header", "<player>", targetName));
        appendCategory(builder, label, result.regions());
        appendFooter(builder, targetName, category, page, totalPages);
        sender.sendMessage(builder.build());
    }

    private void appendCategory(@NotNull TextComponent.Builder builder, @NotNull String label,
                                @NotNull List<RealtyRegionEntity> regions) {
        if (regions.isEmpty()) {
            return;
        }
        builder.appendNewline()
                .append(parseMiniMessage("list.category", "<label>", label));
        for (RealtyRegionEntity region : regions) {
            builder.appendNewline()
                    .append(parseMiniMessage("list.entry", "<region>", region.worldGuardRegionId()));
        }
    }

    private void appendFooter(@NotNull TextComponent.Builder builder, @NotNull String targetName,
                               @Nullable String category, int page, int totalPages) {
        Component previousComponent = page > 1
                ? buildNavComponent("list.previous", targetName, category, page - 1)
                : Component.empty();
        Component nextComponent = page < totalPages
                ? buildNavComponent("list.next", targetName, category, page + 1)
                : Component.empty();
        builder.appendNewline()
                .append(messages.messageFor("list.footer",
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
        return MiniMessage.miniMessage().deserialize(raw);
    }

}
