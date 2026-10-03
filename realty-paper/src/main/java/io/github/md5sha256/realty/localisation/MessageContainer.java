package io.github.md5sha256.realty.localisation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Realty's message store. Loading, rendering and the {@code <prefix>} placeholder all come from
 * {@link com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer}; this
 * subclass only adds {@link #deserializeRaw(String)} and {@link #commandLink(String, String)}.
 */
public class MessageContainer
        extends com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer {

    /**
     * Renders an already-substituted MiniMessage string, resolving {@code <prefix>} as usual.
     *
     * <p><strong>The input must be plugin-authored, never player-authored.</strong> Runtime values
     * belong in {@link #value(String, String)}; a command in a {@code <click>} tag belongs in
     * {@link #commandLink(String, String)}.</p>
     */
    @Nonnull
    public Component deserializeRaw(@Nonnull String raw) {
        // messageFor renders a missing key as the key itself, so an unset prefix would print
        // "prefix". Fall back to empty, matching how the base class resolves <prefix>.
        String rawPrefix = miniMessageFormattedFor("prefix");
        Component prefix = rawPrefix.equals("prefix")
                ? Component.empty()
                : MiniMessage.miniMessage().deserialize(rawPrefix);
        return MiniMessage.miniMessage().deserialize(raw, Placeholder.component("prefix", prefix));
    }

    /** Stands in for the command while the template is parsed; it holds no MiniMessage syntax. */
    private static final String COMMAND_STAND_IN = "/realty-command-link";

    /**
     * Renders the message {@code key}, whose {@code <click:run_command:<command>>} tag then runs
     * {@code command}. A {@code <click>} tag argument cannot be filled by a {@link TagResolver},
     * and a command pasted into the template would be parsed: a name in it such as an account's
     * display name, which its owner chooses, could close the tag and add its own. So the template
     * is parsed with a stand-in, and the stand-in's click events are then given the command as
     * plain text.
     */
    @Nonnull
    public Component commandLink(@Nonnull String key, @Nonnull String command) {
        String raw = miniMessageFormattedFor(key).replace("<command>", COMMAND_STAND_IN);
        return withCommand(deserializeRaw(raw), command);
    }

    private static @Nonnull Component withCommand(@Nonnull Component component, @Nonnull String command) {
        Component result = component;
        ClickEvent click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text
                && COMMAND_STAND_IN.equals(text.value())) {
            result = result.clickEvent(ClickEvent.clickEvent(click.action(), ClickEvent.Payload.string(command)));
        }
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return result;
        }
        return result.children(children.stream().map(child -> withCommand(child, command)).toList());
    }
}
