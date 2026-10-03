package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name {@code /realty list} shows and repeats in its page links can be an account's display
 * name, which its owner may choose. It must be shown as text, never parsed as MiniMessage. The
 * templates are the shipped messages.yml.
 */
class ListCommandNamesTest {

    private static final MessageContainer MESSAGES = new MessageContainer();

    @BeforeAll
    static void loadShippedMessages() throws IOException {
        try (InputStream in = ListCommandNamesTest.class.getResourceAsStream("/messages.yml")) {
            assertNotNull(in, "messages.yml is missing from the plugin resources");
            MESSAGES.load(YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load());
        }
    }

    private static List<Component> tree(Component root) {
        List<Component> all = new ArrayList<>();
        all.add(root);
        for (Component child : root.children()) {
            all.addAll(tree(child));
        }
        return all;
    }

    private static List<ClickEvent> clickEvents(Component root) {
        return tree(root).stream().map(Component::clickEvent).filter(event -> event != null).toList();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void header_showsAnAccountNameWithTagsAsPlainText() {
        String name = "<red>Evil</red> (government)";
        Component header = ListCommand.header(MESSAGES, name);

        assertTrue(plain(header).contains(name), plain(header));
        assertFalse(tree(header).stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())));
        assertEquals(List.of(), clickEvents(header));
    }

    @Test
    void header_showsAnAccountNameWithAClickTagAsPlainText() {
        String name = "<click:run_command:'/op me'>x</click> (business)";
        Component header = ListCommand.header(MESSAGES, name);

        assertTrue(plain(header).contains(name), plain(header));
        assertEquals(List.of(), clickEvents(header));
    }

    @Test
    void pageLink_runsTheListCommandForTheTypedName() {
        Component link = ListCommand.pageLink(MESSAGES, MessageKeys.LIST_NEXT, null, "GovSecurity",
                PartyFlag.GOVERNMENT, 2);

        assertEquals(List.of(ClickEvent.runCommand("/realty list GovSecurity --page 2 --government")),
                clickEvents(link));
    }

    @Test
    void pageLink_withTagsInTheTypedName_hasOnlyTheOneClickEvent() {
        String name = "x'><click:run_command:'/op_me'>y</click><red>";
        Component link = ListCommand.pageLink(MESSAGES, MessageKeys.LIST_NEXT, "owned", name, null, 3);

        assertEquals(List.of(ClickEvent.runCommand("/realty list owned " + name + " --page 3")),
                clickEvents(link));
        assertFalse(tree(link).stream().anyMatch(c -> NamedTextColor.RED.equals(c.color())));
    }

    @Test
    void categoryHeading_namesEachPartFromTheShippedMessages() {
        Map<ListCommand.Category, String> headings = new EnumMap<>(ListCommand.Category.class);
        for (ListCommand.Category category : ListCommand.Category.values()) {
            headings.put(category, plain(ListCommand.categoryHeading(MESSAGES, category)));
        }

        assertEquals(Map.of(
                ListCommand.Category.OWNED, "Owned:",
                ListCommand.Category.AUTHORITY, "Freehold authority:",
                ListCommand.Category.LANDLORD, "Landlord of leases:",
                ListCommand.Category.RENTED, "Rented:"), headings);
    }

    @Test
    void pageLink_repeatsTheLandlordCategory() {
        Component link = ListCommand.pageLink(MESSAGES, MessageKeys.LIST_NEXT, "landlord", "GovSecurity",
                PartyFlag.GOVERNMENT, 2);

        assertEquals(List.of(ClickEvent.runCommand("/realty list landlord GovSecurity --page 2 --government")),
                clickEvents(link));
    }
}
