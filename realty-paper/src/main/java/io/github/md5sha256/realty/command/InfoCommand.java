package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles {@code /realty info [region]}.
 *
 * <p>When the region argument is omitted, falls back to the WorldGuard region
 * at the player's current location.</p>
 *
 * <p>Permission: {@code realty.command.info}.</p>
 */
public record InfoCommand(@NotNull ExecutorState executorState,
                           @NotNull RealtyLogicImpl logic,
                           @NotNull Settings settings,
                           @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("info")
                .permission("realty.command.info")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RegionInfo info = logic.getRegionInfo(regionId, worldId);

                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor("info.header",
                        Placeholder.unparsed("region", regionId)));

                SaleContractEntity sale = info.sale();
                LeaseContractEntity lease = info.lease();
                SaleContractAuctionEntity auction = info.auction();

                if (sale == null && lease == null && auction == null) {
                    builder.appendNewline()
                            .append(messages.messageFor("info.no-contracts"));
                    sender.sendMessage(builder.build());
                    return;
                }

                if (sale != null) {
                    appendSaleInfo(builder, sale, region);
                }

                if (lease != null) {
                    appendLeaseInfo(builder, lease);
                }

                if (auction != null) {
                    appendAuctionInfo(builder, auction);
                }

                sender.sendMessage(builder.build());
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor("info.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void appendSaleInfo(@NotNull TextComponent.Builder builder,
                                @NotNull SaleContractEntity sale,
                                @NotNull WorldGuardRegion region) {
        builder.appendNewline()
                .append(messages.messageFor("info.sale-title-holder",
                        Placeholder.unparsed("title_holder", sale.titleHolderId() != null ? resolveName(sale.titleHolderId()) : "N/A")));

        Set<UUID> memberUuids = region.region().getMembers().getUniqueIds();
        Set<String> memberGroups = region.region().getMembers().getGroups();
        if (!memberUuids.isEmpty() || !memberGroups.isEmpty()) {
            String members = memberUuids.stream()
                    .map(InfoCommand::resolveName)
                    .collect(Collectors.joining(", "));
            String groups = memberGroups.stream()
                    .map(g -> "g:" + g)
                    .collect(Collectors.joining(", "));
            String combined;
            if (!members.isEmpty() && !groups.isEmpty()) {
                combined = members + ", " + groups;
            } else if (!members.isEmpty()) {
                combined = members;
            } else {
                combined = groups;
            }
            builder.appendNewline()
                    .append(messages.messageFor("info.members",
                            Placeholder.unparsed("members", combined)));
        }

        builder.appendNewline()
                .append(messages.messageFor("info.sale-authority",
                        Placeholder.unparsed("authority", resolveName(sale.authorityId()))));
        if (sale.price() != null) {
            builder.appendNewline()
                    .append(messages.messageFor("info.sale-price",
                            Placeholder.unparsed("price", String.valueOf(sale.price()))));
        }
    }

    private void appendLeaseInfo(@NotNull TextComponent.Builder builder,
                                 @NotNull LeaseContractEntity lease) {
        builder.appendNewline()
                .append(messages.messageFor("info.lease-landlord",
                        Placeholder.unparsed("landlord", resolveName(lease.landlordId()))))
                .appendNewline()
                .append(messages.messageFor("info.lease-tenant",
                        Placeholder.unparsed("tenant", lease.tenantId() != null ? resolveName(lease.tenantId()) : "N/A")))
                .appendNewline()
                .append(messages.messageFor("info.lease-price",
                        Placeholder.unparsed("price", String.valueOf(lease.price()))))
                .appendNewline()
                .append(messages.messageFor("info.lease-duration",
                        Placeholder.unparsed("duration", formatDuration(Duration.ofSeconds(lease.durationSeconds())))))
                .appendNewline()
                .append(messages.messageFor("info.lease-start-date",
                        Placeholder.unparsed("start_date", formatDate(lease.startDate()))));
        if (lease.maxExtensions() != null) {
            builder.appendNewline()
                    .append(messages.messageFor("info.lease-extensions",
                            Placeholder.unparsed("current", String.valueOf(lease.currentMaxExtensions())),
                            Placeholder.unparsed("max", String.valueOf(lease.maxExtensions()))));
        } else {
            builder.appendNewline()
                    .append(messages.messageFor("info.lease-extensions-unlimited"));
        }
    }

    private void appendAuctionInfo(@NotNull TextComponent.Builder builder,
                                   @NotNull SaleContractAuctionEntity auction) {
        builder.appendNewline()
                .append(messages.messageFor("info.auction-header",
                        Placeholder.unparsed("id", String.valueOf(auction.saleContractAuctionId()))))
                .appendNewline()
                .append(messages.messageFor("info.auction-start-date",
                        Placeholder.unparsed("start_date", formatDate(auction.startDate()))))
                .appendNewline()
                .append(messages.messageFor("info.auction-bidding-duration",
                        Placeholder.unparsed("duration", formatDuration(Duration.ofSeconds(auction.biddingDurationSeconds())))))
                .appendNewline()
                .append(messages.messageFor("info.auction-payment-deadline",
                        Placeholder.unparsed("deadline", formatDate(auction.paymentDeadline()))))
                .appendNewline()
                .append(messages.messageFor("info.auction-min-bid",
                        Placeholder.unparsed("amount", String.valueOf(auction.minBid()))))
                .appendNewline()
                .append(messages.messageFor("info.auction-min-step",
                        Placeholder.unparsed("amount", String.valueOf(auction.minStep()))));
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private @NotNull String formatDate(@NotNull LocalDateTime dateTime) {
        DateFormat dateFormat = settings.dateFormat();
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        synchronized (dateFormat) {
            return dateFormat.format(date);
        }
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
