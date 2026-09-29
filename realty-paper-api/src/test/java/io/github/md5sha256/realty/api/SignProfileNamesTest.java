package io.github.md5sha256.realty.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        RegionProfileService service = new RegionProfileService(Logger.getLogger("test"));
        service.setGlobalSignProfile(RegionState.ALL, new SignProfile(List.of(line), List.of(command), null));
        RegionProfileService.ResolvedSignProfile resolved =
                service.resolveSignProfile("plot", RegionState.LEASED, Map.of("landlord", landlord));
        assertNotNull(resolved);
        return resolved;
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
}
