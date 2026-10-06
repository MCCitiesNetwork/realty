package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * The name a command is given for a party as an argument of its own, such as the landlord in
 * {@code /realty set landlord <name> [region]}: one word, which {@link PartyResolver} resolves in the
 * handler. An account may be named by {@code #<id>}.
 *
 * <p>The parser has a class of its own so that Brigadier can be told about it. A player's command is
 * parsed by Brigadier before Cloud sees it, and Cloud registers a single-word string as Brigadier's
 * {@code word()}, which stops at {@code #} and refuses the command. {@link #registerBrigadierMappings}
 * registers a party name as a greedy string instead, as Cloud does for flags: Brigadier then accepts
 * whatever follows, and Cloud still reads one word and parses the rest itself.</p>
 */
public final class PartyNameParser<C> implements ArgumentParser<C, String> {

    private PartyNameParser() {}

    public static <C> @NotNull ParserDescriptor<C, String> partyName() {
        return ParserDescriptor.of(new PartyNameParser<>(), String.class);
    }

    @Override
    public @NotNull ArgumentParseResult<String> parse(@NotNull CommandContext<C> ctx, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }

    /**
     * Registers a party name, and a {@link RegionOrFlagParser} around one, as a greedy string.
     * Every other {@link RegionOrFlagParser} stays the single word it would be without a mapping.
     */
    public static <C> void registerBrigadierMappings(@NotNull CloudBrigadierManager<C, ?> brigadier) {
        brigadier.registerMapping(new TypeToken<PartyNameParser<C>>() {},
                builder -> builder.cloudSuggestions().toConstant(StringArgumentType.greedyString()));
        brigadier.registerMapping(new TypeToken<RegionOrFlagParser<C, ?>>() {},
                builder -> builder.cloudSuggestions().to(parser -> parser.delegate() instanceof PartyNameParser
                        ? StringArgumentType.greedyString()
                        : StringArgumentType.word()));
    }
}
