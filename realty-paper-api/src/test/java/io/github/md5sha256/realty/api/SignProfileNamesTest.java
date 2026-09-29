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
    void aKeyThatIsNotATagName_isLeftAsWrittenOnTheLine_andStillFillsCommands() {
        RegionProfileService.ResolvedSignProfile resolved = resolve("Cost: <Price> <landlord>", "/pay <Price>",
                Map.of("Price", "100", "landlord", "Steve"));

        assertEquals("Cost: <Price> Steve", plain(resolved.lines().getFirst()));
        assertEquals(List.of("/pay 100"), resolved.rightClickCommands());
    }
}
