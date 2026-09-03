package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.ActivityRow;
import io.github.md5sha256.realty.database.mapper.ActivityMapper;
import io.github.md5sha256.realty.rest.json.ActivityResponse;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /v1/activity} -- a server-wide feed of recent events, newest first.
 *
 * <p>Each event is individually obtainable in game through {@code /realty history},
 * which is granted by default and accepts any region. The sweep across regions is what
 * this adds, and is the reason the v1.x spec records it under aggregation rather than
 * treating it as plainly public.</p>
 */
final class ActivityHandler {

    /**
     * What the feed reports when the caller names no type: the events that read as a
     * ticker of sales and lettings. The full set is an audit trail, which is a
     * different thing and a poor default -- a bot polling for announcements would
     * otherwise have to filter out every {@code SET_PRICE} itself.
     */
    private static final List<String> DEFAULT_TYPES =
            List.of(HistoryEventType.BUY.name(), HistoryEventType.AUCTION_BUY.name(),
                    HistoryEventType.OFFER_BUY.name(), HistoryEventType.RENT.name());

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    ActivityHandler(@NotNull Database database,
                    @NotNull WorldLookup worldLookup,
                    @NotNull RestSettings settings,
                    @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        List<String> eventTypes = eventTypes(ctx);
        String worldParam = QueryParams.optional(ctx, "world");
        UUID worldId = worldParam == null ? null : this.worldLookup.resolve(worldParam);
        LocalDateTime since = since(ctx);

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<ActivityRow> rows;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            ActivityMapper mapper = session.activityMapper();
            totalCount = mapper.countMatching(eventTypes, worldId, since);
            rows = mapper.selectPage(eventTypes, worldId, since, pageSize, offset);
        }

        List<UUID> playerIds = new ArrayList<>();
        Set<UUID> worldIds = new HashSet<>();
        for (ActivityRow row : rows) {
            playerIds.add(row.firstPlayerId());
            playerIds.add(row.secondPlayerId());
            worldIds.add(row.worldId());
        }
        Map<UUID, String> names = PlayerNames.resolve(this.moduleClient, playerIds);
        Map<UUID, WorldRef> worlds = this.worldLookup.refsFor(worldIds);

        List<ActivityResponse.Event> events = new ArrayList<>(rows.size());
        for (ActivityRow row : rows) {
            events.add(toEvent(row, worlds, names));
        }

        ctx.json(new ActivityResponse(page, pageSize, totalCount,
                totalPages(totalCount, pageSize), events));
    }

    /**
     * {@code type} is repeatable, so a caller can ask for a few kinds of event without
     * either taking the default or naming all twenty-five.
     */
    private static @NotNull List<String> eventTypes(@NotNull Context ctx) {
        List<String> raw = QueryParams.values(ctx, "type");
        if (raw.isEmpty()) {
            return DEFAULT_TYPES;
        }
        List<String> types = new ArrayList<>(raw.size());
        for (String candidate : raw) {
            types.add(knownType(candidate));
        }
        return types;
    }

    private static @NotNull String knownType(@NotNull String raw) {
        String candidate = raw.trim().toUpperCase(Locale.ROOT);
        for (HistoryEventType known : HistoryEventType.values()) {
            if (known.name().equals(candidate)) {
                return known.name();
            }
        }
        throw ApiException.badRequest("INVALID_EVENT_TYPE",
                "Query parameter 'type' is not a known event type: '" + raw + "'");
    }

    private static @Nullable LocalDateTime since(@NotNull Context ctx) {
        String raw = QueryParams.optional(ctx, "since");
        if (raw == null) {
            return null;
        }
        try {
            return IsoDates.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_SINCE",
                    "Query parameter 'since' must be an ISO-8601 instant, for example 2026-08-01T00:00:00Z");
        }
    }

    /**
     * The row's two player columns are positional, so which pair they name is decided
     * by {@code kind} -- the same discriminator the response carries.
     */
    private static @NotNull ActivityResponse.Event toEvent(@NotNull ActivityRow row,
                                                           @NotNull Map<UUID, WorldRef> worlds,
                                                           @NotNull Map<UUID, String> names) {
        PlayerRef first = ref(row.firstPlayerId(), names);
        PlayerRef second = ref(row.secondPlayerId(), names);
        WorldRef world = worlds.get(row.worldId());
        String eventTime = IsoDates.format(row.eventTime());
        return switch (row.kind()) {
            case "freehold" -> new ActivityResponse.Event(
                    row.kind(), row.eventType(), eventTime, row.worldGuardRegionId(), world,
                    first, second, null, null, null, null, row.price(), null, null);
            case "leasehold" -> new ActivityResponse.Event(
                    row.kind(), row.eventType(), eventTime, row.worldGuardRegionId(), world,
                    null, null, first, second, null, null,
                    row.price(), row.durationSeconds(), row.extensionsRemaining());
            case "agent" -> new ActivityResponse.Event(
                    row.kind(), row.eventType(), eventTime, row.worldGuardRegionId(), world,
                    null, null, null, null, first, second, null, null, null);
            default -> throw new IllegalStateException(
                    "Unknown activity row kind: " + row.kind());
        };
    }

    private static @NotNull PlayerRef ref(@NotNull UUID id, @NotNull Map<UUID, String> names) {
        return Objects.requireNonNull(PlayerNames.ref(id, names));
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }
}
