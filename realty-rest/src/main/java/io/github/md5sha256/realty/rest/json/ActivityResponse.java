package io.github.md5sha256.realty.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/activity} -- a server-wide feed of recent events.
 *
 * <p>Each event is individually obtainable in game: {@code /realty history} is granted
 * by default and accepts any region. What this route adds is the sweep across regions,
 * which no command performs -- see the aggregation note in the v1.x spec.</p>
 *
 * <p>Entries take the same polymorphic shape as {@code /v1/region/history}, with the
 * region and world added, since a feed spanning the server has to say where each event
 * happened.</p>
 */
public record ActivityResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Event> events
) {

    /**
     * One event. As on {@code /v1/region/history}, {@code kind} decides which of the
     * party and detail fields are present, and the rest are omitted rather than sent
     * as null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(
            @NotNull String kind,
            @NotNull String eventType,
            @NotNull String eventTime,
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world,
            @Nullable PlayerRef buyer,
            @Nullable PlayerRef authority,
            @Nullable PlayerRef tenant,
            @Nullable PlayerRef landlord,
            @Nullable PlayerRef agent,
            @Nullable PlayerRef actor,
            @Nullable Double price,
            @Nullable Long durationSeconds,
            @Nullable Integer extensionsRemaining
    ) {
    }

}
