package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty info <region>}.
 *
 * <p>Permission: {@code realty.command.info}.</p>
 */
public record InfoCommand(@NotNull ExecutorState executorState,
                           @NotNull RealtyLogicImpl logic,
                           @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("info")
                .permission("realty.command.info")
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RegionInfo info = logic.getRegionInfo(regionId, worldId);

                Component output = messages.messageFor("info.header",
                                Placeholder.unparsed("region", regionId))
                        .appendNewline()
                        .append(messages.messageFor("info.world",
                                Placeholder.unparsed("world", region.world().getName())));

                SaleContractEntity sale = info.sale();
                LeaseContractEntity lease = info.lease();
                SaleContractAuctionEntity auction = info.auction();

                if (sale == null && lease == null && auction == null) {
                    output = output.appendNewline()
                            .append(messages.messageFor("info.no-contracts"));
                    sender.sendMessage(output);
                    return;
                }

                if (sale != null) {
                    output = output.appendNewline().appendNewline()
                            .append(messages.messageFor("info.sale-header",
                                    Placeholder.unparsed("id", String.valueOf(sale.saleContractId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.sale-authority",
                                    Placeholder.unparsed("authority", String.valueOf(sale.authorityId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.sale-title-holder",
                                    Placeholder.unparsed("title_holder", String.valueOf(sale.titleHolderId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.sale-price",
                                    Placeholder.unparsed("price", String.valueOf(sale.price()))));
                }

                if (lease != null) {
                    output = output.appendNewline().appendNewline()
                            .append(messages.messageFor("info.lease-header",
                                    Placeholder.unparsed("id", String.valueOf(lease.leaseContractId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.lease-tenant",
                                    Placeholder.unparsed("tenant", String.valueOf(lease.tenantId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.lease-price",
                                    Placeholder.unparsed("price", String.valueOf(lease.price()))))
                            .appendNewline()
                            .append(messages.messageFor("info.lease-duration",
                                    Placeholder.unparsed("duration", formatDuration(Duration.ofSeconds(lease.durationSeconds())))))
                            .appendNewline()
                            .append(messages.messageFor("info.lease-start-date",
                                    Placeholder.unparsed("start_date", String.valueOf(lease.startDate()))));
                    if (lease.maxExtensions() != null) {
                        output = output.appendNewline()
                                .append(messages.messageFor("info.lease-extensions",
                                        Placeholder.unparsed("current", String.valueOf(lease.currentMaxExtensions())),
                                        Placeholder.unparsed("max", String.valueOf(lease.maxExtensions()))));
                    } else {
                        output = output.appendNewline()
                                .append(messages.messageFor("info.lease-extensions-unlimited"));
                    }
                }

                if (auction != null) {
                    output = output.appendNewline().appendNewline()
                            .append(messages.messageFor("info.auction-header",
                                    Placeholder.unparsed("id", String.valueOf(auction.saleContractAuctionId()))))
                            .appendNewline()
                            .append(messages.messageFor("info.auction-start-date",
                                    Placeholder.unparsed("start_date", String.valueOf(auction.startDate()))))
                            .appendNewline()
                            .append(messages.messageFor("info.auction-bidding-duration",
                                    Placeholder.unparsed("duration", formatDuration(Duration.ofSeconds(auction.biddingDurationSeconds())))))
                            .appendNewline()
                            .append(messages.messageFor("info.auction-payment-deadline",
                                    Placeholder.unparsed("deadline", String.valueOf(auction.paymentDeadline()))))
                            .appendNewline()
                            .append(messages.messageFor("info.auction-min-bid",
                                    Placeholder.unparsed("amount", String.valueOf(auction.minBid()))))
                            .appendNewline()
                            .append(messages.messageFor("info.auction-min-step",
                                    Placeholder.unparsed("amount", String.valueOf(auction.minStep()))));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor("info.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
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
