package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty list [player] [page]}.
 *
 * <p>Permission: {@code realty.command.list}.</p>
 */
public record ListCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final int PAGE_SIZE = 10;

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("list")
                .permission("realty.command.list");
        return List.of(
                base.handler(ctx -> executeSelf(ctx, 1)).build(),
                base.required("playerOrPage", StringParser.stringParser())
                    .optional("page", IntegerParser.integerParser(1))
                    .handler(this::executeList).build()
        );
    }

    private void executeList(@NotNull CommandContext<CommandSourceStack> ctx) {
        String firstArg = ctx.get("playerOrPage");
        Integer page = ctx.getOrDefault("page", null);
        try {
            int pageNum = Integer.parseInt(firstArg);
            if (pageNum < 1) {
                pageNum = 1;
            }
            executeSelf(ctx, pageNum);
        } catch (NumberFormatException e) {
            executeOther(ctx, firstArg, page != null ? page : 1);
        }
    }

    private void executeSelf(@NotNull CommandContext<CommandSourceStack> ctx, int page) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor("list.players-only"));
            return;
        }
        listRegions(sender, player.getUniqueId(), player.getName(), page);
    }

    private void executeOther(@NotNull CommandContext<CommandSourceStack> ctx,
                              @NotNull String playerName, int page) {
        CommandSender sender = ctx.sender().getSender();
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        listRegions(sender, target.getUniqueId(), target.getName() != null ? target.getName() : playerName, page);
    }

    private void listRegions(@NotNull CommandSender sender, @NotNull UUID targetId,
                             @NotNull String targetName, int page) {
        CompletableFuture.runAsync(() -> {
            try {
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

                Component output = messages.messageFor("list.header",
                        Placeholder.unparsed("player", targetName));

                output = appendCategory(output, "Owned", result.owned());
                output = appendCategory(output, "Landlord", result.landlord());
                output = appendCategory(output, "Rented", result.rented());

                output = output.appendNewline()
                        .append(messages.messageFor("list.footer",
                                Placeholder.unparsed("page", String.valueOf(page)),
                                Placeholder.unparsed("total", String.valueOf(totalPages)),
                                Placeholder.unparsed("player", targetName)));
                sender.sendMessage(output);
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("list.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private @NotNull Component appendCategory(@NotNull Component output, @NotNull String label,
                                               @NotNull List<RealtyRegionEntity> regions) {
        if (regions.isEmpty()) {
            return output;
        }
        output = output.appendNewline()
                .append(messages.messageFor("list.category",
                        Placeholder.unparsed("label", label)));
        for (RealtyRegionEntity region : regions) {
            output = output.appendNewline()
                    .append(messages.messageFor("list.entry",
                            Placeholder.unparsed("region", region.worldGuardRegionId())));
        }
        return output;
    }

}
