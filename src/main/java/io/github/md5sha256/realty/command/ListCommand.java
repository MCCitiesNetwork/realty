package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
        @NotNull Database database
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    private static final int PAGE_SIZE = 10;

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("realty.command.list"))
                .executes(ctx -> executeSelf(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeSelf(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeOther(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeOther(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private int executeSelf(@NotNull CommandContext<CommandSourceStack> ctx, int page) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command without specifying a player.");
            return Command.SINGLE_SUCCESS;
        }
        listRegions(sender, player.getUniqueId(), player.getName(), page);
        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(@NotNull CommandContext<CommandSourceStack> ctx, int page) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = ctx.getArgument("player", String.class);
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("Player " + playerName + " has never played on this server.");
            return Command.SINGLE_SUCCESS;
        }
        listRegions(sender, target.getUniqueId(), target.getName() != null ? target.getName() : playerName, page);
        return Command.SINGLE_SUCCESS;
    }

    private void listRegions(@NotNull CommandSender sender, @NotNull UUID targetId,
                             @NotNull String targetName, int page) {
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();

                int ownedCount = regionMapper.countRegionsByTitleHolder(targetId);
                int landlordCount = regionMapper.countRegionsByAuthority(targetId);
                int rentedCount = regionMapper.countRegionsByTenant(targetId);
                int totalCount = ownedCount + landlordCount + rentedCount;

                if (totalCount == 0) {
                    sender.sendMessage("No regions found for " + targetName + ".");
                    return;
                }

                int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
                if (page > totalPages) {
                    sender.sendMessage("Page " + page + " does not exist. There are " + totalPages + " page(s).");
                    return;
                }

                int globalOffset = (page - 1) * PAGE_SIZE;
                int remaining = PAGE_SIZE;

                StringBuilder sb = new StringBuilder();
                sb.append("--- Regions for ").append(targetName).append(" ---");

                // Walk through categories in order, advancing the global offset across them
                int catOffset = globalOffset;

                remaining = appendCategory(sb, "Owned",
                        regionMapper.selectRegionsByTitleHolder(targetId, remaining, catOffset), remaining);
                catOffset = Math.max(0, catOffset - ownedCount);

                if (remaining > 0) {
                    remaining = appendCategory(sb, "Landlord",
                            regionMapper.selectRegionsByAuthority(targetId, remaining, catOffset), remaining);
                    catOffset = Math.max(0, catOffset - landlordCount);
                }

                if (remaining > 0) {
                    appendCategory(sb, "Rented",
                            regionMapper.selectRegionsByTenant(targetId, remaining, catOffset), remaining);
                }

                sb.append("\nPage ").append(page).append(" of ").append(totalPages);
                sb.append(" — /realty list ").append(targetName).append(" <page>");
                sender.sendMessage(sb.toString());
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to list regions: " + ex.getMessage());
            }
        }, executorState.dbExec());
    }

    private static int appendCategory(@NotNull StringBuilder sb, @NotNull String label,
                                       @NotNull List<RealtyRegionEntity> regions, int remaining) {
        if (regions.isEmpty()) {
            return remaining;
        }
        sb.append("\n").append(label).append(":");
        for (RealtyRegionEntity region : regions) {
            sb.append("\n  - ").append(region.worldGuardRegionId());
        }
        return remaining - regions.size();
    }

}
