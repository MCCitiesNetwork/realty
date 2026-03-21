package io.github.md5sha256.realty.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;

public final class SimpleDateFormatSerializer implements TypeSerializer<SimpleDateFormat> {

    public static final SimpleDateFormatSerializer INSTANCE = new SimpleDateFormatSerializer();

    private SimpleDateFormatSerializer() {
    }

    @Override
    public SimpleDateFormat deserialize(Type type, ConfigurationNode node) throws SerializationException {
        String pattern = node.getString();
        if (pattern == null) {
            throw new SerializationException("date-format pattern cannot be null");
        }
        try {
            return new SimpleDateFormat(pattern);
        } catch (IllegalArgumentException ex) {
            throw new SerializationException("Invalid date-format pattern: " + pattern + ": " + ex.getMessage());
        }
    }

    @Override
    public void serialize(Type type, @Nullable SimpleDateFormat obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(String.class, null);
            return;
        }
        node.set(String.class, obj.toPattern());
    }
}
