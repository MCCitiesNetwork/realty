package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handles {@code /realty search} (opens dialog) and
 * {@code /realty search results ...} (paginated results via chat navigation).
 *
 * <p>Permission: {@code realty.command.search}.</p>
 */
public record SearchCommand(
        @NotNull SearchDialog searchDialog,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final CommandFlag<Void> FREEHOLD_FLAG =
            CommandFlag.<Source>builder("freehold")
                    .build();

    private static final CommandFlag<Void> LEASEHOLD_FLAG =
            CommandFlag.<Source>builder("leasehold")
                    .build();

    private static final CommandFlag<String> TAGS_FLAG =
            CommandFlag.<Source>builder("tags")
                    .withComponent(StringParser.stringParser())
                    .build();

    private static final CommandFlag<String> EXCLUDE_TAGS_FLAG =
            CommandFlag.<Source>builder("exclude-tags")
                    .withComponent(StringParser.stringParser())
                    .build();

    private static final CommandFlag<Double> MIN_PRICE_FLAG =
            CommandFlag.<Source>builder("min-price")
                    .withComponent(DoubleParser.doubleParser(0))
                    .build();

    private static final CommandFlag<Double> MAX_PRICE_FLAG =
            CommandFlag.<Source>builder("max-price")
                    .withComponent(DoubleParser.doubleParser(0))
                    .build();

    private static final CommandFlag<OccupancyFilter> OCCUPANCY_FLAG =
            CommandFlag.<Source>builder("occupancy")
                    .withComponent(EnumParser.enumParser(OccupancyFilter.class))
                    .build();

    private static final CommandFlag<Integer> PAGE_FLAG =
            CommandFlag.<Source>builder("page")
                    .withComponent(IntegerParser.integerParser(1))
                    .build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        Command<? extends Source> openDialog = builder
                .literal("search")
                .permission("realty.command.search")
                .handler(this::openDialog)
                .build();
        Command<? extends Source> showResults = builder
                .literal("search")
                .literal("results")
                .permission("realty.command.search")
                .flag(FREEHOLD_FLAG)
                .flag(LEASEHOLD_FLAG)
                .flag(TAGS_FLAG)
                .flag(EXCLUDE_TAGS_FLAG)
                .flag(MIN_PRICE_FLAG)
                .flag(MAX_PRICE_FLAG)
                .flag(OCCUPANCY_FLAG)
                .flag(PAGE_FLAG)
                .handler(this::executeResults)
                .build();
        return List.of(showResults, openDialog);
    }

    private void openDialog(@NotNull CommandContext<? extends Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        searchDialog.open(player);
    }

    private void executeResults(@NotNull CommandContext<? extends Source> ctx) {
        CommandSender sender = ctx.sender().source();
        boolean includeFreehold = ctx.flags().hasFlag(FREEHOLD_FLAG);
        boolean includeLeasehold = ctx.flags().hasFlag(LEASEHOLD_FLAG);
        if (!includeFreehold && !includeLeasehold) {
            includeFreehold = true;
            includeLeasehold = true;
        }
        Collection<String> tagIds = parseTagIds(ctx.flags().getValue(TAGS_FLAG, null));
        Collection<String> excludedTagIds = parseTagIds(ctx.flags().getValue(EXCLUDE_TAGS_FLAG, null));
        double minPrice = ctx.flags().getValue(MIN_PRICE_FLAG, 0.0);
        double maxPrice = ctx.flags().getValue(MAX_PRICE_FLAG, Double.MAX_VALUE);
        OccupancyFilter occupancy = ctx.flags().getValue(OCCUPANCY_FLAG, OccupancyFilter.UNOCCUPIED);
        int page = ctx.flags().getValue(PAGE_FLAG, 1);

        searchDialog.performSearch(sender, includeFreehold, includeLeasehold, tagIds,
                excludedTagIds, minPrice, maxPrice, occupancy, page);
    }

    @Nullable
    private static Collection<String> parseTagIds(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String s : raw.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? null : result;
    }

}
