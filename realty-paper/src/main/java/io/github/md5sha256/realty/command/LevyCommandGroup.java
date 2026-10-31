package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.FeeQuoteFormatter;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.PartyNames;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Groups the levy subcommands under {@code /realty levy}.
 *
 * <ul>
 *   <li>{@code /realty levy set <levy> [region] --authority [--all]}</li>
 *   <li>{@code /realty levy clear [region] --authority [--all]}</li>
 *   <li>{@code /realty levy list}</li>
 * </ul>
 *
 * <p>A levy is chosen for the authority, so both forms take {@code --authority}, admins included:
 * the actor must manage the region's authority, unless they hold {@code realty.command.levy.bypass}.
 * {@code --all} makes the choice for every plot of the region's authority.</p>
 */
public record LevyCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull PartyNames partyNames
) implements CustomCommandBean {

    static final CommandFlag<Void> ALL_FLAG = CommandFlag.<Source>builder("all").build();
    static final CommandFlag<Void> AUTHORITY_FLAG = CommandFlag.<Source>builder("authority").build();
    static final String BYPASS_PERMISSION = "realty.command.levy.bypass";

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("levy");
        return List.of(
                base.literal("set")
                        .permission("realty.command.levy.set")
                        .required("levy", StringParser.stringParser(), levySuggestions())
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(ALL_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .handler(ctx -> executeChange(ctx, ctx.get("levy")))
                        .build(),
                base.literal("clear")
                        .permission("realty.command.levy.clear")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(ALL_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .handler(ctx -> executeChange(ctx, null))
                        .build(),
                base.literal("list")
                        .permission("realty.command.levy.list")
                        .handler(this::executeList)
                        .build()
        );
    }

    private @NotNull SuggestionProvider<Source> levySuggestions() {
        return (ctx, input) -> api.listLevies().thenApply(levies -> levies.stream()
                .map(LevyDefinition::name)
                .map(Suggestion::suggestion)
                .toList());
    }

    /**
     * The range of a levy's rates over all its rules, such as {@code 2–8% by plots held}, or one
     * percentage when they are all the same; {@code no rate} when it has no tiers.
     */
    static @NotNull String rateRange(@NotNull LevyDefinition levy) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (LevyRule rule : levy.rules()) {
            for (LevyTier tier : rule.tiers()) {
                min = Math.min(min, tier.percent());
                max = Math.max(max, tier.percent());
            }
        }
        if (min > max) {
            return "no rate";
        }
        if (Double.compare(min, max) == 0) {
            return FeeQuoteFormatter.percent(min) + "%";
        }
        return FeeQuoteFormatter.percent(min) + "–" + FeeQuoteFormatter.percent(max) + "% by plots held";
    }

    // ── /realty levy set <levy> | clear [region] --authority [--all] ──

    /** @param levyName the levy to choose, or {@code null} to clear the choice */
    private void executeChange(@NotNull CommandContext<Source> ctx, @Nullable String levyName) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        boolean all = ctx.flags().hasFlag(ALL_FLAG);
        if (!mayChange(ctx.flags().hasFlag(AUTHORITY_FLAG))) {
            sender.sendMessage(messages.messageFor(MessageKeys.LEVY_NEEDS_AUTHORITY));
            return;
        }
        WorldGuardRegion region = FeeCommandGroup.regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        boolean bypass = sender.hasPermission(BYPASS_PERMISSION);
        api.getFreeholdContract(regionId, region.world().getUID())
                .thenComposeAsync(freehold -> {
                    if (freehold == null) {
                        return CompletableFuture.completedFuture(
                                new Outcome(new RealtyBackend.SetLevyResult.NoFreehold(), null));
                    }
                    Party authority = freehold.authority();
                    return CompletableFuture.supplyAsync(() -> actors.forRegion(sender, bypass, region),
                                    executorState.dbExec())
                            .thenComposeAsync(actor -> {
                                if (all) {
                                    return levyName != null
                                            ? api.setAuthorityLevy(authority, actor, levyName)
                                            : api.clearAuthorityLevy(authority, actor);
                                }
                                return levyName != null
                                        ? api.setRegionLevy(region, actor, levyName)
                                        : api.clearRegionLevy(region, actor);
                            }, executorState.mainThreadExec())
                            .thenApply(result -> new Outcome(result, authority));
                }, executorState.mainThreadExec())
                .thenAccept(outcome -> sender.sendMessage(message(outcome, regionId, levyName, all)))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    sender.sendMessage(messages.messageFor(MessageKeys.LEVY_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    /**
     * Whether a levy change may go ahead: only with {@code --authority}, for one plot or for all,
     * and for an admin who bypasses the authority check too, so that nobody sets a levy by accident.
     */
    static boolean mayChange(boolean authorityFlag) {
        return authorityFlag;
    }

    private record Outcome(@NotNull RealtyBackend.SetLevyResult result, @Nullable Party authority) {}

    private @NotNull Component message(@NotNull Outcome outcome, @NotNull String regionId,
                                       @Nullable String levyName, boolean all) {
        String levy = levyName != null ? levyName : "";
        String authority = outcome.authority() != null ? partyNames.display(outcome.authority()) : "";
        return switch (outcome.result()) {
            case RealtyBackend.SetLevyResult.Success ignored -> {
                String key = levyName != null
                        ? (all ? MessageKeys.LEVY_SET_ALL : MessageKeys.LEVY_SET)
                        : (all ? MessageKeys.LEVY_CLEARED_ALL : MessageKeys.LEVY_CLEARED);
                yield messages.messageFor(key,
                        Placeholder.unparsed("levy", levy),
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("authority", authority));
            }
            case RealtyBackend.SetLevyResult.NoFreehold ignored ->
                    messages.messageFor(MessageKeys.LEVY_NO_FREEHOLD, Placeholder.unparsed("region", regionId));
            case RealtyBackend.SetLevyResult.NotAuthorityManager ignored ->
                    messages.messageFor(MessageKeys.LEVY_NOT_AUTHORITY_MANAGER, Placeholder.unparsed("region", regionId));
            case RealtyBackend.SetLevyResult.UnknownLevy ignored ->
                    messages.messageFor(MessageKeys.LEVY_UNKNOWN, Placeholder.unparsed("levy", levy));
            case RealtyBackend.SetLevyResult.WrongAuthorityKind ignored ->
                    messages.messageFor(MessageKeys.LEVY_WRONG_AUTHORITY_KIND, Placeholder.unparsed("levy", levy));
            case RealtyBackend.SetLevyResult.NoChange ignored -> messages.messageFor(MessageKeys.LEVY_NO_CHANGE);
            case RealtyBackend.SetLevyResult.Cancelled ignored ->
                    messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED);
        };
    }

    // ── /realty levy list ──

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        api.listLevies().thenAccept(levies -> {
            if (levies.isEmpty()) {
                sender.sendMessage(messages.messageFor(MessageKeys.LEVY_LIST_EMPTY));
                return;
            }
            Component output = messages.messageFor(MessageKeys.LEVY_LIST_HEADER);
            for (LevyDefinition levy : levies) {
                output = output.appendNewline().append(messages.messageFor(MessageKeys.LEVY_LIST_ENTRY,
                        Placeholder.unparsed("levy", levy.name()),
                        Placeholder.unparsed("side", FeeCommandGroup.sideName(levy.paidBy())),
                        Placeholder.unparsed("rate", rateRange(levy)),
                        Placeholder.unparsed("state", levy.enabled() ? "enabled" : "disabled"),
                        Placeholder.unparsed("default", levy.isDefault() ? " (default)" : "")));
            }
            sender.sendMessage(output);
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            sender.sendMessage(messages.messageFor(MessageKeys.LEVY_ERROR,
                    Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
            return null;
        });
    }
}
