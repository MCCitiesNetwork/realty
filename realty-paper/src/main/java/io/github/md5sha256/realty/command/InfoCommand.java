package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.DateFormatter;
import io.github.md5sha256.realty.localisation.MessageKeys;
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
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
                          @NotNull AtomicReference<Settings> settings,
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


    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
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
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String membersStr = resolveMembers(region);

        executorState.dbExec().execute(() -> {
            try {
                RealtyLogicImpl.RegionInfo info = logic.getRegionInfo(regionId, worldId);

                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor(MessageKeys.INFO_HEADER,
                        Placeholder.unparsed("region", regionId)));

                FreeholdContractEntity freehold = info.freehold();
                LeaseContractEntity lease = info.lease();
                boolean hasAuction = info.auction() != null;

                if (freehold == null && lease == null && !hasAuction) {
                    builder.appendNewline()
                            .append(messages.messageFor(MessageKeys.INFO_NO_CONTRACTS));
                    sender.sendMessage(builder.build());
                    return;
                }

                if (freehold != null) {
                    appendFreeholdInfo(builder, freehold, info.lastSoldPrice(), membersStr);
                    builder.appendNewline()
                            .append(messages.messageFor(MessageKeys.INFO_AUCTION_ACTIVE,
                                    Placeholder.unparsed("has_auction", hasAuction ? "Yes" : "No")));
                }

                if (lease != null) {
                    appendLeaseInfo(builder, lease, membersStr);
                }


                sender.sendMessage(builder.build());
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.INFO_ERROR,
                        Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            }
        });
    }

    private void appendFreeholdInfo(@NotNull TextComponent.Builder builder,
                                @NotNull FreeholdContractEntity freehold,
                                @Nullable Double lastSoldPrice,
                                @NotNull String membersStr) {
        String titleHolder = freehold.titleHolderId() != null ? resolveName(freehold.titleHolderId()) : "N/A";
        String authority = resolveName(freehold.authorityId());

        if (freehold.price() != null) {
            builder.appendNewline()
                    .append(messages.messageFor(MessageKeys.INFO_FOR_SALE,
                            Placeholder.unparsed("title_holder", titleHolder),
                            Placeholder.unparsed("authority", authority),
                            Placeholder.unparsed("price", String.valueOf(freehold.price()))));
        } else {
            String lastSold = lastSoldPrice != null ? String.valueOf(lastSoldPrice) : "N/A";
            builder.appendNewline()
                    .append(messages.messageFor(MessageKeys.INFO_SOLD,
                            Placeholder.unparsed("title_holder", titleHolder),
                            Placeholder.unparsed("members", membersStr),
                            Placeholder.unparsed("authority", authority),
                            Placeholder.unparsed("last_sold_price", lastSold)));
        }
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
                .append(messages.messageFor(MessageKeys.INFO_LEASE,
                        Placeholder.unparsed("landlord", resolveName(lease.landlordId())),
                        Placeholder.unparsed("members", membersStr),
                        Placeholder.unparsed("tenant", tenant),
                        Placeholder.unparsed("price", String.valueOf(lease.price())),
                        Placeholder.unparsed("duration",
                                DurationFormatter.format(Duration.ofSeconds(lease.durationSeconds()))),
                        Placeholder.unparsed("start_date", DateFormatter.format(settings.get(),lease.startDate())),
                        Placeholder.unparsed("end_date", DateFormatter.format(settings.get(),leaseEndDate)),
                        Placeholder.unparsed("extensions", extensions)));
    }

}
