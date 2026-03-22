package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.DateFormatter;
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
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty history <region> [--event <type>] [--time <duration>] [--player <name>] [--page <n>]}.
 *
 * <p>Permission: {@code realty.command.history}.</p>
 */
public record HistoryCommand(@NotNull ExecutorState executorState,
                              @NotNull RealtyLogicImpl logic,
                              @NotNull AtomicReference<Settings> settings,
                              @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    private static final int PAGE_SIZE = 10;

    private static final Map<String, String> EVENT_TYPE_MESSAGE_KEYS = Map.of(
            "BUY", MessageKeys.HISTORY_EVENT_BUY,
            "AUCTION_BUY", MessageKeys.HISTORY_EVENT_AUCTION_BUY,
            "OFFER_BUY", MessageKeys.HISTORY_EVENT_OFFER_BUY,
            "AGENT_ADD", MessageKeys.HISTORY_EVENT_AGENT_ADD,
            "AGENT_REMOVE", MessageKeys.HISTORY_EVENT_AGENT_REMOVE,
            "RENT", MessageKeys.HISTORY_EVENT_RENT,
            "UNRENT", MessageKeys.HISTORY_EVENT_UNRENT,
            "LEASE_EXPIRY", MessageKeys.HISTORY_EVENT_LEASE_EXPIRY
    );

    private static final CloudKey<WorldGuardRegion> REGION = CloudKey.of("region",
            WorldGuardRegion.class);

    private static final CommandFlag<HistoryEventType> EVENT_FLAG =
            CommandFlag.<CommandSourceStack>builder("event")
                    .withComponent(EnumParser.enumParser(HistoryEventType.class))
                    .build();

    private static final CommandFlag<Duration> TIME_FLAG =
            CommandFlag.<CommandSourceStack>builder("time")
                    .withComponent(DurationParser.duration())
                    .build();

    private static final CommandFlag<UUID> PLAYER_FLAG =
            CommandFlag.<CommandSourceStack>builder("player")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<Integer> PAGE_FLAG =
            CommandFlag.<CommandSourceStack>builder("page")
                    .withComponent(IntegerParser.integerParser(1))
                    .build();

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("history")
                .permission("realty.command.history")
                .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                .flag(EVENT_FLAG)
                .flag(TIME_FLAG)
                .flag(PLAYER_FLAG)
                .flag(PAGE_FLAG)
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        WorldGuardRegion region = ctx.get(REGION);
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        HistoryEventType eventType = ctx.flags().getValue(EVENT_FLAG, null);
        Duration timeDuration = ctx.flags().getValue(TIME_FLAG, null);
        UUID playerId = ctx.flags().getValue(PLAYER_FLAG, null);
        int page = ctx.flags().getValue(PAGE_FLAG, 1);

        String eventTypeStr = eventType != null ? eventType.name() : null;
        LocalDateTime since = timeDuration != null ? LocalDateTime.now().minus(timeDuration) : null;
        int offset = (page - 1) * PAGE_SIZE;

        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.HistoryResult result = logic.searchHistory(
                        regionId, worldId, eventTypeStr, since, playerId, PAGE_SIZE, offset);

                int totalCount = result.totalCount();
                if (totalCount == 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.HISTORY_NO_RESULTS,
                            Placeholder.unparsed("region", regionId)));
                    return;
                }

                int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
                if (page > totalPages) {
                    sender.sendMessage(messages.messageFor(MessageKeys.HISTORY_INVALID_PAGE,
                            Placeholder.unparsed("page", String.valueOf(page)),
                            Placeholder.unparsed("total", String.valueOf(totalPages))));
                    return;
                }

                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor(MessageKeys.HISTORY_HEADER,
                        Placeholder.unparsed("region", regionId)));

                for (HistoryEntry entry : result.entries()) {
                    builder.appendNewline();
                    String messageKey = resolveEventMessageKey(entry.eventType());
                    switch (entry) {
                        case HistoryEntry.Freehold freehold -> builder.append(
                                messages.messageFor(messageKey,
                                        Placeholder.unparsed("time", DateFormatter.format(settings.get(), freehold.eventTime())),
                                        Placeholder.unparsed("buyer", resolveName(freehold.buyerId())),
                                        Placeholder.unparsed("authority", resolveName(freehold.authorityId())),
                                        Placeholder.unparsed("price", String.valueOf(freehold.price()))));
                        case HistoryEntry.Agent agent -> builder.append(
                                messages.messageFor(messageKey,
                                        Placeholder.unparsed("time", DateFormatter.format(settings.get(), agent.eventTime())),
                                        Placeholder.unparsed("agent", resolveName(agent.agentId())),
                                        Placeholder.unparsed("actor", resolveName(agent.actorId()))));
                        case HistoryEntry.Lease lease -> builder.append(
                                messages.messageFor(messageKey,
                                        Placeholder.unparsed("time", DateFormatter.format(settings.get(), lease.eventTime())),
                                        Placeholder.unparsed("tenant", resolveName(lease.tenantId())),
                                        Placeholder.unparsed("landlord", resolveName(lease.landlordId())),
                                        Placeholder.unparsed("price",
                                                lease.price() != null ? String.valueOf(lease.price()) : "N/A")));
                    }
                }

                appendFooter(builder, regionId, eventType, timeDuration, playerId, page, totalPages);
                sender.sendMessage(builder.build());
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.HISTORY_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void appendFooter(@NotNull TextComponent.Builder builder, @NotNull String regionId,
                               @Nullable HistoryEventType eventType, @Nullable Duration timeDuration,
                               @Nullable UUID playerId, int page, int totalPages) {
        Component previousComponent = page > 1
                ? buildNavComponent(MessageKeys.HISTORY_PREVIOUS, regionId, eventType, timeDuration, playerId, page - 1)
                : Component.empty();
        Component nextComponent = page < totalPages
                ? buildNavComponent(MessageKeys.HISTORY_NEXT, regionId, eventType, timeDuration, playerId, page + 1)
                : Component.empty();
        builder.appendNewline()
                .append(messages.messageFor(MessageKeys.HISTORY_FOOTER,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages)),
                        Placeholder.component("previous", previousComponent),
                        Placeholder.component("next", nextComponent)));
    }

    private @NotNull Component buildNavComponent(@NotNull String key, @NotNull String regionId,
                                                  @Nullable HistoryEventType eventType,
                                                  @Nullable Duration timeDuration,
                                                  @Nullable UUID playerId, int targetPage) {
        StringBuilder command = new StringBuilder("/realty history ").append(regionId);
        if (eventType != null) {
            command.append(" --event ").append(eventType.name());
        }
        if (timeDuration != null) {
            command.append(" --time ").append(DurationFormatter.formatCompact(timeDuration));
        }
        if (playerId != null) {
            String name = resolveName(playerId);
            command.append(" --player ").append(name);
        }
        command.append(" --page ").append(targetPage);
        String raw = messages.miniMessageFormattedFor(key);
        raw = raw.replace("<command>", command.toString());
        return messages.deserializeRaw(raw);
    }

    private static @NotNull String resolveEventMessageKey(@NotNull String eventType) {
        String key = EVENT_TYPE_MESSAGE_KEYS.get(eventType);
        return key != null ? key : eventType;
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

}
