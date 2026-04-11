package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.mapper.SearchMapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.ConfigRegionTag;
import io.github.md5sha256.realty.settings.RealtyTags;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds and shows the search dialog to a player, and handles the search
 * callback when the player submits the form.
 */
public final class SearchDialog {

    static final int PAGE_SIZE = 10;

    static final String INPUT_FREEHOLD = "freehold";
    static final String INPUT_LEASEHOLD = "leasehold";
    static final String INPUT_MIN_PRICE = "min_price";
    static final String INPUT_MAX_PRICE = "max_price";
    static final String TAG_PREFIX = "tag_";

    private static final String TAG_INCLUDE = "include";
    private static final String TAG_EXCLUDE = "exclude";
    private static final String TAG_IGNORE = "ignore";

    private final Database database;
    private final ExecutorState executorState;
    private final AtomicReference<RealtyTags> realtyTags;
    private final MessageContainer messages;

    public SearchDialog(@NotNull Database database,
                        @NotNull ExecutorState executorState,
                        @NotNull AtomicReference<RealtyTags> realtyTags,
                        @NotNull MessageContainer messages) {
        this.database = database;
        this.executorState = executorState;
        this.realtyTags = realtyTags;
        this.messages = messages;
    }

    /**
     * Opens the search dialog for the given player.
     */
    public void open(@NotNull Player player) {
        RealtyTags tags = realtyTags.get();
        List<DialogInput> inputs = buildInputs(player, tags);
        DialogActionCallback searchCallback = buildCallback(player, tags);

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Search Regions"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Filter regions by type, tags, and price range."))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(
                        List.of(ActionButton.builder(Component.text("Search"))
                                .width(150)
                                .action(DialogAction.customClick(
                                        searchCallback,
                                        ClickCallback.Options.builder()
                                                .uses(ClickCallback.UNLIMITED_USES)
                                                .build()))
                                .build()),
                        ActionButton.builder(Component.text("Cancel"))
                                .width(150)
                                .build(),
                        1))
        );
        player.showDialog(dialog);
    }

    private @NotNull List<DialogInput> buildInputs(@NotNull Player player,
                                                   @NotNull RealtyTags tags) {
        List<DialogInput> inputs = new ArrayList<>();

        inputs.add(DialogInput.bool(INPUT_FREEHOLD, Component.text("Freehold"))
                .initial(true)
                .onTrue("true")
                .onFalse("false")
                .build());
        inputs.add(DialogInput.bool(INPUT_LEASEHOLD, Component.text("Leasehold"))
                .initial(true)
                .onTrue("true")
                .onFalse("false")
                .build());

        for (ConfigRegionTag tag : tags.values()) {
            if (tag.permission() != null && !player.hasPermission(tag.permission().node())) {
                continue;
            }
            inputs.add(DialogInput.singleOption(
                    TAG_PREFIX + tag.tagId(),
                    tag.tagDisplayName(),
                    List.of(
                            SingleOptionDialogInput.OptionEntry.create(
                                    TAG_IGNORE, Component.text("Ignore"), true),
                            SingleOptionDialogInput.OptionEntry.create(
                                    TAG_INCLUDE, Component.text("Include"), false),
                            SingleOptionDialogInput.OptionEntry.create(
                                    TAG_EXCLUDE, Component.text("Exclude"), false)
                    )).build());
        }

        inputs.add(DialogInput.text(INPUT_MIN_PRICE, Component.text("Min Price"))
                .width(150)
                .initial("0")
                .maxLength(15)
                .build());
        inputs.add(DialogInput.text(INPUT_MAX_PRICE, Component.text("Max Price"))
                .width(150)
                .initial("")
                .maxLength(15)
                .build());

        return inputs;
    }

    private @NotNull DialogActionCallback buildCallback(@NotNull Player player,
                                                        @NotNull RealtyTags tags) {
        return (response, audience) -> {
            Boolean freehold = response.getBoolean(INPUT_FREEHOLD);
            Boolean leasehold = response.getBoolean(INPUT_LEASEHOLD);
            boolean includeFreehold = freehold == null || freehold;
            boolean includeLeasehold = leasehold == null || leasehold;

            if (!includeFreehold && !includeLeasehold) {
                audience.sendMessage(messages.messageFor(MessageKeys.SEARCH_NO_RESULTS));
                return;
            }

            List<String> includedTags = new ArrayList<>();
            List<String> excludedTags = new ArrayList<>();
            for (ConfigRegionTag tag : tags.values()) {
                if (tag.permission() != null && !player.hasPermission(tag.permission().node())) {
                    continue;
                }
                String value = response.getText(TAG_PREFIX + tag.tagId());
                if (TAG_INCLUDE.equals(value)) {
                    includedTags.add(tag.tagId());
                } else if (TAG_EXCLUDE.equals(value)) {
                    excludedTags.add(tag.tagId());
                }
            }

            Collection<String> tagFilter = includedTags.isEmpty() ? null : includedTags;
            Collection<String> excludeFilter = excludedTags.isEmpty() ? null : excludedTags;

            double minPrice = parsePrice(response.getText(INPUT_MIN_PRICE), 0.0);
            double maxPrice = parsePrice(response.getText(INPUT_MAX_PRICE), Double.MAX_VALUE);

            performSearch(audience, includeFreehold, includeLeasehold, tagFilter, excludeFilter,
                    minPrice, maxPrice, 1);
        };
    }

    /**
     * Executes the search query and sends paginated results to the audience.
     */
    void performSearch(@NotNull Audience sender,
                       boolean includeFreehold, boolean includeLeasehold,
                       @Nullable Collection<String> tagIds,
                       @Nullable Collection<String> excludedTagIds,
                       double minPrice, double maxPrice, int page) {
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                SearchMapper mapper = session.searchMapper();
                int totalCount = mapper.searchCount(includeFreehold, includeLeasehold,
                        tagIds, excludedTagIds, minPrice, maxPrice);

                if (totalCount == 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.SEARCH_NO_RESULTS));
                    return;
                }

                int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
                if (page > totalPages) {
                    sender.sendMessage(messages.messageFor(MessageKeys.SEARCH_INVALID_PAGE,
                            Placeholder.unparsed("page", String.valueOf(page)),
                            Placeholder.unparsed("total", String.valueOf(totalPages))));
                    return;
                }

                int offset = (page - 1) * PAGE_SIZE;
                List<SearchResultEntity> results = mapper.search(includeFreehold, includeLeasehold,
                        tagIds, excludedTagIds, minPrice, maxPrice, PAGE_SIZE, offset);

                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor(MessageKeys.SEARCH_HEADER,
                        Placeholder.unparsed("count", String.valueOf(totalCount))));

                for (SearchResultEntity result : results) {
                    String typeLabel = "freehold".equals(result.contractType())
                            ? "Freehold" : "Leasehold";
                    builder.appendNewline();
                    builder.append(parseMiniMessage(MessageKeys.SEARCH_ENTRY,
                            "<region>", result.worldGuardRegionId(),
                            "<type>", typeLabel,
                            "<price>", CurrencyFormatter.format(result.price())));
                }

                appendFooter(builder, includeFreehold, includeLeasehold, tagIds, excludedTagIds,
                        minPrice, maxPrice, page, totalPages);
                sender.sendMessage(builder.build());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SEARCH_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    static double parsePrice(@Nullable String text, double fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(text.trim());
            return value >= 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void appendFooter(@NotNull TextComponent.Builder builder,
                              boolean includeFreehold, boolean includeLeasehold,
                              @Nullable Collection<String> tagIds,
                              @Nullable Collection<String> excludedTagIds,
                              double minPrice, double maxPrice,
                              int page, int totalPages) {
        Component previousComponent = page > 1
                ? buildNavComponent(MessageKeys.SEARCH_PREVIOUS, includeFreehold, includeLeasehold,
                tagIds, excludedTagIds, minPrice, maxPrice, page - 1)
                : Component.empty();
        Component nextComponent = page < totalPages
                ? buildNavComponent(MessageKeys.SEARCH_NEXT, includeFreehold, includeLeasehold,
                tagIds, excludedTagIds, minPrice, maxPrice, page + 1)
                : Component.empty();
        builder.appendNewline()
                .append(messages.messageFor(MessageKeys.SEARCH_FOOTER,
                        Placeholder.unparsed("page", String.valueOf(page)),
                        Placeholder.unparsed("total", String.valueOf(totalPages)),
                        Placeholder.component("previous", previousComponent),
                        Placeholder.component("next", nextComponent)));
    }

    private @NotNull Component buildNavComponent(@NotNull String key,
                                                 boolean includeFreehold, boolean includeLeasehold,
                                                 @Nullable Collection<String> tagIds,
                                                 @Nullable Collection<String> excludedTagIds,
                                                 double minPrice, double maxPrice,
                                                 int targetPage) {
        StringBuilder command = new StringBuilder("/realty search results");
        if (includeFreehold) {
            command.append(" --freehold");
        }
        if (includeLeasehold) {
            command.append(" --leasehold");
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            command.append(" --tags ").append(String.join(",", tagIds));
        }
        if (excludedTagIds != null && !excludedTagIds.isEmpty()) {
            command.append(" --exclude-tags ").append(String.join(",", excludedTagIds));
        }
        if (minPrice > 0) {
            command.append(" --min-price ").append(minPrice);
        }
        if (maxPrice < Double.MAX_VALUE) {
            command.append(" --max-price ").append(maxPrice);
        }
        command.append(" --page ").append(targetPage);
        return parseMiniMessage(key, "<command>", command.toString());
    }

    private @NotNull Component parseMiniMessage(@NotNull String key,
                                                @NotNull String... replacements) {
        String raw = messages.miniMessageFormattedFor(key);
        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return messages.deserializeRaw(raw);
    }

}
