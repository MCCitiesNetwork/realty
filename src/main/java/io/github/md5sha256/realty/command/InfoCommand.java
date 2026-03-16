package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty info [region]}.
 *
 * <p>Permission: {@code realty.command.info}.</p>
 */
public record InfoCommand(@NotNull ExecutorState executorState,
                           @NotNull RealtyLogicImpl logic) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("info")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.info"))
                .then(Commands.argument("region", new WorldGuardRegionArgument())
                        .executes(this::execute));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        CommandSender sender = ctx.getSource().getSender();
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RegionInfo info = logic.getRegionInfo(regionId, worldId);

                StringBuilder sb = new StringBuilder();
                sb.append("--- Region Info: ").append(regionId).append(" ---\n");
                sb.append("World: ").append(region.world().getName()).append("\n");

                SaleContractEntity sale = info.sale();
                LeaseContractEntity lease = info.lease();
                SaleContractAuctionEntity auction = info.auction();

                if (sale == null && lease == null && auction == null) {
                    sb.append("No contracts or auctions found for this region.");
                    sender.sendMessage(sb.toString());
                    return;
                }

                if (sale != null) {
                    sb.append("\nSale Contract #").append(sale.saleContractId()).append(":\n");
                    sb.append("  Authority: ").append(sale.authorityId()).append("\n");
                    sb.append("  Title Holder: ").append(sale.titleHolderId()).append("\n");
                    sb.append("  Price: ").append(sale.price()).append("\n");
                }

                if (lease != null) {
                    sb.append("\nLease Contract #").append(lease.leaseContractId()).append(":\n");
                    sb.append("  Tenant: ").append(lease.tenantId()).append("\n");
                    sb.append("  Price: ").append(lease.price()).append("\n");
                    sb.append("  Duration: ").append(formatDuration(Duration.ofSeconds(lease.durationSeconds()))).append("\n");
                    sb.append("  Start Date: ").append(lease.startDate()).append("\n");
                    if (lease.maxExtensions() != null) {
                        sb.append("  Extensions: ").append(lease.currentMaxExtensions()).append("/").append(lease.maxExtensions()).append("\n");
                    } else {
                        sb.append("  Extensions: unlimited\n");
                    }
                }

                if (auction != null) {
                    sb.append("\nActive Auction #").append(auction.saleContractAuctionId()).append(":\n");
                    sb.append("  Start Date: ").append(auction.startDate()).append("\n");
                    sb.append("  Bidding Duration: ").append(formatDuration(Duration.ofSeconds(auction.biddingDurationSeconds()))).append("\n");
                    sb.append("  Payment Deadline: ").append(auction.paymentDeadline()).append("\n");
                    sb.append("  Min Bid: ").append(auction.minBid()).append("\n");
                    sb.append("  Min Step: ").append(auction.minStep()).append("\n");
                }

                sender.sendMessage(sb.toString());
            } catch (PersistenceException ex) {
                ex.printStackTrace();
                sender.sendMessage("Failed to retrieve region info: " + ex.getMessage());
            }
        }, executorState.dbExec());

        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull String formatDuration(@NotNull Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

}
