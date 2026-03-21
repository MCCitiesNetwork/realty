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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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

    private static @NotNull String resolveMembers(@NotNull WorldGuardRegion region) {
        Set<UUID> memberUuids = region.region().getMembers().getUniqueIds();
        Set<String> memberGroups = region.region().getMembers().getGroups();
        if (memberUuids.isEmpty() && memberGroups.isEmpty()) {
            return "None";
        }
        String members = memberUuids.stream()
                .map(InfoCommand::resolveName)
                .collect(Collectors.joining(", "));
        String groups = memberGroups.stream()
                .map(g -> "g:" + g)
                .collect(Collectors.joining(", "));
        if (!members.isEmpty() && !groups.isEmpty()) {
            return members + ", " + groups;
        } else if (!members.isEmpty()) {
            return members;
        } else {
            return groups;
        }
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
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
        String membersStr = resolveMembers(region);

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
                    appendSaleInfo(builder, sale, membersStr);
                }

                if (lease != null) {
                    appendLeaseInfo(builder, lease, membersStr);
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
                                @NotNull String membersStr) {
        String titleHolder = sale.titleHolderId() != null ? resolveName(sale.titleHolderId()) : "N/A";
        String authority = resolveName(sale.authorityId());
        String price = sale.price() != null ? String.valueOf(sale.price()) : "N/A";

        builder.appendNewline()
                .append(messages.messageFor("info.sale",
                        Placeholder.unparsed("title_holder", titleHolder),
                        Placeholder.unparsed("members", membersStr),
                        Placeholder.unparsed("authority", authority),
                        Placeholder.unparsed("price", price)));
    }

    private void appendLeaseInfo(@NotNull TextComponent.Builder builder,
                                 @NotNull LeaseContractEntity lease,
                                 @NotNull String membersStr) {
        String tenant = lease.tenantId() != null ? resolveName(lease.tenantId()) : "N/A";
        String extensions;
        if (lease.maxExtensions() != null) {
            extensions = lease.currentMaxExtensions() + "/" + lease.maxExtensions();
        } else {
            extensions = "unlimited";
        }

        LocalDateTime leaseEndDate = lease.startDate().plusSeconds(lease.durationSeconds());

        builder.appendNewline()
                .append(messages.messageFor("info.lease",
                        Placeholder.unparsed("landlord", resolveName(lease.landlordId())),
                        Placeholder.unparsed("members", membersStr),
                        Placeholder.unparsed("tenant", tenant),
                        Placeholder.unparsed("price", String.valueOf(lease.price())),
                        Placeholder.unparsed("duration",
                                formatDuration(Duration.ofSeconds(lease.durationSeconds()))),
                        Placeholder.unparsed("start_date", formatDate(lease.startDate())),
                        Placeholder.unparsed("end_date", formatDate(leaseEndDate)),
                        Placeholder.unparsed("extensions", extensions)));
    }

    private void appendAuctionInfo(@NotNull TextComponent.Builder builder,
                                   @NotNull SaleContractAuctionEntity auction) {
        LocalDateTime biddingEndDate = auction.startDate().plusSeconds(auction.biddingDurationSeconds());

        builder.appendNewline()
                .append(messages.messageFor("info.auction",
                        Placeholder.unparsed("auctioneer", resolveName(auction.auctioneerId())),
                        Placeholder.unparsed("start_date", formatDate(auction.startDate())),
                        Placeholder.unparsed("duration",
                                formatDuration(Duration.ofSeconds(auction.biddingDurationSeconds()))),
                        Placeholder.unparsed("bidding_end_date", formatDate(biddingEndDate)),
                        Placeholder.unparsed("deadline", formatDate(auction.paymentDeadline())),
                        Placeholder.unparsed("min_bid", String.valueOf(auction.minBid())),
                        Placeholder.unparsed("min_step", String.valueOf(auction.minStep()))));
    }

    private @NotNull String formatDate(@NotNull LocalDateTime dateTime) {
        DateFormat dateFormat = settings.dateFormat();
        Date date = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        return dateFormat.format(date);
    }

}
