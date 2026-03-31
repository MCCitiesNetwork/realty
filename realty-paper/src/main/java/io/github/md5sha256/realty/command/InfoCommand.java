package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.DateFormatter;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ExecutorState;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.paper.util.sender.Source;
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
                          @NotNull RealtyApi logic,
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
    public @NotNull Command<Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("info")
                .permission("realty.command.info")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String membersStr = resolveMembers(region);

        executorState.dbExec().execute(() -> {
            try {
                RealtyApi.RegionInfo info = logic.getRegionInfo(regionId, worldId);

                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor(MessageKeys.INFO_HEADER,
                        Placeholder.unparsed("region", regionId)));

                FreeholdContractEntity freehold = info.freehold();
                LeaseholdContractEntity leasehold = info.leasehold();
                boolean hasAuction = info.auction() != null;

                if (freehold == null && leasehold == null && !hasAuction) {
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

                if (leasehold != null) {
                    appendLeaseholdInfo(builder, leasehold, membersStr);
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
                            Placeholder.unparsed("price", CurrencyFormatter.format(freehold.price()))));
        } else {
            String lastSold = lastSoldPrice != null ? CurrencyFormatter.format(lastSoldPrice) : "N/A";
            builder.appendNewline()
                    .append(messages.messageFor(MessageKeys.INFO_SOLD,
                            Placeholder.unparsed("title_holder", titleHolder),
                            Placeholder.unparsed("members", membersStr),
                            Placeholder.unparsed("authority", authority),
                            Placeholder.unparsed("last_sold_price", lastSold)));
        }
    }

    private void appendLeaseholdInfo(@NotNull TextComponent.Builder builder,
                                     @NotNull LeaseholdContractEntity leasehold,
                                     @NotNull String membersStr) {
        String tenant = leasehold.tenantId() != null ? resolveName(leasehold.tenantId()) : "N/A";
        String extensions;
        if (leasehold.maxExtensions() != null) {
            extensions = leasehold.currentMaxExtensions() + "/" + leasehold.maxExtensions();
        } else {
            extensions = "unlimited";
        }

        builder.appendNewline()
                .append(messages.messageFor(MessageKeys.INFO_LEASEHOLD,
                        Placeholder.unparsed("landlord", resolveName(leasehold.landlordId())),
                        Placeholder.unparsed("members", membersStr),
                        Placeholder.unparsed("tenant", tenant),
                        Placeholder.unparsed("price", CurrencyFormatter.format(leasehold.price())),
                        Placeholder.unparsed("duration",
                                DurationFormatter.format(Duration.ofSeconds(leasehold.durationSeconds()))),
                        Placeholder.unparsed("start_date", leasehold.startDate() != null
                                ? DateFormatter.format(settings.get(), leasehold.startDate())
                                : "N/A"),
                        Placeholder.unparsed("end_date", leasehold.endDate() != null
                                ? DateFormatter.format(settings.get(), leasehold.endDate())
                                : "N/A"),
                        Placeholder.unparsed("extensions", extensions)));
    }

}
