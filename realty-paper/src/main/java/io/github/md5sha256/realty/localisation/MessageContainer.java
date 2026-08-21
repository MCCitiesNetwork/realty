package io.github.md5sha256.realty.localisation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import javax.annotation.Nonnull;

/**
 * Realty's message store. Loading, rendering and the {@code <prefix>} placeholder all come from
 * {@link com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer}; this
 * subclass only adds {@link #deserializeRaw(String)}.
 */
public class MessageContainer
        extends com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer {

    /**
     * Renders an already-substituted MiniMessage string, resolving {@code <prefix>} as usual.
     *
     * <p><strong>The input must be plugin-authored, never player-authored.</strong> Paginated
     * commands build their navigation links by substituting a {@code /realty …} command into a
     * {@code <click>} tag argument, which no {@link TagResolver} can fill; this method exists for
     * that case alone. Runtime values belong in {@link #value(String, String)}.</p>
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

}
