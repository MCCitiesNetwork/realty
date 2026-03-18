package io.github.md5sha256.realty.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import javax.annotation.Nonnull;
import java.lang.reflect.Type;
import java.util.function.Function;

public class ComponentSerializer implements TypeSerializer<Component> {

    public static final ComponentSerializer MINI_MESSAGE = new ComponentSerializer(MiniMessage.miniMessage()::deserialize,
            MiniMessage.miniMessage()::serialize);

    public static final ComponentSerializer LEGACY_AMPERSAND = new ComponentSerializer(LegacyComponentSerializer.legacyAmpersand()::deserialize,
            LegacyComponentSerializer.legacyAmpersand()::serialize);


    private final Function<String, Component> deserializer;
    private final Function<Component, String> serializer;

    public ComponentSerializer(
            @Nonnull Function<String, Component> deserializer,
            @Nonnull Function<Component, String> serializer
    ) {
        this.deserializer = deserializer;
        this.serializer = serializer;
    }

    @Override
    public Component deserialize(Type type, ConfigurationNode node) throws SerializationException {
        if (node.isNull()) {
            return null;
        }
        String s = node.getString();
        if (s == null) {
            return null;
        }
        try {
            return this.deserializer.apply(s);
        } catch (Exception ex) {
            throw new SerializationException(ex);
        }
    }

    @Override
    public void serialize(Type type, @Nullable Component obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(String.class, null);
            return;
        }
        String item = this.serializer.apply(obj);
        node.set(String.class, item);
    }

}
