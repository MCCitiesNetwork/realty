package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where {@code --now} may stand in {@code /realty set price <price> [region] [--now]} and
 * {@code /realty set maxextensions <count> [region] [--now]}. The region argument is a
 * {@link RegionOrFlagParser} around a stand-in that takes any word as a region, and the flag is the one
 * {@link SetCommandGroup} registers.
 */
class NowFlagPositionTest {

    private record Parsed(Object value, Optional<String> region, boolean now) {}

    private final AtomicReference<CommandContext<Object>> last = new AtomicReference<>();
    private CommandManager<Object> manager;

    @BeforeEach
    void setUp() {
        manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(Object sender, String permission) {
                return true;
            }
        };
        manager.command(manager.commandBuilder("set").literal("price")
                .required("price", DoubleParser.doubleParser(0.0001, Double.MAX_VALUE))
                .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser()))
                .flag(SetCommandGroup.NOW_FLAG)
                .handler(last::set));
        // The same shape as /realty set maxextensions, whose count accepts -1.
        manager.command(manager.commandBuilder("set").literal("maxextensions")
                .required("maxextensions", IntegerParser.integerParser(-1))
                .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser()))
                .flag(SetCommandGroup.NOW_FLAG)
                .handler(last::set));
        // The same shape as /realty set landlord: type flags and --now share the flag position.
        manager.command(PartyFlags.addTo(manager.commandBuilder("set").literal("landlord")
                .required("landlord", StringParser.<Object>stringParser())
                .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser())))
                .flag(SetCommandGroup.NOW_FLAG)
                .handler(last::set));
    }

    private Parsed parse(String input, String valueName) {
        manager.commandExecutor().executeCommand(new Object(), input).join();
        CommandContext<Object> ctx = last.get();
        Optional<String> region = ctx.<Optional<String>>optional("region").flatMap(Function.identity());
        return new Parsed(ctx.get(valueName), region, ctx.flags().hasFlag(SetCommandGroup.NOW_FLAG));
    }

    @Test
    void flagWithoutARegion_parses() {
        assertEquals(new Parsed(500.0, Optional.empty(), true), parse("set price 500 --now", "price"));
    }

    @Test
    void flagAfterTheRegion_parses() {
        assertEquals(new Parsed(500.0, Optional.of("someregion"), true),
                parse("set price 500 someregion --now", "price"));
    }

    @Test
    void noFlag_parses() {
        assertEquals(new Parsed(500.0, Optional.of("someregion"), false),
                parse("set price 500 someregion", "price"));
    }

    @Test
    void unlimitedExtensionsWithTheFlag_parsesTheNegativeCount() {
        assertEquals(new Parsed(-1, Optional.empty(), true), parse("set maxextensions -1 --now", "maxextensions"));
    }

    @Test
    void unlimitedExtensionsWithoutTheFlag_parsesTheNegativeCount() {
        assertEquals(new Parsed(-1, Optional.empty(), false), parse("set maxextensions -1", "maxextensions"));
    }

    private void assertLandlordParses(String input, Optional<String> region) {
        Parsed parsed = parse(input, "landlord");
        assertEquals(new Parsed("Name", region, true), parsed);
        assertTrue(last.get().flags().isPresent("government"));
    }

    @Test
    void landlordTypeFlagThenNow_parsesWithoutARegion() {
        assertLandlordParses("set landlord Name --government --now", Optional.empty());
    }

    @Test
    void landlordNowThenTypeFlag_parsesWithoutARegion() {
        assertLandlordParses("set landlord Name --now --government", Optional.empty());
    }

    @Test
    void landlordTypeFlagThenNow_parsesWithARegion() {
        assertLandlordParses("set landlord Name someregion --government --now", Optional.of("someregion"));
    }

    @Test
    void landlordNowThenTypeFlag_parsesWithARegion() {
        assertLandlordParses("set landlord Name someregion --now --government", Optional.of("someregion"));
    }
}
