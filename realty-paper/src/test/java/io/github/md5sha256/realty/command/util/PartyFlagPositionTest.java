package io.github.md5sha256.realty.command.util;

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.setting.ManagerSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a type flag may stand in {@code /realty set landlord <name> [region] [type flag]} and
 * {@code /realty list [owned|authority|landlord|rented] [name] [--page <n>] [type flag]}. The manager parses as the
 * one {@code Realty} builds: no manager setting is changed. The region argument is a
 * {@link RegionOrFlagParser} around a stand-in that takes any word as a region.
 */
class PartyFlagPositionTest {

    private record Parsed(String landlord, Optional<String> region, PartyFlags.Read flag) {}

    private final AtomicReference<CommandContext<Object>> last = new AtomicReference<>();
    /** Which list command ran: {@code ""} or the category, such as {@code "owned"}. */
    private final AtomicReference<String> listCategory = new AtomicReference<>();
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
                        .required("landlord", PartyNameParser.partyName())
                        .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser())))
                .handler(last::set));
        // Shaped like /realty register leasehold, whose max-extensions argument accepts -1.
        manager.command(PartyFlags.addTo(manager.commandBuilder("register")
                        .literal("leasehold")
                        .required("maxextensions", IntegerParser.integerParser(-1))
                        .flag(CommandFlag.builder("landlord").withComponent(StringParser.stringParser()))
                        .optional("region", StringParser.stringParser()))
                .handler(last::set));
        // Shaped like /realty list [owned|authority|landlord|rented] [name] [--page <n>] [type flag].
        var list = manager.commandBuilder("list");
        for (String category : List.of("", "owned", "authority", "landlord", "rented")) {
            manager.command(PartyFlags.addTo((category.isEmpty() ? list : list.literal(category))
                            .optional("name", RegionOrFlagParser.of(PartyNameParser.<Object>partyName()))
                            .flag(CommandFlag.builder("page").withComponent(IntegerParser.integerParser(1))))
                    .handler(ctx -> {
                        listCategory.set(category);
                        last.set(ctx);
                    }));
        }
        return manager;
    }

    @BeforeEach
    void setUp() {
        manager = manager(false);
    }

    private CommandContext<Object> run(String input) {
        manager.commandExecutor().executeCommand(new Object(), input).join();
        return last.get();
    }

    private Parsed parse(String input) {
        CommandContext<Object> ctx = run(input);
        Optional<String> region = ctx.<Optional<String>>optional("region").flatMap(Function.identity());
        return new Parsed(ctx.get("landlord"), region, PartyFlags.read(ctx));
    }

    @Test
    void flagWithoutARegion_parses() {
        assertEquals(new Parsed("Gov", Optional.empty(), new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                parse("set landlord Gov --government"));
    }

    @Test
    void flagAfterTheRegion_parses() {
        assertEquals(new Parsed("Gov", Optional.of("myregion"), new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                parse("set landlord Gov myregion --government"));
    }

    @Test
    void noFlagNoRegion_parses() {
        assertEquals(new Parsed("Steve", Optional.empty(), new PartyFlags.Read.One(null)),
                parse("set landlord Steve"));
    }

    @Test
    void noFlagWithRegion_parses() {
        assertEquals(new Parsed("Steve", Optional.of("myregion"), new PartyFlags.Read.One(null)),
                parse("set landlord Steve myregion"));
    }

    @Test
    void flagBeforeTheRegion_isRefused() {
        // Flags are read after the last argument, so the region cannot follow one.
        assertThrows(CompletionException.class, () -> parse("set landlord Gov --government myregion"));
    }

    @Test
    void twoFlagsWithoutARegion_parse() {
        Parsed parsed = parse("set landlord Gov --government --group");
        assertEquals(Optional.empty(), parsed.region());
        assertTrue(parsed.flag() instanceof PartyFlags.Read.TooMany, parsed.toString());
    }

    @Test
    void negativeMaxExtensions_parses() {
        CommandContext<Object> ctx = run("register leasehold -1 myregion --landlord Acme --business");
        assertEquals(-1, ctx.<Integer>get("maxextensions"));
        assertEquals("Acme", ctx.flags().getValue("landlord", null));
        assertEquals(new PartyFlags.Read.One(PartyFlag.BUSINESS), PartyFlags.read(ctx));
    }

    @Test
    void liberalFlagParsing_wouldTakeMinusOneForAFlag() {
        // Why that setting stays off: it would read the -1 that means "unlimited" as a flag.
        manager = manager(true);
        assertThrows(CompletionException.class,
                () -> run("register leasehold -1 myregion --landlord Acme --business"));
    }

    private record Listed(String category, Optional<String> name, Integer page, PartyFlags.Read flag) {}

    private Listed list(String input) {
        CommandContext<Object> ctx = run(input);
        Optional<String> name = ctx.<Optional<String>>optional("name").flatMap(Function.identity());
        return new Listed(listCategory.get(), name, ctx.flags().getValue("page", null), PartyFlags.read(ctx));
    }

    @Test
    void listWithoutAName_acceptsThePageFlag() {
        assertEquals(new Listed("", Optional.empty(), 2, new PartyFlags.Read.One(null)), list("list --page 2"));
    }

    @Test
    void listWithAName_andATypeFlag() {
        assertEquals(new Listed("", Optional.of("GovSecurity"), null,
                        new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                list("list GovSecurity --government"));
    }

    @Test
    void listCategoryWithAName_andFlags() {
        assertEquals(new Listed("owned", Optional.of("GovSecurity"), 2,
                        new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                list("list owned GovSecurity --government --page 2"));
    }

    @Test
    void listWithAPlayerName() {
        assertEquals(new Listed("", Optional.of("Steve"), null, new PartyFlags.Read.One(null)),
                list("list Steve"));
    }

    @Test
    void listLandlordWithAName_andATypeFlag() {
        assertEquals(new Listed("landlord", Optional.of("GovSecurity"), null,
                        new PartyFlags.Read.One(PartyFlag.GOVERNMENT)),
                list("list landlord GovSecurity --government"));
    }

    @Test
    void listAuthorityWithAName_andFlags() {
        assertEquals(new Listed("authority", Optional.of("police"), 3,
                        new PartyFlags.Read.One(PartyFlag.GROUP)),
                list("list authority police --page 3 --group"));
    }

    @Test
    void listAuthorityAndLandlordAlone_areCategoriesNotNames() {
        assertEquals(new Listed("authority", Optional.empty(), null, new PartyFlags.Read.One(null)),
                list("list authority"));
        assertEquals(new Listed("landlord", Optional.empty(), 2, new PartyFlags.Read.One(null)),
                list("list landlord --page 2"));
    }

    @Test
    void listCategoryAlone_isTheCategoryNotAName() {
        assertEquals(new Listed("rented", Optional.empty(), null, new PartyFlags.Read.One(null)),
                list("list rented"));
    }
}
