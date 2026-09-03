package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.rest.json.HistoryResponse;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /v1/region/history?world=&region=} -- the HTTP form of
 * {@code /realty history}.
 *
 * <p>Also answers the questions that would otherwise want routes of their own: a
 * region's price history is {@code type=BUY}, and who has rented it before is
 * {@code type=RENT}.</p>
 */
final class RegionHistoryHandler {

    private final RealtyBackend backend;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    RegionHistoryHandler(@NotNull RealtyBackend backend,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings,
                         @NotNull ModuleClient moduleClient) {
        this.backend = backend;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        String worldParam = QueryParams.required(ctx, "world");
        String regionParam = QueryParams.required(ctx, "region");
        UUID worldId = this.worldLookup.resolve(worldParam);

        String eventType = eventType(ctx);
        LocalDateTime since = since(ctx);
        UUID playerId = playerFilter(ctx);

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        RealtyBackend.HistoryResult result = this.backend.searchHistory(
                regionParam, worldId, eventType, since, playerId, pageSize, offset);

        // Every identity on the page resolves in one module call rather than one per
        // entry, so a full page costs the same hop as a single row.
        List<UUID> ids = new ArrayList<>();
        for (HistoryEntry entry : result.entries()) {
            collectIds(entry, ids);
        }
        Map<UUID, String> names = PlayerNames.resolve(this.moduleClient, ids);

        List<HistoryResponse.Entry> entries = new ArrayList<>(result.entries().size());
        for (HistoryEntry entry : result.entries()) {
            entries.add(toEntry(entry, names));
        }

        ctx.json(new HistoryResponse(page, pageSize, result.totalCount(),
                totalPages(result.totalCount(), pageSize), entries));
    }

    /**
     * The command takes a relative duration; an absolute instant is the better HTTP
     * contract, and {@code now - duration} is a trivial conversion on the client.
     */
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

    private static @Nullable String eventType(@NotNull Context ctx) {
        String raw = QueryParams.optional(ctx, "type");
        if (raw == null) {
            return null;
        }
        String candidate = raw.trim().toUpperCase(Locale.ROOT);
        for (HistoryEventType known : HistoryEventType.values()) {
            if (known.name().equals(candidate)) {
                return known.name();
            }
        }
        throw ApiException.badRequest("INVALID_EVENT_TYPE",
                "Query parameter 'type' is not a known event type: '" + raw + "'");
    }

    private static @Nullable UUID playerFilter(@NotNull Context ctx) {
        String raw = QueryParams.optional(ctx, "player");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("MALFORMED_UUID",
                    "Query parameter 'player' is not a valid UUID");
        }
    }

    private static void collectIds(@NotNull HistoryEntry entry, @NotNull List<UUID> ids) {
        switch (entry) {
            case HistoryEntry.Freehold freehold -> {
                ids.add(freehold.buyerId());
                ids.add(freehold.authorityId());
            }
            case HistoryEntry.Leasehold leasehold -> {
                ids.add(leasehold.tenantId());
                ids.add(leasehold.landlordId());
            }
            case HistoryEntry.Agent agent -> {
                ids.add(agent.agentId());
                ids.add(agent.actorId());
            }
        }
    }

    private static @NotNull HistoryResponse.Entry toEntry(@NotNull HistoryEntry entry,
                                                          @NotNull Map<UUID, String> names) {
        String eventTime = IsoDates.format(entry.eventTime());
        return switch (entry) {
            case HistoryEntry.Freehold freehold -> HistoryResponse.Entry.freehold(
                    freehold.eventType(), eventTime,
                    ref(freehold.buyerId(), names), ref(freehold.authorityId(), names),
                    freehold.price());
            case HistoryEntry.Leasehold leasehold -> HistoryResponse.Entry.leasehold(
                    leasehold.eventType(), eventTime,
                    ref(leasehold.tenantId(), names), ref(leasehold.landlordId(), names),
                    leasehold.price(), leasehold.durationSeconds(), leasehold.extensionsRemaining());
            case HistoryEntry.Agent agent -> HistoryResponse.Entry.agent(
                    agent.eventType(), eventTime,
                    ref(agent.agentId(), names), ref(agent.actorId(), names));
        };
    }

    private static @NotNull PlayerRef ref(@NotNull UUID id, @NotNull Map<UUID, String> names) {
        return Objects.requireNonNull(PlayerNames.ref(id, names));
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }
}
