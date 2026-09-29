package io.github.md5sha256.realty.command.util;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.setting.ManagerSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Where a type flag may stand in commands shaped like {@code /realty set landlord <name> [region]}
 * and {@code /realty register leasehold <maxextensions> [--landlord <name>] [region]}. The manager
 * parses as the one {@code Realty} builds: no manager setting is changed.
 *
 * <p>Cloud places flags after the last argument, so a flag is read only after the region. The
 * setting {@link ManagerSetting#LIBERAL_FLAG_PARSING} would allow a flag anywhere, but it would
 * also read the {@code -1} that means "unlimited extensions" as an unknown flag.</p>
 */
class PartyFlagPositionTest {

    private record Parsed(String landlord, Optional<String> region, PartyFlags.Read flag) {}

    /** Stands in for the WorldGuard region parser, which fails for a name that is not a region. */
    private static final ParserDescriptor<Object, String> KNOWN_REGION = ParserDescriptor.of(
            (ctx, input) -> {
                String name = input.readString();
                return name.equals("myregion")
                        ? ArgumentParseResult.success(name)
                        : ArgumentParseResult.failure(new IllegalArgumentException("Region not found: " + name));
            }, String.class);

    private final AtomicReference<CommandContext<Object>> last = new AtomicReference<>();
    private CommandManager<Object> manager;

    private CommandManager<Object> manager(boolean liberalFlagParsing) {
        CommandManager<Object> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(Object sender, String permission) {
                return true;
            }
        };
        manager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, liberalFlagParsing);
        manager.command(PartyFlags.addTo(manager.commandBuilder("set")
                        .literal("landlord")
                        .required("landlord", StringParser.stringParser())
                        .optional("region", KNOWN_REGION))
                .handler(last::set));
        manager.command(PartyFlags.addTo(manager.commandBuilder("register")
                        .literal("leasehold")
                        .required("maxextensions", IntegerParser.integerParser(-1))
                        .flag(CommandFlag.builder("landlord").withComponent(StringParser.stringParser()))
                        .optional("region", KNOWN_REGION))
                .handler(last::set));
        return manager;
    }

    @BeforeEach
    void setUp() {
        manager = manager(false);
    }

    private Parsed parse(String input) {
        manager.commandExecutor().executeCommand(new Object(), input).join();
        CommandContext<Object> ctx = last.get();
        return new Parsed(ctx.get("landlord"), ctx.optional("region"), PartyFlags.read(ctx));
    }

    @Test
    void flagAfterTheRegion_parses() {
        assertEquals(new Parsed("Gov", Optional.of("myregion"), new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                parse("set landlord Gov myregion --government"));
    }

    @Test
    void flagBeforeTheRegion_isRefused() {
        assertThrows(CompletionException.class, () -> parse("set landlord Gov --government myregion"));
    }

    @Test
    void flagWithoutARegion_isRefused() {
        // The flag is read as the region, and no region has that name.
        assertThrows(CompletionException.class, () -> parse("set landlord Gov --government"));
    }

    @Test
    void noFlag_parses() {
        assertEquals(new Parsed("Steve", Optional.of("myregion"), new PartyFlags.Read.One(null)),
                parse("set landlord Steve myregion"));
    }

    @Test
    void negativeMaxExtensions_parses() {
        manager.commandExecutor().executeCommand(new Object(),
                "register leasehold -1 myregion --landlord Acme --business").join();
        CommandContext<Object> ctx = last.get();
        assertEquals(-1, ctx.<Integer>get("maxextensions"));
        assertEquals("Acme", ctx.flags().getValue("landlord", null));
        assertEquals(new PartyFlags.Read.One(PartyFlag.BUSINESS), PartyFlags.read(ctx));
    }

    @Test
    void liberalFlagParsing_wouldAllowAFlagBeforeOrWithoutTheRegion() {
        manager = manager(true);
        assertEquals(new Parsed("Gov", Optional.of("myregion"), new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                parse("set landlord Gov --government myregion"));
        assertEquals(new Parsed("Gov", Optional.empty(), new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                parse("set landlord Gov --government"));
    }

    @Test
    void liberalFlagParsing_wouldTakeMinusOneForAFlag() {
        manager = manager(true);
        assertThrows(CompletionException.class, () -> manager.commandExecutor()
                .executeCommand(new Object(), "register leasehold -1 myregion --landlord Acme --business").join());
    }
}
