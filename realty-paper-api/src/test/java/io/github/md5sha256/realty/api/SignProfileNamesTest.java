package io.github.md5sha256.realty.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A sign line is MiniMessage written by the server, but the values put into it are not: the
 * landlord or authority can be an account whose display name its owner chooses. Values are shown
 * as text; commands and flags, which are not MiniMessage, get them unchanged.
 */
class SignProfileNamesTest {

    private static List<Component> tree(Component root) {
        List<Component> all = new ArrayList<>();
        all.add(root);
        for (Component child : root.children()) {
            all.addAll(tree(child));
        }
        return all;
    }

    private static RegionProfileService.ResolvedSignProfile resolve(String line, String command, String landlord) {
        return resolve(line, command, Map.of("landlord", landlord));
    }

    private static RegionProfileService.ResolvedSignProfile resolve(String line, String command,
                                                                    Map<String, String> placeholders) {
        RegionProfileService service = new RegionProfileService(Logger.getLogger("test"));
        service.setGlobalSignProfile(RegionState.ALL, new SignProfile(List.of(line), List.of(command), null));
        // The backend's map is ordered; a HashMap puts landlord before authority, as in the attack.
        RegionProfileService.ResolvedSignProfile resolved =
                service.resolveSignProfile("plot", RegionState.LEASED, new HashMap<>(placeholders));
        assertNotNull(resolved);
        return resolved;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void aLandlordNameWithTags_isShownAsText() {
        String landlord = "<red><click:run_command:'/op me'>Evil</click> (government)";
        RegionProfileService.ResolvedSignProfile resolved =
                resolve("<green>Landlord: <landlord>", "/realty info plot", landlord);

        Component line = resolved.lines().getFirst();
        assertEquals("Landlord: " + landlord, PlainTextComponentSerializer.plainText().serialize(line));
        assertFalse(tree(line).stream().anyMatch(c -> c.clickEvent() != null));
        assertFalse(tree(line).stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())));
    }

    @Test
    void theTemplatesOwnTags_stillApply() {
        Component line = resolve("<green>Landlord: <landlord>", "/realty info plot", "Steve").lines().getFirst();

        assertEquals("Landlord: Steve", PlainTextComponentSerializer.plainText().serialize(line));
        assertFalse(tree(line).stream().noneMatch(c -> NamedTextColor.GREEN.equals(c.color())));
    }

    @Test
    void commands_getTheNameUnchanged() {
        assertEquals(List.of("/msg <b>x</b> hi"),
                resolve("x", "/msg <landlord> hi", "<b>x</b>").rightClickCommands());
    }

    @Test
    void chainedNames_cannotBuildATag() {
        String authority = "<click:run_command:'/op me'>Click";
        String landlord = "\\<authority>";
        Component line = resolve("Landlord: <landlord>", "/realty info plot",
                Map.of("authority", authority, "landlord", landlord)).lines().getFirst();

        assertFalse(tree(line).stream().anyMatch(c -> c.clickEvent() != null), line.toString());
        assertEquals("Landlord: " + landlord, plain(line));
    }

    @Test
    void aValueThatNamesAnotherPlaceholder_isShownAsText() {
        Component line = resolve("Landlord: <landlord>", "/realty info plot",
                Map.of("landlord", "<tenant>", "tenant", "Steve")).lines().getFirst();

        assertEquals("Landlord: <tenant>", plain(line));
    }

    @Test
    void operatorFormatting_stillApplies() {
        Component line = resolve("<red>Landlord:</red> <landlord>", "/realty info plot",
                "GovSecurity (government)").lines().getFirst();

        assertEquals("Landlord: GovSecurity (government)", plain(line));
        for (Component part : tree(line)) {
            if (part instanceof TextComponent text && !text.content().isEmpty()) {
                boolean isLabel = text.content().contains("Landlord:");
                assertEquals(isLabel, NamedTextColor.RED.equals(part.color()), text.content());
            }
        }
    }

    @Test
    void clickCommand_replacesEachKeyOnce() {
        assertEquals(List.of("/pay <tenant> 5"),
                resolve("x", "/pay <landlord> 5", Map.of("landlord", "<tenant>", "tenant", "Steve"))
                        .rightClickCommands());
    }

    @Test
    void aKeyThatIsNotATagName_fillsTheLineAndCommands() {
        RegionProfileService.ResolvedSignProfile resolved = resolve("Cost: <Price> <landlord>", "/pay <Price>",
                Map.of("Price", "100", "landlord", "Steve"));

        assertEquals("Cost: 100 Steve", plain(resolved.lines().getFirst()));
        assertEquals(List.of("/pay 100"), resolved.rightClickCommands());
    }

    @Test
    void keysWithAnyCharacters_fillTheLine() {
        Component line = resolve("<sale price> | <price:usd> | <a.b>", "x",
                Map.of("sale price", "1", "price:usd", "2", "a.b", "3")).lines().getFirst();

        assertEquals("1 | 2 | 3", plain(line));
    }

    @Test
    void aKeyThatIsNotATagName_insertsItsValueAsText() {
        Component line = resolve("<green>Cost: <Price>", "x", Map.of("Price", "<red>x")).lines().getFirst();

        assertEquals("Cost: <red>x", plain(line));
        assertFalse(tree(line).stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())));
        assertFalse(tree(line).stream().noneMatch(c -> NamedTextColor.GREEN.equals(c.color())));
    }

    @Test
    void aKeyThatIsNotATagName_isNotReadAgainForAnotherPlaceholder() {
        Component line = resolve("Owner: <Owner>", "x",
                Map.of("Owner", "<region> <Price>", "region", "plot", "Price", "100")).lines().getFirst();

        assertEquals("Owner: <region> <Price>", plain(line));
    }

    @Test
    void keysMatchCaseExactly() {
        Component line = resolve("<Landlord> <PRICE> <landlord> <Price>", "x",
                Map.of("landlord", "Steve", "Price", "100")).lines().getFirst();

        assertEquals("<Landlord> <PRICE> Steve 100", plain(line));
    }

    @Test
    void anEscapedKey_isShownAsWritten() {
        Component line = resolve("\\<Price> \\<landlord>", "x",
                Map.of("Price", "100", "landlord", "Steve")).lines().getFirst();

        assertEquals("<Price> <landlord>", plain(line));
    }

    @Test
    void aKeyThatIsNotATagName_worksInsideTheTemplatesTags() {
        Component line = resolve("<hover:show_text:'Cost <Price>'>Hover</hover>", "x",
                Map.of("Price", "<b>100")).lines().getFirst();

        assertEquals("Hover", plain(line));
        Component hover = (Component) tree(line).stream().filter(c -> c.hoverEvent() != null)
                .findFirst().orElseThrow().hoverEvent().value();
        assertEquals("Cost <b>100", plain(hover));
    }

    private static String clickValue(Component line) {
        return tree(line).stream().filter(c -> c.clickEvent() != null)
                .findFirst().orElseThrow().clickEvent().value();
    }

    @Test
    void aPlaceholderInAClickArgument_isLeftAsWritten() {
        Map<String, String> values = Map.of("region", "plot; op me", "Price", "100");

        assertEquals("/x <region>", clickValue(resolve("<click:run_command:'/x <region>'>Go</click>", "x",
                values).lines().getFirst()));
        assertEquals("/x <Price>", clickValue(resolve("<click:run_command:'/x <Price>'>Go</click>", "x",
                values).lines().getFirst()));
    }

    @Test
    void aPlaceholderAfterATagWithAQuotedArgument_isFilled() {
        Component line = resolve("<click:run_command:'/x <region>'>Go <Price></click> <region>", "x",
                Map.of("region", "plot", "Price", "100")).lines().getFirst();

        assertEquals("Go 100 plot", plain(line));
        assertEquals("/x <region>", clickValue(line));
    }
}
