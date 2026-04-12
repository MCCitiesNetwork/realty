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
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds and shows the search dialog to a player, and handles the search
 * callback when the player submits the form.
 *
 * <p>The search flow uses two dialog pages:
 * <ol>
 *     <li>Main dialog — contract type checkboxes and price range inputs.</li>
 *     <li>Tag dialog — a grid of tag buttons (max 10 per column) that cycle
 *         through Ignore / Include / Exclude states.</li>
 * </ol>
 * Per-player state is tracked between the two pages so that criteria and tag
 * selections are preserved when navigating back and forth.
 */
public final class SearchDialog {

    static final int PAGE_SIZE = 10;
    private static final int MAX_TAGS_PER_COLUMN = 10;

    static final String INPUT_FREEHOLD = "freehold";
    static final String INPUT_LEASEHOLD = "leasehold";
    static final String INPUT_MIN_PRICE = "min_price";
    static final String INPUT_MAX_PRICE = "max_price";

    private final Database database;
    private final ExecutorState executorState;
    private final AtomicReference<RealtyTags> realtyTags;
    private final MessageContainer messages;
    private final ConcurrentHashMap<UUID, SearchState> playerStates = new ConcurrentHashMap<>();

    enum TagState {
        IGNORE,
        INCLUDE,
        EXCLUDE;

        TagState next() {
            return switch (this) {
                case IGNORE -> INCLUDE;
                case INCLUDE -> EXCLUDE;
                case EXCLUDE -> IGNORE;
            };
        }
    }

    static final class SearchState {
        boolean freehold = true;
        boolean leasehold = true;
        String minPrice = "0";
        String maxPrice = "";
        final Map<String, TagState> tagStates = new LinkedHashMap<>();
    }

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
        SearchState state = new SearchState();
        for (ConfigRegionTag tag : tags.values()) {
            if (tag.permission() == null || player.hasPermission(tag.permission().node())) {
                state.tagStates.put(tag.tagId(), TagState.IGNORE);
            }
        }
        playerStates.put(player.getUniqueId(), state);
        showMainDialog(player, state, tags);
    }

    private void showMainDialog(@NotNull Player player,
                                @NotNull SearchState state,
                                @NotNull RealtyTags tags) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.bool(INPUT_FREEHOLD, Component.text("Freehold"))
                .initial(state.freehold)
                .onTrue("true")
                .onFalse("false")
                .build());
        inputs.add(DialogInput.bool(INPUT_LEASEHOLD, Component.text("Leasehold"))
                .initial(state.leasehold)
                .onTrue("true")
                .onFalse("false")
                .build());
        inputs.add(DialogInput.text(INPUT_MIN_PRICE, Component.text("Min Price"))
                .width(150)
                .initial(state.minPrice)
                .maxLength(15)
                .build());
        inputs.add(DialogInput.text(INPUT_MAX_PRICE, Component.text("Max Price"))
                .width(150)
                .initial(state.maxPrice)
                .maxLength(15)
                .build());

        ClickCallback.Options clickOptions = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .build();

        DialogActionCallback searchCallback = (response, audience) -> {
            saveCriteria(state, response);
            List<String> includedTags = new ArrayList<>();
            List<String> excludedTags = new ArrayList<>();
            for (Map.Entry<String, TagState> entry : state.tagStates.entrySet()) {
                if (entry.getValue() == TagState.INCLUDE) {
                    includedTags.add(entry.getKey());
                } else if (entry.getValue() == TagState.EXCLUDE) {
                    excludedTags.add(entry.getKey());
                }
            }
            Collection<String> tagFilter = includedTags.isEmpty() ? null : includedTags;
            Collection<String> excludeFilter = excludedTags.isEmpty() ? null : excludedTags;
            double minPrice = parsePrice(state.minPrice, 0.0);
            double maxPrice = parsePrice(state.maxPrice, Double.MAX_VALUE);
            boolean includeFreehold = state.freehold;
            boolean includeLeasehold = state.leasehold;
            playerStates.remove(player.getUniqueId());

            if (!includeFreehold && !includeLeasehold) {
                audience.sendMessage(messages.messageFor(MessageKeys.SEARCH_NO_RESULTS));
                return;
            }
            performSearch(audience, includeFreehold, includeLeasehold, tagFilter,
                    excludeFilter, minPrice, maxPrice, 1);
        };

        DialogActionCallback configTagsCallback = (response, audience) -> {
            saveCriteria(state, response);
            showTagDialog(player, state, tags);
        };

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Search"))
                .width(150)
                .action(DialogAction.customClick(searchCallback, clickOptions))
                .build());
        if (!state.tagStates.isEmpty()) {
            actions.add(ActionButton.builder(Component.text("Filter Tags"))
                    .width(150)
                    .action(DialogAction.customClick(configTagsCallback, clickOptions))
                    .build());
        }

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Search Regions"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Filter regions by type and price range."))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(
                        actions,
                        ActionButton.builder(Component.text("Cancel")).width(150).build(),
                        actions.size()))
        );
        player.showDialog(dialog);
    }

    private void showTagDialog(@NotNull Player player,
                               @NotNull SearchState state,
                               @NotNull RealtyTags tags) {
        ClickCallback.Options clickOptions = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .build();

        List<ActionButton> tagButtons = new ArrayList<>();
        for (ConfigRegionTag tag : tags.values()) {
            if (tag.permission() != null && !player.hasPermission(tag.permission().node())) {
                continue;
            }
            TagState currentState = state.tagStates.getOrDefault(tag.tagId(), TagState.IGNORE);
            Component label = buildTagLabel(tag.tagDisplayName(), currentState);

            String tagId = tag.tagId();
            DialogActionCallback toggleCallback = (response, audience) -> {
                TagState current = state.tagStates.getOrDefault(tagId, TagState.IGNORE);
                state.tagStates.put(tagId, current.next());
                showTagDialog(player, state, tags);
            };

            tagButtons.add(ActionButton.builder(label)
                    .width(150)
                    .action(DialogAction.customClick(toggleCallback, clickOptions))
                    .build());
        }

        int tagCount = tagButtons.size();
        int columns = Math.max(1, (tagCount + MAX_TAGS_PER_COLUMN - 1) / MAX_TAGS_PER_COLUMN);

        DialogActionCallback doneCallback = (response, audience) ->
                showMainDialog(player, state, tags);

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Filter Tags"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Click a tag to cycle: Ignore -> Include -> Exclude"))))
                        .inputs(List.of())
                        .build())
                .type(DialogType.multiAction(
                        tagButtons,
                        ActionButton.builder(Component.text("Done"))
                                .width(150)
                                .action(DialogAction.customClick(doneCallback, clickOptions))
                                .build(),
                        columns))
        );
        player.showDialog(dialog);
    }

    private @NotNull Component buildTagLabel(@NotNull Component tagName,
                                             @NotNull TagState tagState) {
        return switch (tagState) {
            case IGNORE -> tagName.colorIfAbsent(NamedTextColor.GRAY);
            case INCLUDE -> Component.text()
                    .color(NamedTextColor.GREEN)
                    .append(Component.text("[Include] "))
                    .append(tagName)
                    .build();
            case EXCLUDE -> Component.text()
                    .color(NamedTextColor.RED)
                    .append(Component.text("[Exclude] "))
                    .append(tagName)
                    .build();
        };
    }

    private void saveCriteria(@NotNull SearchState state,
                              @NotNull DialogResponseView response) {
        Boolean freehold = response.getBoolean(INPUT_FREEHOLD);
        state.freehold = freehold == null || freehold;
        Boolean leasehold = response.getBoolean(INPUT_LEASEHOLD);
        state.leasehold = leasehold == null || leasehold;
        String minPrice = response.getText(INPUT_MIN_PRICE);
        if (minPrice != null) {
            state.minPrice = minPrice;
        }
        String maxPrice = response.getText(INPUT_MAX_PRICE);
        if (maxPrice != null) {
            state.maxPrice = maxPrice;
        }
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
