package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.ActivityRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The server-wide activity feed, across all three history tables.
 *
 * <p>Its own mapper rather than a method on one of the history mappers, because it
 * belongs to none of them: every query here unions all three, and hanging it off
 * {@code FreeholdHistoryMapper} would imply a primacy that table does not have.</p>
 */
public interface ActivityMapper {

    /**
     * One page of events, newest first, across every region.
     *
     * @param eventTypes the event types to include; never empty, since an empty set
     *                   would mean "no events" rather than "all events" and the caller
     *                   already substitutes its default before reaching here
     * @param worldId    narrows to one world, or null for every world
     * @param since      only events at or after this instant, or null for all time
     */
    @NotNull List<ActivityRow> selectPage(@NotNull Collection<String> eventTypes,
                                          @Nullable UUID worldId,
                                          @Nullable LocalDateTime since,
                                          int limit,
                                          int offset);

    int countMatching(@NotNull Collection<String> eventTypes,
                      @Nullable UUID worldId,
                      @Nullable LocalDateTime since);
}
