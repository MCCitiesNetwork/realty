package io.github.md5sha256.realty.command.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Brigadier makes of a party name given as {@code #<id>}; see {@link PartyNameParser}. The
 * commands are shaped like the ones {@code Realty} registers, and the mappings are the ones it
 * registers.
 */
class PartyNameBrigadierTest {

    private CommandDispatcher<Object> dispatcher;

    @BeforeEach
    void setUp() {
        CommandManager<Object> manager = new CommandManager<>(ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(Object sender, String permission) {
                return true;
            }
        };
        var realty = manager.commandBuilder("realty");
        manager.command(PartyFlags.addTo(realty.literal("set").literal("landlord")
                        .required("landlord", PartyNameParser.partyName())
                        .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser())))
                .handler(ctx -> {}));
        manager.command(PartyFlags.addTo(realty.literal("group").literal("map")
                        .required("group", StringParser.stringParser())
                        .required("account", PartyNameParser.partyName()))
                .handler(ctx -> {}));
        manager.command(PartyFlags.addTo(realty.literal("list")
                        .optional("name", RegionOrFlagParser.of(PartyNameParser.<Object>partyName()))
                        .flag(CommandFlag.builder("page").withComponent(IntegerParser.integerParser(1))))
                .handler(ctx -> {}));
        // Shaped like /realty set duration: a region that is not a party name.
        manager.command(realty.literal("set").literal("duration")
                .required("duration", IntegerParser.integerParser())
                .optional("region", RegionOrFlagParser.of(StringParser.<Object>stringParser()))
                .handler(ctx -> {}));

        CloudBrigadierManager<Object, Object> brigadier =
                new CloudBrigadierManager<>(manager, SenderMapper.identity());
        PartyNameParser.registerBrigadierMappings(brigadier);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(brigadier.literalBrigadierNodeFactory().createNode(
                "realty", manager.commandTree().getNamedNode("realty"), ctx -> 1, (sender, permission) -> true));
    }

    private ParseResults<Object> parse(String input) {
        return dispatcher.parse(input, new Object());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "realty set landlord #105545 sia1_10b --business",
            "realty set landlord #105545 --business",
            "realty set landlord #105545",
            "realty set landlord Steve sia1_10b",
            "realty group map mayors #105545 --government",
            "realty list #105545 --business",
            "realty list #105545 --page 2 --business",
            "realty list --page 2",
    })
    void brigadierAcceptsTheCommand(String input) {
        ParseResults<Object> results = parse(input);
        assertTrue(results.getExceptions().isEmpty(), results.getExceptions().toString());
        assertEquals("", results.getReader().getRemaining());
        assertNotNull(results.getContext().getCommand(), "not executable: " + input);
    }

    @Test
    void aRegionThatIsNotAPartyName_staysASingleWord() {
        // Only party names are widened: the region after a duration is still one Brigadier word.
        ParseResults<Object> results = parse("realty set duration 5 #105545");
        assertEquals("#105545", results.getReader().getRemaining());
    }
}
