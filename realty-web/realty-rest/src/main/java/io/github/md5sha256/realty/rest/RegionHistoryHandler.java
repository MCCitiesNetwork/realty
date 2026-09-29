package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.HistoryEventType;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.rest.json.HistoryResponse;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PartyNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        PartyRef playerFilter = PlayerNameResolution.fromRequest(ctx, this.moduleClient, false);
        UUID playerId = playerFilter == null ? null : UUID.fromString(playerFilter.id());

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        RealtyBackend.HistoryResult result = this.backend.searchHistory(
                regionParam, worldId, eventType, since, playerId, pageSize, offset);

        // Every identity on the page resolves in one module call rather than one per
        // entry, so a full page costs the same hop as a single row.
        List<Party> parties = new ArrayList<>();
        for (HistoryEntry entry : result.entries()) {
            collectParties(entry, parties);
        }
        PartyNames.Resolved names = PartyNames.resolve(this.moduleClient, parties);

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

    /** A missing buyer or tenant is added as null, which {@link PartyNames#resolve} skips. */
    private static void collectParties(@NotNull HistoryEntry entry, @NotNull List<Party> parties) {
        switch (entry) {
            case HistoryEntry.Freehold freehold -> {
                parties.add(personal(freehold.buyerId()));
                parties.add(freehold.authority());
            }
            case HistoryEntry.Leasehold leasehold -> {
                parties.add(personal(leasehold.tenantId()));
                parties.add(leasehold.landlord());
            }
            case HistoryEntry.Agent agent -> {
                parties.add(new Party.Personal(agent.agentId()));
                parties.add(new Party.Personal(agent.actorId()));
            }
        }
    }

    private static @Nullable Party personal(@Nullable UUID player) {
        return player == null ? null : new Party.Personal(player);
    }

    private static @NotNull HistoryResponse.Entry toEntry(@NotNull HistoryEntry entry,
                                                          @NotNull PartyNames.Resolved names) {
        String eventTime = IsoDates.format(entry.eventTime());
        return switch (entry) {
            case HistoryEntry.Freehold freehold -> HistoryResponse.Entry.freehold(
                    freehold.eventType(), eventTime,
                    names.ref(freehold.buyerId()), Objects.requireNonNull(names.ref(freehold.authority())),
                    freehold.price());
            case HistoryEntry.Leasehold leasehold -> HistoryResponse.Entry.leasehold(
                    leasehold.eventType(), eventTime,
                    names.ref(leasehold.tenantId()),
                    Objects.requireNonNull(names.ref(leasehold.landlord())),
                    leasehold.price(), leasehold.durationSeconds(), leasehold.extensionsRemaining());
            case HistoryEntry.Agent agent -> HistoryResponse.Entry.agent(
                    agent.eventType(), eventTime,
                    Objects.requireNonNull(names.ref(agent.agentId())),
                    Objects.requireNonNull(names.ref(agent.actorId())));
        };
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }
}
