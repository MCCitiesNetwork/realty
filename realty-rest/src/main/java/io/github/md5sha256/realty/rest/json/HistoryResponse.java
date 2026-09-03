package io.github.md5sha256.realty.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/region/history} -- the HTTP form of
 * {@code /realty history}, which is granted by default and accepts any region.
 *
 * <p>Entries are polymorphic, mirroring the sealed {@code HistoryEntry}. Rather than a
 * union of every field with most of them null on any given row, each entry carries a
 * {@code kind} discriminator and only the fields its kind defines, so a consumer
 * switches on {@code kind} the same way the backend switches on the sealed type.</p>
 */
public record HistoryResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Entry> entries
) {

    /**
     * One event. {@code kind} is {@code freehold}, {@code leasehold} or {@code agent},
     * and decides which of the remaining fields are present -- the absent ones are
     * omitted rather than serialised as null, so a consumer cannot mistake "this kind
     * has no such field" for "this field happened to be empty".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(
            @NotNull String kind,
            @NotNull String eventType,
            @NotNull String eventTime,
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

        public static @NotNull Entry freehold(@NotNull String eventType, @NotNull String eventTime,
                                              @NotNull PlayerRef buyer, @NotNull PlayerRef authority,
                                              double price) {
            return new Entry("freehold", eventType, eventTime, buyer, authority,
                    null, null, null, null, price, null, null);
        }

        public static @NotNull Entry leasehold(@NotNull String eventType, @NotNull String eventTime,
                                               @NotNull PlayerRef tenant, @NotNull PlayerRef landlord,
                                               @Nullable Double price, @Nullable Long durationSeconds,
                                               @Nullable Integer extensionsRemaining) {
            return new Entry("leasehold", eventType, eventTime, null, null, tenant, landlord,
                    null, null, price, durationSeconds, extensionsRemaining);
        }

        public static @NotNull Entry agent(@NotNull String eventType, @NotNull String eventTime,
                                           @NotNull PlayerRef agent, @NotNull PlayerRef actor) {
            return new Entry("agent", eventType, eventTime, null, null, null, null, agent, actor,
                    null, null, null);
        }
    }

}
