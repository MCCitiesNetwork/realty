package io.github.md5sha256.realty.localisation;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MessageContainer {

    private final Map<String, String> rawMessages = new ConcurrentHashMap<>();

    private @Nonnull TagResolver prefixResolver() {
        String raw = this.rawMessages.get("prefix");
        if (raw == null) {
            return Placeholder.component("prefix", Component.empty());
        }
        Component prefix = MiniMessage.miniMessage().deserialize(raw);
        return Placeholder.component("prefix", prefix);
    }

    public String plaintextMessageFor(@Nonnull String key) {
        return PlainTextComponentSerializer.plainText().serialize(messageFor(key));
    }

    @Nonnull
    public Component messageFor(@Nonnull String key) {
        String raw = this.rawMessages.get(key);
        if (raw == null) {
            return Component.text(key);
        }
        return MiniMessage.miniMessage().deserialize(raw, prefixResolver());
    }

    @Nonnull
    public Component messageFor(@Nonnull String key, @Nonnull TagResolver... resolvers) {
        String raw = this.rawMessages.get(key);
        if (raw == null) {
            return Component.text(key);
        }
        TagResolver[] combined = Arrays.copyOf(resolvers, resolvers.length + 1);
        combined[resolvers.length] = prefixResolver();
        return MiniMessage.miniMessage().deserialize(raw, combined);
    }

    @Nonnull
    public String miniMessageFormattedFor(@Nonnull String key) {
        return this.rawMessages.getOrDefault(key, key);
    }

    public void setMessage(@Nonnull String key, @Nonnull String rawMiniMessage) {
        this.rawMessages.put(key, rawMiniMessage);
    }

    public void clear() {
        this.rawMessages.clear();
    }

    public void load(@Nonnull ConfigurationNode root) throws ConfigurateException {
        Map<String, String> temp = new HashMap<>();
        loadInto("", root, temp);
        this.rawMessages.putAll(temp);
    }

    public void save(@Nonnull ConfigurationNode root) throws ConfigurateException {
        for (Map.Entry<String, String> entry : this.rawMessages.entrySet()) {
            root.node((Object[]) entry.getKey().split("\\.")).set(entry.getValue());
        }
    }

    private void loadInto(String path,
                          ConfigurationNode root,
                          Map<String, String> temp) throws ConfigurateException {
        if (!root.empty()) {
            if (root.isList()) {
                List<String> strings = root.getList(String.class, Collections.emptyList());
                String joined = strings.stream()
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining("\n"));
                if (!joined.isEmpty()) {
                    temp.put(path, joined);
                }
            } else {
                String raw = root.getString();
                if (raw != null) {
                    temp.put(path, raw);
                }
            }
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.childrenMap().entrySet()) {
            String key = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            String newPath = path.isEmpty() ? key : path + "." + key;
            loadInto(newPath, node, temp);
        }
    }

}
