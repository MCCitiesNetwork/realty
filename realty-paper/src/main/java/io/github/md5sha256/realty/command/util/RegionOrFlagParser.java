package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.leangen.geantyref.TypeFactory;
import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An optional region argument that steps aside for a flag. Cloud reads flags only after the last
 * argument, so without this a command such as {@code /realty set landlord GovSecurity --government}
 * would take {@code --government} for the region. When the next word starts with {@code --}, this
 * parser consumes nothing and yields an empty {@link Optional}, which the handler treats as "no
 * region given"; every other word goes to the delegate parser.
 *
 * <p>A region whose id starts with {@code --} therefore cannot be named where this parser is used.</p>
 */
public final class RegionOrFlagParser<C, T> implements ArgumentParser.FutureArgumentParser<C, Optional<T>> {

    private final ArgumentParser<C, T> delegate;

    private RegionOrFlagParser(@NotNull ArgumentParser<C, T> delegate) {
        this.delegate = delegate;
    }

    /** A WorldGuard region, or empty when a flag follows instead. */
    public static @NotNull ParserDescriptor<Source, Optional<WorldGuardRegion>> regionOrFlag() {
        return of(WorldGuardRegionResolver.worldGuardRegionResolver());
    }

    @SuppressWarnings("unchecked")
    public static <C, T> @NotNull ParserDescriptor<C, Optional<T>> of(@NotNull ParserDescriptor<C, T> delegate) {
        TypeToken<Optional<T>> valueType = (TypeToken<Optional<T>>) TypeToken.get(
                TypeFactory.parameterizedClass(Optional.class, delegate.valueType().getType()));
        return ParserDescriptor.of(new RegionOrFlagParser<>(delegate.parser()), valueType);
    }

    @Override
    public @NotNull CompletableFuture<ArgumentParseResult<Optional<T>>> parseFuture(
            @NotNull CommandContext<C> ctx,
            @NotNull CommandInput input
    ) {
        if (input.peekString().startsWith("--")) {
            return ArgumentParseResult.successFuture(Optional.empty());
        }
        return delegate.parseFuture(ctx, input).thenApply(result -> result.mapSuccess(Optional::of));
    }

    @Override
    public @NotNull SuggestionProvider<C> suggestionProvider() {
        return delegate.suggestionProvider();
    }
}
